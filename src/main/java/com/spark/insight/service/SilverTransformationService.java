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
    private final DuckDBManagerService duckDBManager;

    @Transactional
    public void transform(String appId, java.util.function.BiConsumer<Double, String> progressReporter) {
        log.info("Starting Silver transformation (Metadata & Feature Recovery) for app: {}", appId);
        progressReporter.accept(0.0, "Silver: Initializing...");
        
        duckDBManager.runWithRetry(() -> {
            jdbcTemplate.execute("INSTALL json; LOAD json;");
            cleanSilverData(appId);
        });

        // 1. Extract Application Metadata First (to update "Initializing...")
        progressReporter.accept(5.0, "Silver: Extracting Metadata...");
        duckDBManager.runWithRetry(() -> transformApplicationMetadata(appId));

        // 2. Core transformations - Estimated weights based on complexity
        progressReporter.accept(10.0, "Silver: Transforming Tasks (Heavy)...");
        duckDBManager.runWithRetry(() -> transformTasks(appId));
        
        progressReporter.accept(40.0, "Silver: Transforming Stages...");
        duckDBManager.runWithRetry(() -> transformStages(appId));
        
        progressReporter.accept(60.0, "Silver: Transforming Jobs...");
        duckDBManager.runWithRetry(() -> transformJobs(appId));
        
        progressReporter.accept(75.0, "Silver: Transforming Executors...");
        duckDBManager.runWithRetry(() -> transformExecutors(appId));
        
        progressReporter.accept(85.0, "Silver: Transforming SQL Executions...");
        duckDBManager.runWithRetry(() -> transformSql(appId));

        progressReporter.accept(90.0, "Silver: Transforming Storage (RDDs & Blocks)...");
        duckDBManager.runWithRetry(() -> transformStorage(appId));
        
        progressReporter.accept(95.0, "Silver: Finalizing Environment...");
        duckDBManager.runWithRetry(() -> transformEnvironment(appId));

        progressReporter.accept(100.0, "Silver: Completed");
        log.info("Finished Silver transformation for app: {}", appId);
    }
    
    public void transform(String appId) {
        transform(appId, (p, m) -> {});
    }

    private void cleanSilverData(String appId) {
        log.info("Cleaning Silver data for app: {}", appId);
        jdbcTemplate.update("DELETE FROM silver_jobs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_stages WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_tasks WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_executors WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_sql_executions WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_environment_configs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_storage_blocks WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM silver_rdd_info WHERE app_id = ?", appId);
    }

    private void transformApplicationMetadata(String appId) {
        log.debug("Updating Application Metadata for app: {}", appId);
        
        // Extract from ApplicationStart and LogStart events
        String sql = """
            UPDATE gold_applications
            SET app_name = json_extract_string(b.raw_json, '$."App Name"'),
                user_name = json_extract_string(b.raw_json, '$.User'),
                start_time = epoch_ms((json_extract(b.raw_json, '$.Timestamp'))::BIGINT),
                spark_version = (SELECT json_extract_string(raw_json, '$."Spark Version"') FROM bronze_event_log_start WHERE app_id = ? LIMIT 1)
            FROM bronze_event_application_start b
            WHERE gold_applications.app_id = b.app_id AND b.app_id = ?
            """;
        jdbcTemplate.update(sql, appId, appId);
    }

    private void transformJobs(String appId) {
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
        String sql = """
            WITH stage_job_map AS (
                SELECT DISTINCT ON (app_id, sid)
                    app_id, sid as stage_id, job_id
                FROM (
                    SELECT app_id, (json_extract(raw_json, '$."Job ID"'))::INT as job_id, unnest(CAST(json_extract(raw_json, '$."Stage IDs"') AS INT[])) as sid
                    FROM bronze_event_job_start WHERE app_id = ?
                )
            ),
            ss_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT)
                    app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT as stage_id, 
                    (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT as attempt_id, raw_json
                FROM bronze_event_stage_submitted WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT DESC
            ),
            sc_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT)
                    app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT as stage_id, 
                    (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT as attempt_id, raw_json
                FROM bronze_event_stage_completed WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Stage Info"."Stage ID"'))::INT, (json_extract(raw_json, '$."Stage Info"."Stage Attempt ID"'))::INT DESC
            ),
            task_metrics AS (
                SELECT stage_id, stage_attempt_id, sum(input_bytes) as input_sum, sum(shuffle_read_bytes) as shuffle_sum
                FROM silver_tasks WHERE app_id = ? GROUP BY stage_id, stage_attempt_id
            )
            INSERT INTO silver_stages (app_id, stage_id, attempt_id, job_id, name, num_tasks, status, submission_time, completion_time, duration_ms, input_bytes, shuffle_read_bytes, parent_ids, rdd_info)
            SELECT DISTINCT ON (ss.app_id, ss.stage_id, ss.attempt_id)
                ss.app_id, ss.stage_id, ss.attempt_id,
                jm.job_id,
                json_extract_string(ss.raw_json, '$."Stage Info"."Stage Name"'),
                (json_extract(ss.raw_json, '$."Stage Info"."Number of Tasks"'))::INT,
                CASE WHEN sc.raw_json IS NOT NULL THEN 'COMPLETED' ELSE 'PENDING' END,
                epoch_ms((json_extract(ss.raw_json, '$."Stage Info"."Submission Time"'))::BIGINT),
                epoch_ms((json_extract(sc.raw_json, '$."Stage Info"."Completion Time"'))::BIGINT),
                (json_extract(sc.raw_json, '$."Stage Info"."Completion Time"'))::BIGINT - (json_extract(ss.raw_json, '$."Stage Info"."Submission Time"'))::BIGINT,
                coalesce(tm.input_sum, 0),
                coalesce(tm.shuffle_sum, 0),
                json_extract(ss.raw_json, '$."Stage Info"."Parent IDs"'),
                json_extract_string(ss.raw_json, '$."Stage Info"."RDD Info"')
            FROM ss_dedup ss
            LEFT JOIN stage_job_map jm ON ss.app_id = jm.app_id AND ss.stage_id = jm.stage_id
            LEFT JOIN sc_dedup sc ON ss.app_id = sc.app_id AND ss.stage_id = sc.stage_id AND ss.attempt_id = sc.attempt_id
            LEFT JOIN task_metrics tm ON ss.stage_id = tm.stage_id AND ss.attempt_id = tm.stage_attempt_id
            ORDER BY ss.app_id, ss.stage_id, ss.attempt_id
            """;
        jdbcTemplate.update(sql, appId, appId, appId, appId);
    }

    private void transformTasks(String appId) {
        String sql = """
            WITH task_dedup AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT)
                    raw_json, app_id, (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT as task_id
                FROM bronze_event_task_end WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."Task Info"."Task ID"'))::BIGINT, ingested_at DESC
            )
            INSERT INTO silver_tasks (app_id, task_id, stage_id, stage_attempt_id, executor_id, host, index, attempt_number, launch_time, finish_time, duration_ms, status, locality, speculative, executor_run_time, executor_cpu_time, gc_time, executor_deserialize_time, result_serialization_time, getting_result_time, scheduler_delay, input_bytes, output_bytes, shuffle_read_bytes, shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, memory_bytes_spilled, disk_bytes_spilled, peak_execution_memory)
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
                (json_extract(raw_json, '$."Task Metrics"."Executor Deserialize Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Result Serialization Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Info"."Getting Result Time"'))::BIGINT,
                -- Scheduler Delay = Duration - RunTime - Deser - Ser - GettingResult
                ((json_extract(raw_json, '$."Task Info"."Finish Time"'))::BIGINT - (json_extract(raw_json, '$."Task Info"."Launch Time"'))::BIGINT) - 
                COALESCE((json_extract(raw_json, '$."Task Metrics"."Executor Run Time"'))::BIGINT, 0) - 
                COALESCE((json_extract(raw_json, '$."Task Metrics"."Executor Deserialize Time"'))::BIGINT, 0) - 
                COALESCE((json_extract(raw_json, '$."Task Metrics"."Result Serialization Time"'))::BIGINT, 0) - 
                COALESCE((json_extract(raw_json, '$."Task Info"."Getting Result Time"'))::BIGINT, 0),
                (json_extract(raw_json, '$."Task Metrics"."Input Metrics"."Bytes Read"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Output Metrics"."Bytes Written"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Shuffle Read Metrics"."Total Bytes Read"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Shuffle Read Metrics"."Fetch Wait Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Shuffle Write Metrics"."Bytes Written"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Shuffle Write Metrics"."Write Time"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Memory Bytes Spilled"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Disk Bytes Spilled"'))::BIGINT,
                (json_extract(raw_json, '$."Task Metrics"."Peak Execution Memory"'))::BIGINT
            FROM task_dedup
            ORDER BY app_id, task_id
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void transformExecutors(String appId) {
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

    private void transformSql(String appId) {
        String sql = """
            WITH sql_start AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."executionId"'))::BIGINT)
                    app_id, (json_extract(raw_json, '$."executionId"'))::BIGINT as execution_id, raw_json
                FROM bronze_event_sql_execution_start WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."executionId"'))::BIGINT, ingested_at DESC
            ),
            sql_end AS (
                SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$."executionId"'))::BIGINT)
                    app_id, (json_extract(raw_json, '$."executionId"'))::BIGINT as execution_id, raw_json
                FROM bronze_event_sql_execution_end WHERE app_id = ?
                ORDER BY app_id, (json_extract(raw_json, '$."executionId"'))::BIGINT, ingested_at DESC
            )
            INSERT INTO silver_sql_executions
            SELECT DISTINCT ON (s.app_id, s.execution_id)
                s.app_id, s.execution_id,
                json_extract_string(s.raw_json, '$.description'),
                json_extract_string(s.raw_json, '$.details'),
                json_extract_string(s.raw_json, '$.physicalPlanDescription'),
                json_extract_string(s.raw_json, '$.sparkPlanInfo'),
                epoch_ms((json_extract(s.raw_json, '$.time'))::BIGINT),
                epoch_ms((json_extract(e.raw_json, '$.time'))::BIGINT),
                (json_extract(e.raw_json, '$.time'))::BIGINT - (json_extract(s.raw_json, '$.time'))::BIGINT,
                CASE WHEN e.raw_json IS NOT NULL THEN 'COMPLETED' ELSE 'RUNNING' END
            FROM sql_start s
            LEFT JOIN sql_end e ON s.app_id = e.app_id AND s.execution_id = e.execution_id
            ORDER BY s.app_id, s.execution_id
            """;
        jdbcTemplate.update(sql, appId, appId);
    }

    private void transformEnvironment(String appId) {
        log.debug("Transforming Environment Configs for app: {}", appId);
        String sql = """
            INSERT INTO silver_environment_configs
            SELECT app_id, k, (json_extract(raw_json, '$."Spark Properties"')->>k), 'spark_conf'
            FROM (
                SELECT app_id, raw_json, unnest(json_keys(json_extract(raw_json, '$."Spark Properties"'))) as k
                FROM bronze_event_environment_update WHERE app_id = ?
            )
            UNION ALL
            SELECT app_id, k, (json_extract(raw_json, '$."JVM Information"')->>k), 'jvm_info'
            FROM (
                SELECT app_id, raw_json, unnest(json_keys(json_extract(raw_json, '$."JVM Information"'))) as k
                FROM bronze_event_environment_update WHERE app_id = ?
            )
            UNION ALL
            SELECT app_id, k, (json_extract(raw_json, '$."System Properties"')->>k), 'system_props'
            FROM (
                SELECT app_id, raw_json, unnest(json_keys(json_extract(raw_json, '$."System Properties"'))) as k
                FROM bronze_event_environment_update WHERE app_id = ?
            )
            """;
        try {
            jdbcTemplate.update(sql, appId, appId, appId);
        } catch (Exception e) {
            log.warn("Environment transformation failed: {}", e.getMessage());
        }
    }

    private void transformStorage(String appId) {
        log.info("Transforming Storage (RDD & Blocks) for app: {}", appId);

        // 1. Extract RDD metadata from Stage Submitted events
        // Using json_transform to ensure we handle the JSON array correctly
        String rddSql = """
            INSERT INTO silver_rdd_info
            SELECT DISTINCT ON (app_id, rdd_id)
                app_id, 
                (rdd->>'RDD ID')::INT as rdd_id,
                rdd->>'Name' as name,
                rdd->>'Storage Level' as storage_level,
                (rdd->>'Number of Partitions')::INT as num_partitions,
                rdd->>'Callsite' as callsite,
                rdd->>'Scope' as scope
            FROM (
                SELECT app_id, unnest(json_transform(json_extract(raw_json, '$."Stage Info"."RDD Info"'), '["JSON"]')) as rdd
                FROM bronze_event_stage_submitted WHERE app_id = ?
            )
            WHERE rdd IS NOT NULL
            ORDER BY app_id, rdd_id, (rdd->>'RDD ID')::INT
            """;
        jdbcTemplate.update(rddSql, appId);

        // 2. Extract Block Updates (from SparkListenerBlockUpdated)
        String blockSql = """
            INSERT INTO silver_storage_blocks
            SELECT 
                app_id,
                json_extract_string(raw_json, '$."Block ID"') as block_id,
                (regexp_extract(json_extract_string(raw_json, '$."Block ID"'), 'rdd_(\\d+)_', 1))::INT as rdd_id,
                json_extract_string(raw_json, '$."Block ID"') as name,
                json_extract_string(raw_json, '$."Storage Level"') as storage_level,
                (json_extract(raw_json, '$."Memory Size"'))::BIGINT as memory_size,
                (json_extract(raw_json, '$."Disk Size"'))::BIGINT as disk_size,
                COALESCE(json_extract_string(raw_json, '$."Block Manager ID"."Executor ID"'), json_extract_string(raw_json, '$."Executor ID"')) as executor_id,
                json_extract_string(raw_json, '$."Block Manager ID".Host') as host,
                'UPDATED' as status,
                epoch_ms((json_extract(raw_json, '$.Timestamp'))::BIGINT) as event_time
            FROM bronze_event_block_updated WHERE app_id = ?
            """;
        jdbcTemplate.update(blockSql, appId);

        // 3. ALSO extract blocks from SparkListenerTaskEnd (many Spark logs put updates here)
        String taskEndBlockSql = """
            INSERT INTO silver_storage_blocks
            SELECT 
                app_id,
                b->>'Block ID' as block_id,
                (regexp_extract(b->>'Block ID', 'rdd_(\\d+)_', 1))::INT as rdd_id,
                b->>'Block ID' as name,
                b->>'Storage Level' as storage_level,
                (b->>'Memory Size')::BIGINT as memory_size,
                (b->>'Disk Size')::BIGINT as disk_size,
                json_extract_string(raw_json, '$."Task Info"."Executor ID"') as executor_id,
                json_extract_string(raw_json, '$."Task Info".Host') as host,
                'UPDATED' as status,
                epoch_ms((json_extract(raw_json, '$."Task Info"."Finish Time"'))::BIGINT)
            FROM (
                SELECT app_id, raw_json, unnest(json_transform(json_extract(raw_json, '$."Task Metrics"."Updated Blocks"'), '["JSON"]')) as b
                FROM bronze_event_task_end WHERE app_id = ?
            )
            WHERE b IS NOT NULL
            """;
        jdbcTemplate.update(taskEndBlockSql, appId);

        // 4. Handle Unpersist RDD (Mark blocks as DELETED)
        String unpersistSql = """
            INSERT INTO silver_storage_blocks (app_id, block_id, rdd_id, status, event_time)
            SELECT 
                app_id,
                'rdd_' || (json_extract(raw_json, '$."RDD ID"'))::INT || '_all' as block_id,
                (json_extract(raw_json, '$."RDD ID"'))::INT as rdd_id,
                'DELETED' as status,
                epoch_ms((json_extract(raw_json, '$.Timestamp'))::BIGINT)
            FROM bronze_event_unpersist_rdd WHERE app_id = ?
            """;
        jdbcTemplate.update(unpersistSql, appId);
    }
}
