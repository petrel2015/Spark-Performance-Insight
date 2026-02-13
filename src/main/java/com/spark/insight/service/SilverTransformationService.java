package com.spark.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SilverTransformationService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void transform(String appId) {
        log.info("Starting Silver transformation for app: {}", appId);
        
        jdbcTemplate.execute("INSTALL json; LOAD json;");

        // Order matters: transform tasks first so we can aggregate metrics for stages
        transformJobs(appId);
        transformTasks(appId);
        transformStages(appId);
        transformExecutors(appId);

        log.info("Finished Silver transformation for app: {}", appId);
    }

    private void transformJobs(String appId) {
        log.debug("Transforming Jobs for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_jobs WHERE app_id = ?", appId);

        String sql = """
            WITH js_raw AS (SELECT * FROM bronze_event_job_start WHERE app_id = ?),
                 je_raw AS (SELECT * FROM bronze_event_job_end WHERE app_id = ?)
            INSERT INTO silver_jobs
            SELECT
                js.app_id,
                (json_extract(js.raw_json, '$."Job ID"'))::INT as job_id,
                epoch_ms((json_extract(js.raw_json, '$."Submission Time"'))::BIGINT),
                epoch_ms((json_extract(je.raw_json, '$."Completion Time"'))::BIGINT),
                (json_extract(je.raw_json, '$."Completion Time"'))::BIGINT - (json_extract(js.raw_json, '$."Submission Time"'))::BIGINT,
                json_extract_string(je.raw_json, '$."Job Result".Result'),
                json_array_length(json_extract(js.raw_json, '$."Stage IDs"')),
                json_extract(js.raw_json, '$."Stage IDs"'),
                json_extract_string(js.raw_json, '$.Properties."spark.job.description"'),
                (json_extract(js.raw_json, '$.Properties."spark.sql.execution.id"'))::BIGINT
            FROM js_raw js
            LEFT JOIN je_raw je ON (json_extract(js.raw_json, '$."Job ID"')) = (json_extract(je.raw_json, '$."Job ID"'))
            """;
            
        jdbcTemplate.update(sql, appId, appId);
    }

    private void transformStages(String appId) {
        log.debug("Transforming Stages for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_stages WHERE app_id = ?", appId);

        // Aggregate metrics from silver_tasks instead of parsing complex JSON
        String sql = """
            WITH ss_raw AS (SELECT * FROM bronze_event_stage_submitted WHERE app_id = ?),
                 sc_raw AS (SELECT * FROM bronze_event_stage_completed WHERE app_id = ?),
                 task_metrics AS (
                     SELECT stage_id, stage_attempt_id, sum(input_bytes) as input_sum, sum(shuffle_read_bytes) as shuffle_sum
                     FROM silver_tasks WHERE app_id = ? GROUP BY stage_id, stage_attempt_id
                 )
            INSERT INTO silver_stages (app_id, stage_id, attempt_id, name, num_tasks, status, submission_time, completion_time, duration_ms, input_bytes, shuffle_read_bytes, parent_ids)
            SELECT
                ss.app_id,
                (json_extract(ss.raw_json, '$."Stage Info"."Stage ID"'))::INT as stage_id,
                (json_extract(ss.raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT as attempt_id,
                json_extract_string(ss.raw_json, '$."Stage Info"."Stage Name"'),
                (json_extract(ss.raw_json, '$."Stage Info"."Number of Tasks"'))::INT,
                CASE WHEN sc.id IS NOT NULL THEN 'COMPLETED' ELSE 'PENDING' END,
                epoch_ms((json_extract(ss.raw_json, '$."Stage Info"."Submission Time"'))::BIGINT),
                epoch_ms((json_extract(sc.raw_json, '$."Stage Info"."Completion Time"'))::BIGINT),
                (json_extract(sc.raw_json, '$."Stage Info"."Completion Time"'))::BIGINT - (json_extract(ss.raw_json, '$."Stage Info"."Submission Time"'))::BIGINT,
                coalesce(tm.input_sum, 0),
                coalesce(tm.shuffle_sum, 0),
                json_extract(ss.raw_json, '$."Stage Info"."Parent IDs"')
            FROM ss_raw ss
            LEFT JOIN sc_raw sc ON (json_extract(ss.raw_json, '$."Stage Info"."Stage ID"')) = (json_extract(sc.raw_json, '$."Stage Info"."Stage ID"'))
                AND (json_extract(ss.raw_json, '$."Stage Info"."Stage Attempt ID"')) = (json_extract(sc.raw_json, '$."Stage Info"."Stage Attempt ID"'))
            LEFT JOIN task_metrics tm ON (json_extract(ss.raw_json, '$."Stage Info"."Stage ID"'))::INT = tm.stage_id 
                AND (json_extract(ss.raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT = tm.stage_attempt_id
            """;

        jdbcTemplate.update(sql, appId, appId, appId);
    }

    private void transformTasks(String appId) {
        log.debug("Transforming Tasks for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_tasks WHERE app_id = ?", appId);

        String sql = """
            INSERT INTO silver_tasks (app_id, task_id, stage_id, stage_attempt_id, executor_id, host, index, attempt_number, launch_time, finish_time, duration_ms, status, locality, speculative, executor_run_time, executor_cpu_time, gc_time, input_bytes, output_bytes, shuffle_read_bytes, shuffle_write_bytes, memory_bytes_spilled, disk_bytes_spilled, peak_execution_memory)
            SELECT
                app_id,
                (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT,
                (json_extract(raw_json, '$."Stage ID"'))::INT,
                (json_extract(raw_json, '$."Stage Attempt ID"'))::INT,
                json_extract_string(raw_json, '$."Task Info"."Executor ID"'),
                json_extract_string(raw_json, '$."Task Info".Host'),
                (json_extract(raw_json, '$."Task Info".Index'))::INT,
                (json_extract(raw_json, '$."Task Info".Attempt'))::INT,
                epoch_ms((json_extract(raw_json, '$."Task Info"."Launch Time"'))::BIGINT),
                epoch_ms((json_extract(raw_json, '$."Task Info"."Finish Time"'))::BIGINT),
                (json_extract(raw_json, '$."Task Info"."Finish Time"'))::BIGINT - (json_extract(raw_json, '$."Task Info"."Launch Time"'))::BIGINT,
                CASE WHEN (json_extract(raw_json, '$."Task Info".Failed'))::BOOLEAN THEN 'FAILED' ELSE 'SUCCESS' END,
                json_extract_string(raw_json, '$."Task Info".Locality'),
                (json_extract(raw_json, '$."Task Info".Speculative'))::BOOLEAN,
                (json_extract(raw_json, '$."Task Metrics"."Executor Run Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Executor CPU Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."JVM GC Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Input Metrics"."Bytes Read"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Output Metrics"."Bytes Written"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Shuffle Read Metrics"."Total Bytes Read"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Shuffle Write Metrics"."Bytes Written"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Memory Bytes Spilled"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Disk Bytes Spilled"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Peak Execution Memory"'))::BIGINT
            FROM bronze_event_task_end
            WHERE app_id = ?
            """;

        jdbcTemplate.update(sql, appId);
    }

    private void transformExecutors(String appId) {
        log.debug("Transforming Executors for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_executors WHERE app_id = ?", appId);

        String sql = """
            WITH ea_raw AS (SELECT * FROM bronze_event_executor_added WHERE app_id = ?),
                 er_raw AS (SELECT * FROM bronze_event_executor_removed WHERE app_id = ?)
            INSERT INTO silver_executors (app_id, executor_id, host, total_cores, add_time, remove_time, remove_reason)
            SELECT
                ea.app_id,
                json_extract_string(ea.raw_json, '$."Executor ID"'),
                json_extract_string(ea.raw_json, '$."Executor Info".Host'),
                (json_extract(ea.raw_json, '$."Executor Info"."Total Cores"'))::INT,
                epoch_ms((json_extract(ea.raw_json, '$.Timestamp'))::BIGINT),
                epoch_ms((json_extract(er.raw_json, '$.Timestamp'))::BIGINT),
                json_extract_string(er.raw_json, '$."Removed Reason"')
            FROM ea_raw ea
            LEFT JOIN er_raw er ON json_extract(ea.raw_json, '$."Executor ID"') = json_extract(er.raw_json, '$."Executor ID"')
            """;

        jdbcTemplate.update(sql, appId, appId);
    }
}
