package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.annotation.MonitorStep;
import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SilverTransformationService {

    private final JdbcTemplate jdbcTemplate;
    private final DuckDBManagerService duckDBManager;
    private final Executor executor;
    private final PipelineProgressService progressService;
    private final SystemProperties systemProperties;
    private final ApplicationService applicationService;

    private static final String PHASE = "SILVER_TRANSFORM";

    @Autowired
    @Lazy
    private SilverTransformationService self;

    public SilverTransformationService(
            JdbcTemplate jdbcTemplate, 
            DuckDBManagerService duckDBManager, 
            @Qualifier("transformationExecutor") Executor executor,
            PipelineProgressService progressService,
            SystemProperties systemProperties,
            ApplicationService applicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.duckDBManager = duckDBManager;
        this.executor = executor;
        this.progressService = progressService;
        this.systemProperties = systemProperties;
        this.applicationService = applicationService;
    }

    @MonitorStep(value = "Full Silver Transformation", type = "APP")
    public void transform(String appId, BiConsumer<Double, String> progressReporter) {
        log.info("Starting Batch Silver transformation for app: {}", appId);
        
        duckDBManager.runWithRetry(() -> {
            jdbcTemplate.execute("INSTALL json; LOAD json;");
            applicationService.executeLocked(appId, () -> {
                self.transformApplicationMetadata(appId);
                return null;
            });
        });

        // 1. Discover all unique Stage IDs
        List<Long> allStageIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT (json_extract(raw_json, '$.\"Stage ID\"'))::BIGINT FROM bronze_event_task_end WHERE app_id = ? ORDER BY 1",
            Long.class, appId
        );
        
        int totalStages = allStageIds.size();
        int batchSize = systemProperties.getTransformation().getStageBatchSize();
        log.info("Found {} stages to transform for app: {}. Processing in batches of {}", totalStages, appId, batchSize);

        // 2. Sequential Batch Processing (Prevents OOM from multiple full scans)
        int processedCount = 0;
        Set<String> completedStages = progressService.getCompletedTaskIds(appId, PHASE, "STAGE");

        for (int i = 0; i < allStageIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allStageIds.size());
            List<Long> batch = allStageIds.subList(i, end);
            
            // Filter out stages already completed in this batch to support incremental resume
            List<Long> filteredBatch = batch.stream()
                    .filter(sid -> !completedStages.contains("stage_" + sid + "_0")) // Simplified: check attempt 0
                    .collect(Collectors.toList());

            if (!filteredBatch.isEmpty()) {
                String stageIdsStr = filteredBatch.stream().map(String::valueOf).collect(Collectors.joining(","));
                log.info("Processing Silver batch: stages [{}]", stageIdsStr);

                duckDBManager.runWithRetry(() -> {
                    self.transformTasksForStages(appId, filteredBatch);
                    self.finalizeStagesMetadata(appId, filteredBatch);
                });

                // Mark progress for each stage in the batch
                for (Long sid : filteredBatch) {
                    progressService.markCompleted(appId, PHASE, "STAGE", "stage_" + sid + "_0");
                }
            }

            processedCount += batch.size();
            double pct = (double) processedCount / Math.max(1, totalStages) * 80.0;
            progressReporter.accept(pct, String.format("Silver: Processed %d/%d stages (%.2f%%)", processedCount, totalStages, pct));
        }

        // 3. Parallel independent tasks (Keep using executor for smaller non-IO tasks)
        var currentProgress = new AtomicReference<>(80.0);
        BiConsumer<Double, String> reporter = (increment, message) -> {
            double next = currentProgress.updateAndGet(v -> v + increment);
            progressReporter.accept(Math.min(next, 100.0), "Silver: " + message);
        };

        var otherFuture = CompletableFuture.runAsync(() -> {
            self.transformJobs(appId);
            reporter.accept(5.0, "Jobs completed");
            self.transformExecutors(appId);
            reporter.accept(5.0, "Executors completed");
            self.transformSql(appId);
            reporter.accept(4.0, "SQL completed");
            self.transformStorage(appId);
            reporter.accept(3.0, "Storage completed");
            self.transformEnvironment(appId);
            reporter.accept(3.0, "Environment completed");
        }, executor);

        otherFuture.join();
        progressReporter.accept(100.0, "Silver: Completed");
    }

    public void transform(String appId) {
        transform(appId, (p, m) -> {});
    }

    @MonitorStep(value = "Silver Tasks Batch", type = "STAGE")
    protected void transformTasksForStages(String appId, List<Long> stageIds) {
        String stageIdsIn = stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = "INSERT INTO silver_tasks (app_id, task_id, stage_id, stage_attempt_id, executor_id, host, index, attempt_number, launch_time, finish_time, duration_ms, status, locality, speculative, executor_run_time, executor_cpu_time, gc_time, executor_deserialize_time, result_serialization_time, getting_result_time, scheduler_delay, input_bytes, output_bytes, shuffle_read_bytes, shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, memory_bytes_spilled, disk_bytes_spilled, peak_execution_memory) " +
                     "SELECT DISTINCT ON (app_id, (json_extract(raw_json, '$.\"Task Info\".\"Task ID\"'))::BIGINT) " +
                     "    app_id, (json_extract(raw_json, '$.\"Task Info\".\"Task ID\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Stage ID\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Stage Attempt ID\"'))::BIGINT, " +
                     "    json_extract_string(raw_json, '$.\"Task Info\".\"Executor ID\"'), " +
                     "    json_extract_string(raw_json, '$.\"Task Info\".Host'), " +
                     "    (json_extract(raw_json, '$.\"Task Info\".Index'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Info\".Attempt'))::BIGINT, " +
                     "    epoch_ms((json_extract(raw_json, '$.\"Task Info\".\"Launch Time\"'))::BIGINT), " +
                     "    epoch_ms((json_extract(raw_json, '$.\"Task Info\".\"Finish Time\"'))::BIGINT), " +
                     "    (json_extract(raw_json, '$.\"Task Info\".\"Finish Time\"'))::BIGINT - (json_extract(raw_json, '$.\"Task Info\".\"Launch Time\"'))::BIGINT, " +
                     "    CASE WHEN (json_extract(raw_json, '$.\"Task Info\".Failed'))::BOOLEAN THEN 'FAILED' ELSE 'SUCCESS' END, " +
                     "    json_extract_string(raw_json, '$.\"Task Info\".Locality'), " +
                     "    (json_extract(raw_json, '$.\"Task Info\".Speculative'))::BOOLEAN, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Executor Run Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Executor CPU Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"JVM GC Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Executor Deserialize Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Result Serialization Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Info\".\"Getting Result Time\"'))::BIGINT, " +
                     "    ((json_extract(raw_json, '$.\"Task Info\".\"Finish Time\"'))::BIGINT - (json_extract(raw_json, '$.\"Task Info\".\"Launch Time\"'))::BIGINT) - " +
                     "    COALESCE((json_extract(raw_json, '$.\"Task Metrics\".\"Executor Run Time\"'))::BIGINT, 0) - " +
                     "    COALESCE((json_extract(raw_json, '$.\"Task Metrics\".\"Executor Deserialize Time\"'))::BIGINT, 0) - " +
                     "    COALESCE((json_extract(raw_json, '$.\"Task Metrics\".\"Result Serialization Time\"'))::BIGINT, 0) - " +
                     "    COALESCE((json_extract(raw_json, '$.\"Task Info\".\"Getting Result Time\"'))::BIGINT, 0), " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Input Metrics\".\"Bytes Read\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Output Metrics\".\"Bytes Written\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Shuffle Read Metrics\".\"Total Bytes Read\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Shuffle Read Metrics\".\"Fetch Wait Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Shuffle Write Metrics\".\"Bytes Written\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Shuffle Write Metrics\".\"Write Time\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Memory Bytes Spilled\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Disk Bytes Spilled\"'))::BIGINT, " +
                     "    (json_extract(raw_json, '$.\"Task Metrics\".\"Peak Execution Memory\"'))::BIGINT " +
                     "FROM bronze_event_task_end " +
                     "WHERE app_id = ? AND (json_extract(raw_json, '$.\"Stage ID\"'))::BIGINT IN (" + stageIdsIn + ") " +
                     "ORDER BY app_id, (json_extract(raw_json, '$.\"Task Info\".\"Task ID\"'))::BIGINT, ingested_at DESC";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Silver Stages Metadata Batch", type = "STAGE")
    protected void finalizeStagesMetadata(String appId, List<Long> stageIds) {
        String stageIdsIn = stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = "INSERT INTO silver_stages (app_id, stage_id, attempt_id, job_id, name, num_tasks, status, submission_time, completion_time, duration_ms, input_bytes, shuffle_read_bytes, parent_ids, rdd_info) " +
                     "WITH stage_job_map AS (SELECT DISTINCT sid, job_id FROM (SELECT (json_extract(raw_json, '$.\"Job ID\"'))::BIGINT as job_id, unnest(CAST(json_extract(raw_json, '$.\"Stage IDs\"') AS BIGINT[])) as sid FROM bronze_event_job_start WHERE app_id = ?) WHERE sid IN (" + stageIdsIn + ")), " +
                     "ss_info AS (SELECT DISTINCT ON (app_id, sid) (json_extract(raw_json, '$.\"Stage Info\".\"Stage ID\"'))::BIGINT as sid, (json_extract(raw_json, '$.\"Stage Info\".\"Stage Attempt ID\"'))::BIGINT as aid, raw_json FROM bronze_event_stage_submitted WHERE app_id = ? AND sid IN (" + stageIdsIn + ") ORDER BY app_id, sid, aid DESC, ingested_at DESC), " +
                     "sc_info AS (SELECT DISTINCT ON (app_id, sid) (json_extract(raw_json, '$.\"Stage Info\".\"Stage ID\"'))::BIGINT as sid, (json_extract(raw_json, '$.\"Stage Info\".\"Stage Attempt ID\"'))::BIGINT as aid, raw_json FROM bronze_event_stage_completed WHERE app_id = ? AND sid IN (" + stageIdsIn + ") ORDER BY app_id, sid, aid DESC, ingested_at DESC), " +
                     "task_metrics AS (SELECT stage_id, sum(input_bytes) as input_sum, sum(shuffle_read_bytes) as shuffle_sum FROM silver_tasks WHERE app_id = ? AND stage_id IN (" + stageIdsIn + ") GROUP BY stage_id) " +
                     "SELECT DISTINCT ON (ss.sid, ss.aid) ?, ss.sid, ss.aid, jm.job_id, json_extract_string(ss.raw_json, '$.\"Stage Info\".\"Stage Name\"'), (json_extract(ss.raw_json, '$.\"Stage Info\".\"Number of Tasks\"'))::BIGINT, CASE WHEN sc.raw_json IS NOT NULL THEN 'COMPLETED' ELSE 'PENDING' END, epoch_ms((json_extract(ss.raw_json, '$.\"Stage Info\".\"Submission Time\"'))::BIGINT), epoch_ms((json_extract(sc.raw_json, '$.\"Stage Info\".\"Completion Time\"'))::BIGINT), (json_extract(sc.raw_json, '$.\"Stage Info\".\"Completion Time\"'))::BIGINT - (json_extract(ss.raw_json, '$.\"Stage Info\".\"Submission Time\"'))::BIGINT, coalesce(tm.input_sum, 0), coalesce(tm.shuffle_sum, 0), json_extract(ss.raw_json, '$.\"Stage Info\".\"Parent IDs\"'), json_extract_string(ss.raw_json, '$.\"Stage Info\".\"RDD Info\"') " +
                     "FROM ss_info ss " +
                     "LEFT JOIN stage_job_map jm ON ss.sid = jm.sid " +
                     "LEFT JOIN sc_info sc ON ss.sid = sc.sid AND ss.aid = sc.aid " +
                     "LEFT JOIN task_metrics tm ON ss.sid = tm.stage_id";
        jdbcTemplate.update(sql, appId, appId, appId, appId, appId);
    }

    @MonitorStep(value = "Silver Metadata", type = "APP")
    protected void transformApplicationMetadata(String appId) {
        String sql = "UPDATE gold_applications " +
                     "SET app_name = json_extract_string(b.raw_json, '$.\"App Name\"'), " +
                     "    user_name = json_extract_string(b.raw_json, '$.User'), " +
                     "    start_time = epoch_ms((json_extract(b.raw_json, '$.Timestamp'))::BIGINT), " +
                     "    spark_version = (SELECT json_extract_string(raw_json, '$.\"Spark Version\"') FROM bronze_event_log_start WHERE app_id = ? LIMIT 1) " +
                     "FROM bronze_event_application_start b " +
                     "WHERE gold_applications.app_id = b.app_id AND b.app_id = ?";
        jdbcTemplate.update(sql, appId, appId);
    }

    @MonitorStep(value = "Silver Jobs", type = "JOB")
    protected void transformJobs(String appId) {
        String sql = "INSERT INTO silver_jobs " +
                     "SELECT DISTINCT ON (js.app_id, (json_extract(js.raw_json, '$.\"Job ID\"'))::BIGINT) " +
                     "    js.app_id, (json_extract(js.raw_json, '$.\"Job ID\"'))::BIGINT, " +
                     "    epoch_ms((json_extract(js.raw_json, '$.\"Submission Time\"'))::BIGINT), " +
                     "    epoch_ms((json_extract(je.raw_json, '$.\"Completion Time\"'))::BIGINT), " +
                     "    (json_extract(je.raw_json, '$.\"Completion Time\"'))::BIGINT - (json_extract(js.raw_json, '$.\"Submission Time\"'))::BIGINT, " +
                     "    json_extract_string(je.raw_json, '$.\"Job Result\".Result'), " +
                     "    json_array_length(json_extract(js.raw_json, '$.\"Stage IDs\"')), " +
                     "    json_extract(js.raw_json, '$.\"Stage IDs\"'), " +
                     "    json_extract_string(js.raw_json, '$.Properties.\"spark.job.description\"'), " +
                     "    (json_extract(js.raw_json, '$.Properties.\"spark.sql.execution.id\"'))::BIGINT " +
                     "FROM bronze_event_job_start js " +
                     "LEFT JOIN bronze_event_job_end je ON js.app_id = je.app_id AND (json_extract(js.raw_json, '$.\"Job ID\"'))::BIGINT = (json_extract(je.raw_json, '$.\"Job ID\"'))::BIGINT " +
                     "WHERE js.app_id = ? ORDER BY js.app_id, (json_extract(js.raw_json, '$.\"Job ID\"'))::BIGINT, js.ingested_at DESC";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Silver Executors", type = "APP")
    protected void transformExecutors(String appId) {
        String sql = "INSERT INTO silver_executors " +
                     "SELECT DISTINCT ON (ea.app_id, json_extract_string(ea.raw_json, '$.\"Executor ID\"')) " +
                     "    ea.app_id, json_extract_string(ea.raw_json, '$.\"Executor ID\"'), " +
                     "    json_extract_string(ea.raw_json, '$.\"Executor Info\".Host'), " +
                     "    (json_extract(ea.raw_json, '$.\"Executor Info\".\"Total Cores\"'))::BIGINT, " +
                     "    epoch_ms((json_extract(ea.raw_json, '$.Timestamp'))::BIGINT), " +
                     "    epoch_ms((json_extract(er.raw_json, '$.Timestamp'))::BIGINT), " +
                     "    json_extract_string(er.raw_json, '$.\"Removed Reason\"') " +
                     "FROM bronze_event_executor_added ea " +
                     "LEFT JOIN bronze_event_executor_removed er ON ea.app_id = er.app_id AND json_extract_string(ea.raw_json, '$.\"Executor ID\"') = json_extract_string(er.raw_json, '$.\"Executor ID\"') " +
                     "WHERE ea.app_id = ? ORDER BY ea.app_id, json_extract_string(ea.raw_json, '$.\"Executor ID\"'), ea.ingested_at DESC";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Silver SQL", type = "APP")
    protected void transformSql(String appId) {
        String sql = "INSERT INTO silver_sql_executions " +
                     "SELECT DISTINCT ON (s.app_id, (json_extract(s.raw_json, '$.executionId'))::BIGINT) " +
                     "    s.app_id, (json_extract(s.raw_json, '$.executionId'))::BIGINT, " +
                     "    json_extract_string(s.raw_json, '$.description'), " +
                     "    json_extract_string(s.raw_json, '$.details'), " +
                     "    json_extract_string(s.raw_json, '$.physicalPlanDescription'), " +
                     "    json_extract_string(s.raw_json, '$.sparkPlanInfo'), " +
                     "    epoch_ms((json_extract(s.raw_json, '$.time'))::BIGINT), " +
                     "    epoch_ms((json_extract(e.raw_json, '$.time'))::BIGINT), " +
                     "    (json_extract(e.raw_json, '$.time'))::BIGINT - (json_extract(s.raw_json, '$.time'))::BIGINT, " +
                     "    CASE WHEN e.raw_json IS NOT NULL THEN 'COMPLETED' ELSE 'RUNNING' END " +
                     "FROM bronze_event_sql_execution_start s " +
                     "LEFT JOIN bronze_event_sql_execution_end e ON s.app_id = e.app_id AND (json_extract(s.raw_json, '$.executionId'))::BIGINT = (json_extract(e.raw_json, '$.executionId'))::BIGINT " +
                     "WHERE s.app_id = ? ORDER BY s.app_id, (json_extract(s.raw_json, '$.executionId'))::BIGINT, s.ingested_at DESC";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Silver Environment", type = "APP")
    protected void transformEnvironment(String appId) {
        String sql = "INSERT INTO silver_environment_configs " +
                     "SELECT app_id, k, (json_extract(raw_json, '$.\"Spark Properties\"')->>k), 'spark_conf' " +
                     "FROM (SELECT app_id, raw_json, unnest(json_keys(json_extract(raw_json, '$.\"Spark Properties\"'))) as k FROM bronze_event_environment_update WHERE app_id = ?) " +
                     "UNION ALL " +
                     "SELECT app_id, k, (json_extract(raw_json, '$.\"JVM Information\"')->>k), 'jvm_info' " +
                     "FROM (SELECT app_id, raw_json, unnest(json_keys(json_extract(raw_json, '$.\"JVM Information\"'))) as k FROM bronze_event_environment_update WHERE app_id = ?) " +
                     "UNION ALL " +
                     "SELECT app_id, k, (json_extract(raw_json, '$.\"System Properties\"')->>k), 'system_props' " +
                     "FROM (SELECT app_id, raw_json, unnest(json_keys(json_extract(raw_json, '$.\"System Properties\"'))) as k FROM bronze_event_environment_update WHERE app_id = ?)";
        try { jdbcTemplate.update(sql, appId, appId, appId); } catch (Exception e) { log.warn("Env failed: {}", e.getMessage()); }
    }

    @MonitorStep(value = "Silver Storage", type = "APP")
    protected void transformStorage(String appId) {
        String rddSql = "INSERT INTO silver_rdd_info SELECT DISTINCT ON (app_id, rdd_id) app_id, (rdd->>'RDD ID')::BIGINT as rdd_id, rdd->>'Name', rdd->>'Storage Level', (rdd->>'Number of Partitions')::BIGINT, rdd->>'Callsite', rdd->>'Scope' FROM (SELECT app_id, unnest(json_transform(json_extract(raw_json, '$.\"Stage Info\".\"RDD Info\"'), '[\"JSON\"]')) as rdd FROM bronze_event_stage_submitted WHERE app_id = ?) WHERE rdd IS NOT NULL ORDER BY app_id, rdd_id";
        jdbcTemplate.update(rddSql, appId);
        
        String blockSql = "INSERT INTO silver_storage_blocks SELECT app_id, json_extract_string(raw_json, '$.\"Block ID\"'), (regexp_extract(json_extract_string(raw_json, '$.\"Block ID\"'), 'rdd_(\\d+)_', 1))::BIGINT, json_extract_string(raw_json, '$.\"Block ID\"'), json_extract_string(raw_json, '$.\"Storage Level\"'), (json_extract(raw_json, '$.\"Memory Size\"'))::BIGINT, (json_extract(raw_json, '$.\"Disk Size\"'))::BIGINT, COALESCE(json_extract_string(raw_json, '$.\"Block Manager ID\".\"Executor ID\"'), json_extract_string(raw_json, '$.\"Executor ID\"')), json_extract_string(raw_json, '$.\"Block Manager ID\".Host'), 'UPDATED', epoch_ms((json_extract(raw_json, '$.Timestamp'))::BIGINT) FROM bronze_event_block_updated WHERE app_id = ?";
        jdbcTemplate.update(blockSql, appId);
    }

    public void cleanSilverData(String appId) {
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
}
