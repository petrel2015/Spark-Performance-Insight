package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.annotation.MonitorStep;
import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoldAggregationService {

    private final JdbcTemplate jdbcTemplate;
    private final DuckDBManagerService duckDBManager;
    private final StageService stageService;
    private final Executor executor;
    private final PipelineProgressService progressService;
    private final SystemProperties systemProperties;

    private static final String PHASE = "GOLD_AGGR";

    @Autowired
    @Lazy
    private GoldAggregationService self;

    public GoldAggregationService(
            JdbcTemplate jdbcTemplate, 
            DuckDBManagerService duckDBManager, 
            StageService stageService, 
            @Qualifier("transformationExecutor") Executor executor,
            PipelineProgressService progressService,
            SystemProperties systemProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.duckDBManager = duckDBManager;
        this.stageService = stageService;
        this.executor = executor;
        this.progressService = progressService;
        this.systemProperties = systemProperties;
    }

    @MonitorStep(value = "Full Gold Aggregation", type = "APP")
    public void aggregate(String appId, BiConsumer<Double, String> progressReporter) {
        log.info("Starting Batch Gold aggregation for app: {}", appId);
        duckDBManager.runWithRetry(() -> self.cleanGoldData(appId));

        List<JobInfo> jobs = discoverJobs(appId);
        List<Long> allStageIds = jobs.stream().flatMap(j -> j.stageIds.stream()).distinct().sorted().collect(Collectors.toList());

        int totalStages = allStageIds.size();
        int batchSize = systemProperties.getTransformation().getStageBatchSize();
        Set<String> completedStages = progressService.getCompletedTaskIds(appId, PHASE, "STAGE");

        int processedCount = 0;
        for (int i = 0; i < allStageIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allStageIds.size());
            List<Long> batch = allStageIds.subList(i, end);
            List<Long> filteredBatch = batch.stream().filter(sid -> !completedStages.contains("stage_" + sid + "_0")).collect(Collectors.toList());

            if (!filteredBatch.isEmpty()) {
                duckDBManager.runWithRetry(() -> {
                    self.aggregateStagesBatch(appId, filteredBatch);
                    self.aggregateTasksBatch(appId, filteredBatch);
                    stageService.calculateStageMetricsBatch(appId, filteredBatch);
                });
                for (Long sid : filteredBatch) progressService.markCompleted(appId, PHASE, "STAGE", "stage_" + sid + "_0");
            }
            processedCount += batch.size();
            progressReporter.accept((double) processedCount / totalStages * 60.0, "Gold: Stages processing");
        }

        // Job Aggregation using Java-level logic for 100% reliability
        self.aggregateJobsJava(appId);
        
        CompletableFuture.runAsync(() -> {
            self.aggregateExecutors(appId);
            self.aggregateStorage(appId);
            self.aggregateSql(appId);
            self.aggregateEnvironment(appId);
        }, executor).join();

        duckDBManager.runWithRetry(() -> self.aggregateApp(appId));
        progressReporter.accept(100.0, "Gold: Completed");
    }

    public void aggregate(String appId) { aggregate(appId, (p, m) -> {}); }

    protected void aggregateJobsJava(String appId) {
        log.info("Aggregating Jobs via Java: {}", appId);
        List<JobInfo> jobs = discoverJobs(appId);
        
        for (JobInfo job : jobs) {
            String stageIdsIn = job.stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            if (stageIdsIn.isEmpty()) continue;

            Map<String, Object> stats = jdbcTemplate.queryForMap(
                "SELECT count(*) as n_stages, " +
                "count(case when status = 'COMPLETED' then 1 end) as n_done, " +
                "COALESCE(sum(num_tasks), 0) as t_tasks, " +
                "COALESCE(sum(num_completed_tasks), 0) as t_done, " +
                "COALESCE(avg(performance_score), 100.0) as avg_score " +
                "FROM gold_stages WHERE app_id = ? AND stage_id IN (" + stageIdsIn + ")",
                appId
            );

            Map<String, Object> meta = jdbcTemplate.queryForMap(
                "SELECT submission_time, completion_time, duration_ms, status, description, sql_execution_id, stage_ids FROM silver_jobs WHERE app_id = ? AND job_id = ?",
                appId, job.jobId
            );

            try {
                jdbcTemplate.update(
                    "INSERT INTO gold_jobs (id, app_id, job_id, submission_time, completion_time, duration, status, description, sql_execution_id, stage_ids, num_stages, num_completed_stages, num_failed_stages, num_tasks, num_completed_tasks, num_failed_tasks, performance_score) " +
                    "VALUES (uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0, ?)",
                    appId, 
                    toLong(job.jobId), 
                    toTimestamp(meta.get("submission_time")),
                    toTimestamp(meta.get("completion_time")),
                    toLong(meta.get("duration_ms")), 
                    toString(meta.get("status")),
                    toString(meta.get("description")),
                    toLong(meta.get("sql_execution_id")),
                    toString(meta.get("stage_ids")),
                    toLong(stats.get("n_stages")),
                    toLong(stats.get("n_done")),
                    toLong(stats.get("t_tasks")),
                    toLong(stats.get("t_done")),
                    toDouble(stats.get("avg_score"))
                );
            } catch (Exception e) {
                log.error("Failed to insert gold_job: appId={}, jobId={}, error={}", appId, job.jobId, e.getMessage());
                throw e;
            }
        }
    }

    private Timestamp toTimestamp(Object obj) {
        if (obj == null) return null;
        if (obj instanceof OffsetDateTime odt) return Timestamp.valueOf(odt.toLocalDateTime());
        if (obj instanceof LocalDateTime ldt) return Timestamp.valueOf(ldt);
        if (obj instanceof Timestamp ts) return ts;
        if (obj instanceof java.util.Date date) return new Timestamp(date.getTime());
        return null;
    }

    private Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        if (obj instanceof String s && !s.isEmpty()) {
            try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
        }
        return 0L;
    }

    private Double toDouble(Object obj) {
        if (obj == null) return 100.0;
        if (obj instanceof Number num) return num.doubleValue();
        if (obj instanceof String s && !s.isEmpty()) {
            try { return Double.parseDouble(s); } catch (Exception e) { return 100.0; }
        }
        return 100.0;
    }

    private String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    @MonitorStep(value = "Gold Stages Batch", type = "STAGE")
    protected void aggregateStagesBatch(String appId, List<Long> stageIds) {
        String stageIdsIn = stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = "INSERT INTO gold_stages " +
            "WITH base_metrics AS (SELECT app_id, stage_id, stage_attempt_id, quantile(duration_ms, 0.5) as p50, quantile(duration_ms, 0.75) as p75, quantile(duration_ms, 0.95) as p95, quantile(duration_ms, 0.99) as p99, max(duration_ms) as max_dur, sum(gc_time) as total_gc, sum(executor_run_time) as total_run, sum(duration_ms) as total_duration, sum(shuffle_write_time) as total_sw_time, sum(shuffle_fetch_wait_time) as total_fetch_wait, sum(executor_cpu_time) as total_cpu_time, sum(executor_deserialize_time) as total_deser, sum(result_serialization_time) as total_ser, sum(getting_result_time) as total_get_res, sum(shuffle_read_bytes) as s_read, sum(input_bytes) as i_bytes, sum(scheduler_delay) as total_delay, sum(disk_bytes_spilled) as total_disk_spill, sum(memory_bytes_spilled) as total_mem_spill, max(peak_execution_memory) as max_peak_mem, sum(peak_execution_memory) as total_peak_mem, count(case when status = 'SUCCESS' then 1 end) as done_tasks, count(case when status = 'FAILED' then 1 end) as failed_tasks FROM silver_tasks WHERE app_id = ? AND stage_id IN (" + stageIdsIn + ") GROUP BY app_id, stage_id, stage_attempt_id), " +
            "calculated_scores AS (SELECT *, CASE WHEN p50 > 0 THEN CAST(max_dur AS DOUBLE) / p50 ELSE 1.0 END as skew_ratio, CASE WHEN total_run > 0 THEN CAST(total_gc AS DOUBLE) / total_run ELSE 0.0 END as gc_ratio, CASE WHEN total_duration > 0 THEN total_duration ELSE 1 END as dur_denom, CASE WHEN total_run > 0 THEN total_run ELSE 1 END as run_denom FROM base_metrics) " +
            "SELECT uuid(), s.app_id, s.stage_id, s.job_id, s.attempt_id, s.name, s.num_tasks, cs.done_tasks, cs.failed_tasks, s.submission_time, s.completion_time, s.duration_ms, cs.i_bytes, 0, 0, cs.s_read, 0, 0, 0, 0, cs.total_gc, cs.total_duration, cs.total_deser, cs.total_ser, cs.total_get_res, cs.total_delay, cs.max_peak_mem, cs.total_peak_mem, cs.total_mem_spill, cs.total_disk_spill, cs.total_sw_time, cs.p50, cs.p75, cs.p95, cs.p99, cs.max_dur, s.status, (cs.skew_ratio > 2.0), cs.skew_ratio, cs.gc_ratio, replace(replace(replace(CAST(s.parent_ids AS VARCHAR), '[', ''), ']', ''), ' ', ''), s.rdd_info, (SELECT string_agg(locality || ': ' || cnt, ', ') FROM (SELECT locality, count(*) as cnt FROM silver_tasks st WHERE st.app_id = s.app_id AND st.stage_id = s.stage_id AND st.stage_attempt_id = s.attempt_id AND locality IS NOT NULL GROUP BY locality) loc), json_array(json_object('dimension', 'GC Impact', 'score', CAST(greatest(0, 100 - (cs.gc_ratio * 200)) AS INTEGER)), json_object('dimension', 'Shuffle Write Impact', 'score', CAST(greatest(0, 100 - (cs.total_sw_time / 1000000.0 * 100.0 / cs.dur_denom)) AS INTEGER)), json_object('dimension', 'Shuffle Read Blocked', 'score', CAST(greatest(0, 100 - (cs.total_fetch_wait / 1000000.0 * 100.0 / cs.dur_denom)) AS INTEGER)), json_object('dimension', 'I/O Wait', 'score', CAST(LEAST(100, (cs.total_cpu_time / 1000000.0 * 100.0 / cs.run_denom)) AS INTEGER)), json_object('dimension', 'Serialization Impact', 'score', CAST(greatest(0, 100 - ((cs.total_ser + cs.total_deser) * 100.0 / cs.dur_denom)) AS INTEGER)), json_object('dimension', 'Result Fetching', 'score', CAST(greatest(0, 100 - (cs.total_get_res * 100.0 / cs.dur_denom)) AS INTEGER)), json_object('dimension', 'Scheduler Delay Impact', 'score', CAST(greatest(0, 100 - (cs.total_delay * 100.0 / cs.dur_denom)) AS INTEGER)), json_object('dimension', 'Data Skew', 'score', CAST(greatest(0, 100 - (cs.skew_ratio - 1) * 10) AS INTEGER)), json_object('dimension', 'Disk Spill', 'score', CASE WHEN cs.total_disk_spill > 0 THEN 0 ELSE 100 END)), (greatest(0, 100 - (cs.skew_ratio - 1) * 10) * 0.15 + greatest(0, 100 - (cs.gc_ratio * 200)) * 0.15 + greatest(0, 100 - (cs.total_sw_time / 1000000.0 * 100.0 / cs.dur_denom)) * 0.15 + greatest(0, 100 - (cs.total_fetch_wait / 1000000.0 * 100.0 / cs.dur_denom)) * 0.15 + (CASE WHEN cs.total_disk_spill > 0 THEN 0 ELSE 100 END) * 0.15 + LEAST(100, (cs.total_cpu_time / 1000000.0 * 100.0 / cs.run_denom)) * 0.10 + greatest(0, 100 - (cs.total_delay * 100.0 / cs.dur_denom)) * 0.05 + greatest(0, 100 - ((cs.total_ser + cs.total_deser) * 100.0 / cs.dur_denom)) * 0.05 + greatest(0, 100 - (cs.total_get_res * 100.0 / cs.dur_denom)) * 0.05), greatest(0, 100 - (cs.skew_ratio - 1) * 10), greatest(0, 100 - (cs.gc_ratio * 200)), 100.0, greatest(0, 100 - (cs.total_sw_time / 1000000.0 * 100.0 / cs.dur_denom)), greatest(0, 100 - (cs.total_fetch_wait / 1000000.0 * 100.0 / cs.dur_denom)), LEAST(100, (cs.total_cpu_time / 1000000.0 * 100.0 / cs.run_denom)), greatest(0, 100 - ((cs.total_ser + cs.total_deser) * 100.0 / cs.dur_denom)), greatest(0, 100 - (cs.total_get_res * 100.0 / cs.dur_denom)), greatest(0, 100 - (cs.total_delay * 100.0 / cs.dur_denom)), CASE WHEN cs.total_disk_spill > 0 THEN 0 ELSE 100 END FROM silver_stages s JOIN calculated_scores cs ON s.app_id = cs.app_id AND s.stage_id = cs.stage_id AND s.attempt_id = cs.stage_attempt_id WHERE s.app_id = ? AND s.stage_id IN (" + stageIdsIn + ")";
        jdbcTemplate.update(sql, appId, appId);
    }

    @MonitorStep(value = "Gold Tasks Batch", type = "STAGE")
    protected void aggregateTasksBatch(String appId, List<Long> stageIds) {
        String stageIdsIn = stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("INSERT INTO gold_tasks SELECT uuid(), app_id, stage_id, stage_attempt_id, task_id, index, executor_id, host, epoch(launch_time) * 1000, epoch(finish_time) * 1000, duration_ms, gc_time, scheduler_delay, getting_result_time, executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, peak_execution_memory, input_bytes, 0, 0, 0, memory_bytes_spilled, disk_bytes_spilled, shuffle_read_bytes, 0, shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, 0, 0, speculative, status, locality FROM silver_tasks WHERE app_id = ? AND stage_id IN (" + stageIdsIn + ")", appId);
    }

    @MonitorStep(value = "Gold Storage", type = "APP")
    protected void aggregateStorage(String appId) {
        String blocksSql = "INSERT INTO gold_storage_blocks (id, app_id, rdd_id, block_name, storage_level, memory_size, disk_size, executor_id, host) WITH latest_blocks AS (SELECT app_id, block_id, rdd_id, storage_level, memory_size, disk_size, executor_id, host, status, row_number() OVER (PARTITION BY app_id, block_id, executor_id ORDER BY event_time DESC) as rn FROM silver_storage_blocks WHERE app_id = ?), deleted_rdds AS (SELECT DISTINCT rdd_id FROM silver_storage_blocks WHERE app_id = ? AND status = 'DELETED') SELECT uuid(), lb.app_id, lb.rdd_id, lb.block_id, lb.storage_level, lb.memory_size, lb.disk_size, lb.executor_id, lb.host FROM latest_blocks lb LEFT JOIN deleted_rdds dr ON lb.rdd_id = dr.rdd_id WHERE lb.rn = 1 AND lb.status != 'DELETED' AND dr.rdd_id IS NULL AND (lb.memory_size > 0 OR lb.disk_size > 0) AND lb.block_id NOT LIKE '%_all'";
        jdbcTemplate.update(blocksSql, appId, appId);

        String rddsSql = "INSERT INTO gold_storage_rdds (id, app_id, rdd_id, name, storage_level, num_partitions, num_cached_partitions, memory_size, disk_size) WITH block_agg AS (SELECT rdd_id, max(storage_level) as storage_level, count(DISTINCT block_name) as cached_parts, sum(memory_size) as mem_sum, sum(disk_size) as disk_sum FROM gold_storage_blocks WHERE app_id = ? GROUP BY rdd_id), rdd_last_snapshot AS (SELECT rdd_id, max(cached_parts) as cached_parts, max(mem_size) as mem_size, max(disk_size) as disk_size FROM (SELECT (r->>'RDD ID')::BIGINT as rdd_id, (r->>'Number of Cached Partitions')::INT as cached_parts, (r->>'Memory Size')::BIGINT as mem_size, (r->>'Disk Size')::BIGINT as disk_size FROM silver_stages, unnest(CAST(rdd_info AS JSON[])) as r WHERE app_id = ?) t GROUP BY rdd_id) SELECT uuid(), ri.app_id, ri.rdd_id, ri.name, COALESCE(ba.storage_level, ri.storage_level), ri.num_partitions, COALESCE(ba.cached_parts, rs.cached_parts, 0), COALESCE(ba.mem_sum, rs.mem_size, 0), COALESCE(ba.disk_sum, rs.disk_size, 0) FROM silver_rdd_info ri LEFT JOIN block_agg ba ON ri.rdd_id = ba.rdd_id LEFT JOIN rdd_last_snapshot rs ON ri.rdd_id = rs.rdd_id WHERE ri.app_id = ? AND ri.storage_level NOT LIKE '%NONE%'";
        jdbcTemplate.update(rddsSql, appId, appId, appId);
    }

    @MonitorStep(value = "Gold Executors", type = "APP")
    protected void aggregateExecutors(String appId) {
        String sql = "INSERT INTO gold_executors SELECT uuid(), app_id, executor_id, silver_executors.host, add_time, remove_time, total_cores, 0, TRUE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL, 0, 0, count(case when status = 'FAILED' then 1 end), count(case when status = 'SUCCESS' then 1 end), count(*), sum(duration_ms), sum(gc_time), sum(input_bytes), sum(shuffle_read_bytes), sum(shuffle_write_bytes), remove_reason, avg(duration_ms), sum(executor_cpu_time / 1000000.0) / nullif(sum(executor_run_time), 0), max(peak_execution_memory) FROM silver_executors JOIN silver_tasks USING (app_id, executor_id) WHERE app_id = ? GROUP BY app_id, executor_id, silver_executors.host, add_time, remove_time, total_cores, remove_reason";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Gold SQL", type = "APP")
    protected void aggregateSql(String appId) {
        String sql = "INSERT INTO gold_sql_executions SELECT uuid(), s.app_id, s.execution_id, s.description, s.details, s.physical_plan, s.plan_info, s.start_time, s.end_time, s.duration_ms, s.status, COALESCE(avg(j.performance_score), 100.0) as performance_score FROM silver_sql_executions s LEFT JOIN gold_jobs j ON s.app_id = j.app_id AND s.execution_id = j.sql_execution_id WHERE s.app_id = ? GROUP BY s.app_id, s.execution_id, s.description, s.details, s.physical_plan, s.plan_info, s.start_time, s.end_time, s.duration_ms, s.status";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Gold Environment", type = "APP")
    protected void aggregateEnvironment(String appId) {
        jdbcTemplate.update("INSERT INTO gold_environment_configs (id, app_id, param_key, param_value, category) SELECT uuid(), app_id, param_key, param_value, category FROM silver_environment_configs WHERE app_id = ?", appId);
    }

    @MonitorStep(value = "Gold App", type = "APP")
    protected void aggregateApp(String appId) {
        String sql = "UPDATE gold_applications SET status = 'FINISHED', duration = (SELECT sum(duration) FROM gold_jobs WHERE app_id = ?), performance_score = (SELECT CAST(COALESCE(avg(performance_score), 100) AS INTEGER) FROM gold_jobs WHERE app_id = ?), total_tasks = (SELECT count(*) FROM gold_tasks WHERE app_id = ?), failed_tasks = (SELECT count(*) FROM gold_tasks WHERE app_id = ? AND status = 'FAILED'), total_input_bytes = (SELECT coalesce(sum(input_bytes), 0) FROM gold_stages WHERE app_id = ?), total_shuffle_read_bytes = (SELECT coalesce(sum(shuffle_read_bytes), 0) FROM gold_stages WHERE app_id = ?) WHERE app_id = ?";
        jdbcTemplate.update(sql, appId, appId, appId, appId, appId, appId, appId);
    }

    @MonitorStep(value = "Gold Clean", type = "APP")
    protected void cleanGoldData(String appId) {
        jdbcTemplate.update("DELETE FROM gold_jobs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_stages WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_tasks WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_executors WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_sql_executions WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_environment_configs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_storage_rdds WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_storage_blocks WHERE app_id = ?", appId);
    }

    private List<JobInfo> discoverJobs(String appId) {
        return jdbcTemplate.query("SELECT job_id, stage_ids FROM silver_jobs WHERE app_id = ?", (rs, rowNum) -> {
            String raw = rs.getString("stage_ids");
            List<Long> sids = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(raw);
            while (m.find()) sids.add(Long.parseLong(m.group()));
            return new JobInfo(rs.getLong("job_id"), sids);
        }, appId);
    }

    private static class JobInfo {
        final long jobId;
        final List<Long> stageIds;
        JobInfo(long jobId, List<Long> stageIds) { this.jobId = jobId; this.stageIds = stageIds; }
    }
}
