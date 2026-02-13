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

        // 1. Transform Jobs
        transformJobs(appId);

        // 2. Transform Stages
        transformStages(appId);

        // 3. Transform Tasks
        transformTasks(appId);

        // 4. Transform Executors
        transformExecutors(appId);

        log.info("Finished Silver transformation for app: {}", appId);
    }

    private void transformJobs(String appId) {
        log.debug("Transforming Jobs for app: {}", appId);
        
        // Clear old silver data for this app
        jdbcTemplate.update("DELETE FROM silver_jobs WHERE app_id = ?", appId);

        String sql = 
            "INSERT INTO silver_jobs (app_id, job_id, submission_time, completion_time, duration_ms, status, num_stages, stage_ids, description, sql_execution_id) " +
            "SELECT " +
            "    js.app_id, " +
            "    (js.raw_json->>'$.Job ID')::INT as job_id, " +
            "    epoch_ms((js.raw_json->>'$.Submission Time')::BIGINT) as submission_time, " +
            "    epoch_ms((je.raw_json->>'$.Completion Time')::BIGINT) as completion_time, " +
            "    (je.raw_json->>'$.Completion Time')::BIGINT - (js.raw_json->>'$.Submission Time')::BIGINT as duration_ms, " +
            "    je.raw_json->>'$.Job Result.Result' as status, " +
            "    json_array_length(js.raw_json->'$.Stage IDs') as num_stages, " +
            "    js.raw_json->'$.Stage IDs' as stage_ids, " +
            "    js.raw_json->'$.Properties.spark.job.description' as description, " +
            "    (js.raw_json->>'$.Properties.spark.sql.execution.id')::BIGINT as sql_execution_id " +
            "FROM bronze_event_job_start js " +
            "LEFT JOIN bronze_event_job_end je ON js.app_id = je.app_id AND (js.raw_json->>'$.Job ID') = (je.raw_json->>'$.Job ID') " +
            "WHERE js.app_id = ?";
            
        jdbcTemplate.update(sql, appId);
    }

    private void transformStages(String appId) {
        log.debug("Transforming Stages for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_stages WHERE app_id = ?", appId);

        String sql = 
            "INSERT INTO silver_stages (app_id, stage_id, attempt_id, job_id, name, num_tasks, status, submission_time, completion_time, duration_ms, input_bytes, output_bytes, shuffle_read_bytes, shuffle_write_bytes, parent_ids) " +
            "SELECT " +
            "    ss.app_id, " +
            "    (ss.raw_json->>'$.Stage Info.Stage ID')::INT, " +
            "    (ss.raw_json->>'$.Stage Info.Stage Attempt ID')::INT, " +
            "    NULL, -- Job ID mapping might need a separate step or join with silver_jobs " +
            "    ss.raw_json->>'$.Stage Info.Stage Name', " +
            "    (ss.raw_json->>'$.Stage Info.Number of Tasks')::INT, " +
            "    CASE WHEN sc.id IS NOT NULL THEN 'COMPLETED' ELSE 'PENDING' END, " +
            "    epoch_ms((ss.raw_json->>'$.Stage Info.Submission Time')::BIGINT), " +
            "    epoch_ms((sc.raw_json->>'$.Stage Info.Completion Time')::BIGINT), " +
            "    (sc.raw_json->>'$.Stage Info.Completion Time')::BIGINT - (ss.raw_json->>'$.Stage Info.Submission Time')::BIGINT, " +
            "    (sc.raw_json->>'$.Stage Info.Accumulables[*].Value' FILTER x -> x.Name = 'internal.metrics.input.bytesRead')::BIGINT, " +
            "    (sc.raw_json->>'$.Stage Info.Accumulables[*].Value' FILTER x -> x.Name = 'internal.metrics.output.bytesWritten')::BIGINT, " +
            "    (sc.raw_json->>'$.Stage Info.Accumulables[*].Value' FILTER x -> x.Name = 'internal.metrics.shuffle.read.remoteBytesRead')::BIGINT, " +
            "    (sc.raw_json->>'$.Stage Info.Accumulables[*].Value' FILTER x -> x.Name = 'internal.metrics.shuffle.write.bytesWritten')::BIGINT, " +
            "    ss.raw_json->'$.Stage Info.Parent IDs' " +
            "FROM bronze_event_stage_submitted ss " +
            "LEFT JOIN bronze_event_stage_completed sc ON ss.app_id = sc.app_id " +
            "    AND (ss.raw_json->>'$.Stage Info.Stage ID') = (sc.raw_json->>'$.Stage Info.Stage ID') " +
            "    AND (ss.raw_json->>'$.Stage Info.Stage Attempt ID') = (sc.raw_json->>'$.Stage Info.Stage Attempt ID') " +
            "WHERE ss.app_id = ?";

        jdbcTemplate.update(sql, appId);
    }

    private void transformTasks(String appId) {
        log.debug("Transforming Tasks for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_tasks WHERE app_id = ?", appId);

        // Note: For large apps, this might need optimization or batching
        String sql = 
            "INSERT INTO silver_tasks " +
            "SELECT " +
            "    app_id, " +
            "    (raw_json->>'$.Task Info.Task ID')::BIGINT, " +
            "    (raw_json->>'$.Stage ID')::INT, " +
            "    (raw_json->>'$.Stage Attempt ID')::INT, " +
            "    raw_json->>'$.Task Info.Executor ID', " +
            "    raw_json->>'$.Task Info.Host', " +
            "    (raw_json->>'$.Task Info.Index')::INT, " +
            "    (raw_json->>'$.Task Info.Attempt')::INT, " +
            "    epoch_ms((raw_json->>'$.Task Info.Launch Time')::BIGINT), " +
            "    epoch_ms((raw_json->>'$.Task Info.Finish Time')::BIGINT), " +
            "    (raw_json->>'$.Task Info.Finish Time')::BIGINT - (raw_json->>'$.Task Info.Launch Time')::BIGINT, " +
            "    CASE WHEN (raw_json->>'$.Task Info.Failed') = 'true' THEN 'FAILED' ELSE 'SUCCESS' END, " +
            "    raw_json->>'$.Task Info.Locality', " +
            "    (raw_json->>'$.Task Info.Speculative')::BOOLEAN, " +
            "    (raw_json->>'$.Task Metrics.Executor Run Time')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Executor CPU Time')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.JVM GC Time')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Input Metrics.Bytes Read')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Output Metrics.Bytes Written')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Shuffle Read Metrics.Total Bytes Read')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Shuffle Write Metrics.Bytes Written')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Memory Bytes Spilled')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Disk Bytes Spilled')::BIGINT, " +
            "    (raw_json->>'$.Task Metrics.Peak Execution Memory')::BIGINT " +
            "FROM bronze_event_task_end " +
            "WHERE app_id = ?";

        jdbcTemplate.update(sql, appId);
    }

    private void transformExecutors(String appId) {
        log.debug("Transforming Executors for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_executors WHERE app_id = ?", appId);

        String sql = 
            "INSERT INTO silver_executors " +
            "SELECT " +
            "    ea.app_id, " +
            "    ea.raw_json->>'$.Executor ID', " +
            "    ea.raw_json->>'$.Executor Info.Host', " +
            "    (ea.raw_json->>'$.Executor Info.Total Cores')::INT, " +
            "    NULL, -- Memory info often in environment update, not executor added event " +
            "    epoch_ms((ea.raw_json->>'$.Timestamp')::BIGINT), " +
            "    epoch_ms((er.raw_json->>'$.Timestamp')::BIGINT), " +
            "    er.raw_json->>'$.Removed Reason' " +
            "FROM bronze_event_executor_added ea " +
            "LEFT JOIN bronze_event_executor_removed er ON ea.app_id = er.app_id AND (ea.raw_json->>'$.Executor ID') = (er.raw_json->>'$.Executor ID') " +
            "WHERE ea.app_id = ?";

        jdbcTemplate.update(sql, appId);
    }
}
