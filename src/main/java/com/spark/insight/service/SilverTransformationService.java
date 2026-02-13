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
        log.info("Starting Silver transformation (Physical Rebuild Mode) for app: {}", appId);
        
        jdbcTemplate.execute("INSTALL json; LOAD json;");

        // Force rebuild tables without constraints
        rebuildSilverTables();

        transformJobs(appId);
        transformTasks(appId);
        transformStages(appId);
        transformExecutors(appId);

        log.info("Finished Silver transformation for app: {}", appId);
    }

    private void rebuildSilverTables() {
        log.info("Dropping and Recreating Silver tables to clear legacy constraints...");
        
        // We drop and recreate. Since this is a data pipeline from Bronze, 
        // we can always re-generate Silver from Bronze.
        
        // Jobs
        jdbcTemplate.execute("DROP TABLE IF EXISTS silver_jobs");
        jdbcTemplate.execute("""
            CREATE TABLE silver_jobs (
                app_id VARCHAR, job_id INT, submission_time TIMESTAMP, completion_time TIMESTAMP, 
                duration_ms BIGINT, status VARCHAR, num_stages INT, stage_ids JSON, 
                description TEXT, sql_execution_id BIGINT
            )""");
        
        // Stages
        jdbcTemplate.execute("DROP TABLE IF EXISTS silver_stages");
        jdbcTemplate.execute("""
            CREATE TABLE silver_stages (
                app_id VARCHAR, stage_id INT, attempt_id INT, name VARCHAR, num_tasks INT, 
                status VARCHAR, submission_time TIMESTAMP, completion_time TIMESTAMP, 
                duration_ms BIGINT, input_bytes BIGINT DEFAULT 0, shuffle_read_bytes BIGINT DEFAULT 0, 
                parent_ids JSON
            )""");

        // Tasks
        jdbcTemplate.execute("DROP TABLE IF EXISTS silver_tasks");
        jdbcTemplate.execute("""
            CREATE TABLE silver_tasks (
                app_id VARCHAR, task_id BIGINT, stage_id INT, stage_attempt_id INT, 
                executor_id VARCHAR, host VARCHAR, index INT, attempt_number INT, 
                launch_time TIMESTAMP, finish_time TIMESTAMP, duration_ms BIGINT, 
                status VARCHAR, locality VARCHAR, speculative BOOLEAN, 
                executor_run_time BIGINT, executor_cpu_time BIGINT, gc_time BIGINT, 
                input_bytes BIGINT, output_bytes BIGINT, shuffle_read_bytes BIGINT, 
                shuffle_write_bytes BIGINT, memory_bytes_spilled BIGINT, 
                disk_bytes_spilled BIGINT, peak_execution_memory BIGINT
            )""");

        // Executors
        jdbcTemplate.execute("DROP TABLE IF EXISTS silver_executors");
        jdbcTemplate.execute("""
            CREATE TABLE silver_executors (
                app_id VARCHAR, executor_id VARCHAR, host VARCHAR, total_cores INT, 
                add_time TIMESTAMP, remove_time TIMESTAMP, remove_reason TEXT
            )""");
    }

    private void transformJobs(String appId) {
        log.debug("Transforming Jobs for app: {}", appId);
        String sql = """
            WITH js_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Job ID"'))::INT)
                    app_id, (json_extract(raw_json, '$."Job ID"'))::INT as job_id, raw_json
                FROM bronze_event_job_start WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Job ID"'))::INT, ingested_at DESC
            ),
            je_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Job ID"'))::INT)
                    app_id, (json_extract(raw_json, '$."Job ID"'))::INT as job_id, raw_json
                FROM bronze_event_job_end WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Job ID"'))::INT, ingested_at DESC
            )
            INSERT INTO silver_jobs
            SELECT DISTINCT ON (js.app_id, js.job_id)
                js.app_id, js.job_id,
                epoch_ms((json_extract(js.raw_json, '$."Submission Time"'))::BIGINT),
                epoch_ms((json_extract(je.raw_json, '$."Completion Time"'))::BIGINT),
                (json_extract(je.raw_json, '$."Completion Time"'))::BIGINT - (json_extract(js.raw_json, '$."Submission Time"'))::BIGINT,
                json_extract_string(je.raw_json, '$."Job Result".Result'),
                json_array_length(json_extract(js.raw_json, '$."Stage IDs"')),
                json_extract(js.raw_json, '$."Stage IDs"'),
                json_extract_string(js.raw_json, '$.Properties."spark.job.description"'),
                (json_extract(js.raw_json, '$.Properties."spark.sql.execution.id"'))::BIGINT
            FROM js_dedup js
            LEFT JOIN je_dedup je ON js.app_id = je.app_id AND js.job_id = je.job_id
            ORDER BY js.app_id, js.job_id
            """;
        jdbcTemplate.update(sql, appId, appId);
    }

    private void transformStages(String appId) {
        log.debug("Transforming Stages for app: {}", appId);
        String sql = """
            WITH ss_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT)
                    app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT as stage_id, 
                    (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT as attempt_id, raw_json
                FROM bronze_event_stage_submitted WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT, ingested_at DESC
            ),
            sc_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT)
                    app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT as stage_id, 
                    (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT as attempt_id, raw_json
                FROM bronze_event_stage_completed WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT, ingested_at DESC
            ),
            task_metrics AS (
                SELECT stage_id, stage_attempt_id, sum(input_bytes) as input_sum, sum(shuffle_read_bytes) as shuffle_sum
                FROM silver_tasks WHERE app_id = ? GROUP BY stage_id, stage_attempt_id
            )
            INSERT INTO silver_stages (app_id, stage_id, attempt_id, name, num_tasks, status, submission_time, completion_time, duration_ms, input_bytes, shuffle_read_bytes, parent_ids)
            SELECT DISTINCT ON (ss.app_id, ss.stage_id, ss.attempt_id)
                ss.app_id, ss.stage_id, ss.attempt_id,
                json_extract_string(ss.raw_json, '$."Stage Info"."Stage Name"'),
                (json_extract(ss.raw_json, '$."Stage Info"."Number of Tasks"'))::INT,
                CASE WHEN sc.raw_json IS NOT NULL THEN 'COMPLETED' ELSE 'PENDING' END,
                epoch_ms((json_extract(ss.raw_json, '$."Stage Info"."Submission Time"'))::BIGINT),
                epoch_ms((json_extract(sc.raw_json, '$."Stage Info"."Completion Time"'))::BIGINT),
                (json_extract(sc.raw_json, '$."Stage Info"."Completion Time"'))::BIGINT - (json_extract(ss.raw_json, '$."Stage Info"."Submission Time"'))::BIGINT,
                coalesce(tm.input_sum, 0),
                coalesce(tm.shuffle_sum, 0),
                json_extract(ss.raw_json, '$."Stage Info"."Parent IDs"')
            FROM ss_dedup ss
            LEFT JOIN sc_dedup sc ON ss.app_id = sc.app_id AND ss.stage_id = sc.stage_id AND ss.attempt_id = sc.attempt_id
            LEFT JOIN task_metrics tm ON ss.stage_id = tm.stage_id AND ss.attempt_id = tm.stage_attempt_id
            ORDER BY ss.app_id, ss.stage_id, ss.attempt_id
            """;
        jdbcTemplate.update(sql, appId, appId, appId);
    }

    private void transformTasks(String appId) {
        log.debug("Transforming Tasks for app: {}", appId);
        String sql = """
            WITH task_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT)
                    raw_json, app_id, (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT as task_id
                FROM bronze_event_task_end WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT, ingested_at DESC
            )
            INSERT INTO silver_tasks (app_id, task_id, stage_id, stage_attempt_id, executor_id, host, index, attempt_number, launch_time, finish_time, duration_ms, status, locality, speculative, executor_run_time, executor_cpu_time, gc_time, input_bytes, output_bytes, shuffle_read_bytes, shuffle_write_bytes, memory_bytes_spilled, disk_bytes_spilled, peak_execution_memory)
            SELECT DISTINCT ON (app_id, task_id)
                app_id, task_id,
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
            FROM task_dedup
            ORDER BY app_id, task_id
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void transformExecutors(String appId) {
        log.debug("Transforming Executors for app: {}", appId);
        String sql = """
            WITH ea_dedup AS (
                SELECT DISTINCT ON (app_id, json_extract_string(raw_json, '$."Executor ID"'))
                    app_id, json_extract_string(raw_json, '$."Executor ID"') as executor_id, raw_json
                FROM bronze_event_executor_added WHERE app_id = ?
                ORDER BY app_id, json_extract_string(raw_json, '$."Executor ID"'), ingested_at DESC
            ),
            er_dedup AS (
                SELECT DISTINCT ON (app_id, json_extract_string(raw_json, '$."Executor ID"'))
                    app_id, json_extract_string(raw_json, '$."Executor ID"') as executor_id, raw_json
                FROM bronze_event_executor_removed WHERE app_id = ?
                ORDER BY app_id, json_extract_string(raw_json, '$."Executor ID"'), ingested_at DESC
            )
            INSERT INTO silver_executors (app_id, executor_id, host, total_cores, add_time, remove_time, remove_reason)
            SELECT DISTINCT ON (ea.app_id, ea.executor_id)
                ea.app_id, ea.executor_id,
                json_extract_string(ea.raw_json, '$."Executor Info".Host'),
                (json_extract(ea.raw_json, '$."Executor Info"."Total Cores"'))::INT,
                epoch_ms((json_extract(ea.raw_json, '$.Timestamp'))::BIGINT),
                epoch_ms((json_extract(er.raw_json, '$.Timestamp'))::BIGINT),
                json_extract_string(er.raw_json, '$."Removed Reason"')
            FROM ea_dedup ea
            LEFT JOIN er_dedup er ON ea.app_id = er.app_id AND ea.executor_id = er.executor_id
            ORDER BY ea.app_id, ea.executor_id
            """;
        jdbcTemplate.update(sql, appId, appId);
    }
}
