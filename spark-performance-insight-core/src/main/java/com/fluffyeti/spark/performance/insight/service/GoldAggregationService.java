package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.annotation.MonitorStep;
import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoldAggregationService {

    private final JdbcTemplate jdbcTemplate;
    private final DuckDBManagerService duckDBManager;
    private final StageService stageService;
    private final PipelineProgressService progressService;
    private final SystemProperties systemProperties;

    @Autowired
    @Lazy
    private GoldAggregationService self;

    public GoldAggregationService(
            JdbcTemplate jdbcTemplate, 
            DuckDBManagerService duckDBManager,
            StageService stageService,
            PipelineProgressService progressService,
            SystemProperties systemProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.duckDBManager = duckDBManager;
        this.stageService = stageService;
        this.progressService = progressService;
        this.systemProperties = systemProperties;
    }

    @MonitorStep(value = "Full Gold Aggregation", type = "APP")
    public void aggregate(String appId, BiConsumer<Double, String> progressReporter) {
        log.info("Starting Batch Gold aggregation for app: {}", appId);
        
        duckDBManager.runWithRetry(() -> {
            self.cleanGoldData(appId);
        });

        // 1. First, aggregate stages and tasks (Leaf levels)
        List<Long> allStageIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT stage_id FROM silver_tasks WHERE app_id = ? ORDER BY 1",
            Long.class, appId
        );
        
        if (!allStageIds.isEmpty()) {
            duckDBManager.runWithRetry(() -> {
                self.aggregateTasksBatch(appId, allStageIds);
                self.aggregateStagesBatch(appId, allStageIds);
            });
        }
        progressReporter.accept(40.0, "Tasks and Stages aggregated");

        // 2. Then, aggregate jobs (Depends on gold_stages)
        self.aggregateJobsJava(appId);
        progressReporter.accept(70.0, "Jobs aggregated");

        // 3. Final aggregations
        duckDBManager.runWithRetry(() -> {
            self.aggregateExecutors(appId);
            self.aggregateSql(appId);
            self.aggregateStorage(appId);
            self.aggregateEnvironment(appId);
            self.aggregateApp(appId);
        });
        progressReporter.accept(100.0, "Gold: Completed");
    }

    public void aggregate(String appId) { aggregate(appId, (p, m) -> {}); }

    protected void aggregateJobsJava(String appId) {
        log.info("Aggregating Jobs via Java: {}", appId);
        List<JobInfo> jobs = discoverJobs(appId);
        
        for (JobInfo job : jobs) {
            if (job.stageIds == null || job.stageIds.isEmpty()) {
                log.warn("Job {} has no stages recorded.", job.jobId);
                continue;
            }
            
            String stageIdsIn = job.stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));

            Map<String, Object> stats = jdbcTemplate.queryForMap(
                "SELECT count(*) as n_stages, " +
                "count(case when UPPER(status) = 'COMPLETED' or UPPER(status) = 'SUCCEEDED' then 1 end) as n_done, " +
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

            log.info("Job {} aggregation - n_stages: {}, n_done: {}, t_tasks: {}, t_done: {}", 
                job.jobId, stats.get("n_stages"), stats.get("n_done"), stats.get("t_tasks"), stats.get("t_done"));

            try {
                jdbcTemplate.update(
                    "INSERT INTO gold_jobs (id, app_id, job_id, submission_time, completion_time, duration, status, description, sql_execution_id, stage_ids, num_stages, num_completed_stages, num_failed_stages, num_tasks, num_completed_tasks, num_failed_tasks, performance_score) " +
                    "VALUES (uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0, ?)",
                    appId, 
                    toLong(job.jobId), 
                    toLong(meta.get("submission_time")),
                    toLong(meta.get("completion_time")),
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
        String sql = "INSERT INTO gold_stages (id, app_id, stage_id, job_id, attempt_id, stage_name, num_tasks, num_completed_tasks, num_failed_tasks, submission_time, completion_time, duration, input_bytes, input_records, output_bytes, shuffle_read_bytes, shuffle_read_records, shuffle_write_bytes, shuffle_write_records, output_records, gc_time_sum, tasks_duration_sum, executor_deserialize_time_sum, result_serialization_time_sum, getting_result_time_sum, scheduler_delay_sum, peak_execution_memory_max, peak_execution_memory_sum, memory_bytes_spilled_sum, disk_bytes_spilled_sum, shuffle_write_time_sum, duration_p50, duration_p75, duration_p95, duration_p99, max_task_duration, status, is_skewed, skew_ratio, gc_ratio, parent_stage_ids, rdd_info, locality_summary, diagnosis_info, performance_score, score_skew, score_gc, score_locality, score_shuffle_write, score_shuffle_read, score_io, score_ser, score_result, score_delay, score_spill) " +
            "WITH base_metrics AS (SELECT app_id, stage_id, stage_attempt_id, quantile(duration_ms, 0.5) as p50, quantile(duration_ms, 0.75) as p75, quantile(duration_ms, 0.95) as p95, quantile(duration_ms, 0.99) as p99, max(duration_ms) as max_dur, sum(gc_time) as total_gc, sum(executor_run_time) as total_run, sum(duration_ms) as total_duration, sum(shuffle_write_time) as total_sw_time, sum(shuffle_fetch_wait_time) as total_fetch_wait, sum(executor_cpu_time) as total_cpu_time, sum(executor_deserialize_time) as total_deser, sum(result_serialization_time) as total_ser, sum(getting_result_time) as total_get_res, sum(shuffle_read_bytes) as s_read, sum(input_bytes) as i_bytes, sum(output_bytes) as o_bytes, sum(scheduler_delay) as total_delay, sum(disk_bytes_spilled) as total_disk_spill, sum(memory_bytes_spilled) as total_mem_spill, max(peak_execution_memory) as max_peak_mem, sum(peak_execution_memory) as total_peak_mem, count(case when status = 'SUCCESS' then 1 end) as done_tasks, count(case when status = 'FAILED' then 1 end) as failed_tasks FROM silver_tasks WHERE app_id = ? AND stage_id IN (" + stageIdsIn + ") GROUP BY app_id, stage_id, stage_attempt_id), " +
            "calculated_scores AS (SELECT *, CASE WHEN p50 > 0 THEN CAST(max_dur AS DOUBLE) / p50 ELSE 1.0 END as s_ratio, CASE WHEN total_run > 0 THEN CAST(total_gc AS DOUBLE) / total_run ELSE 0.0 END as g_ratio, CASE WHEN total_duration > 0 THEN total_duration ELSE 1 END as dur_denom, CASE WHEN total_run > 0 THEN total_run ELSE 1 END as run_denom FROM base_metrics), " +
            "final_scores AS (SELECT *, " +
            "CAST(greatest(0, 100 - (s_ratio - 1.0) * 20.0) AS DOUBLE) as sc_skew, " +
            "CAST(greatest(0, 100 - (g_ratio * 400.0)) AS DOUBLE) as sc_gc, " +
            "CAST(greatest(0, 100 - (total_sw_time / 1000000.0 * 100.0 / dur_denom)) AS DOUBLE) as sc_sw, " +
            "CAST(greatest(0, 100 - (total_fetch_wait * 100.0 / dur_denom)) AS DOUBLE) as sc_sr, " +
            "CAST(LEAST(100, (total_cpu_time * 100.0 / nullif(total_run, 0))) AS DOUBLE) as sc_io, " +
            "CAST(greatest(0, 100 - ((total_ser + total_deser) * 100.0 / dur_denom)) AS DOUBLE) as sc_ser, " +
            "CAST(greatest(0, 100 - (total_get_res * 100.0 / dur_denom)) AS DOUBLE) as sc_res, " +
            "CAST(greatest(0, 100 - (total_delay * 100.0 / dur_denom)) AS DOUBLE) as sc_del, " +
            "CAST(CASE WHEN total_disk_spill > 0 THEN 0 ELSE 100 END AS DOUBLE) as sc_spill " +
            "FROM calculated_scores) " +
            "SELECT uuid(), s.app_id, s.stage_id, s.job_id, s.attempt_id, s.name, s.num_tasks, fs.done_tasks, fs.failed_tasks, s.submission_time, s.completion_time, fs.total_duration, fs.i_bytes, 0, fs.o_bytes, fs.s_read, 0, 0, 0, 0, fs.total_gc, fs.total_duration, fs.total_deser, fs.total_ser, fs.total_get_res, fs.total_delay, fs.max_peak_mem, fs.total_peak_mem, fs.total_mem_spill, fs.total_disk_spill, fs.total_sw_time, fs.p50, fs.p75, fs.p95, fs.p99, fs.max_dur, s.status, (fs.s_ratio > 2.0), fs.s_ratio, fs.g_ratio, replace(replace(replace(CAST(s.parent_ids AS VARCHAR), '[', ''), ']', ''), ' ', ''), s.rdd_info, (SELECT string_agg(locality || ': ' || cnt, ', ') FROM (SELECT locality, count(*) as cnt FROM silver_tasks st WHERE st.app_id = s.app_id AND st.stage_id = s.stage_id AND st.stage_attempt_id = s.attempt_id AND locality IS NOT NULL GROUP BY locality) loc), " +
            "json_array(json_object('dimension', 'Data Skew', 'score', fs.sc_skew), json_object('dimension', 'GC Impact', 'score', fs.sc_gc), json_object('dimension', 'Shuffle Write Impact', 'score', fs.sc_sw), json_object('dimension', 'Shuffle Read Blocked', 'score', fs.sc_sr), json_object('dimension', 'I/O Wait', 'score', fs.sc_io), json_object('dimension', 'Serialization Impact', 'score', fs.sc_ser), json_object('dimension', 'Result Fetching', 'score', fs.sc_res), json_object('dimension', 'Scheduler Delay Impact', 'score', fs.sc_del), json_object('dimension', 'Disk Spill', 'score', fs.sc_spill)), " +
            "((fs.sc_skew + fs.sc_gc + fs.sc_sw + fs.sc_sr + fs.sc_io + fs.sc_ser + fs.sc_res + fs.sc_del + fs.sc_spill) / 9.0), " +
            "fs.sc_skew, fs.sc_gc, 100.0, fs.sc_sw, fs.sc_sr, fs.sc_io, fs.sc_ser, fs.sc_res, fs.sc_del, fs.sc_spill " +
            "FROM silver_stages s JOIN final_scores fs ON s.app_id = fs.app_id AND s.stage_id = fs.stage_id AND s.attempt_id = fs.stage_attempt_id WHERE s.app_id = ?";
        jdbcTemplate.update(sql, appId, appId);
    }

    @MonitorStep(value = "Gold Tasks Batch", type = "STAGE")
    protected void aggregateTasksBatch(String appId, List<Long> stageIds) {
        String stageIdsIn = stageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = "INSERT INTO gold_tasks (id, app_id, stage_id, attempt_id, task_id, task_index, executor_id, host, launch_time, finish_time, duration, gc_time, scheduler_delay, getting_result_time, executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, peak_execution_memory, input_bytes, input_records, output_bytes, output_records, memory_bytes_spilled, disk_bytes_spilled, shuffle_read_bytes, shuffle_read_records, shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, shuffle_write_records, shuffle_remote_read, speculative, status, locality) " +
                     "SELECT uuid(), app_id, stage_id, stage_attempt_id, task_id, index, executor_id, host, launch_time, finish_time, duration_ms, gc_time, scheduler_delay, getting_result_time, executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, peak_execution_memory, " +
                     "input_bytes, 0, output_bytes, 0, memory_bytes_spilled, disk_bytes_spilled, " +
                     "shuffle_read_bytes, 0, shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, 0, 0, " +
                     "speculative, status, locality FROM silver_tasks WHERE app_id = ? AND stage_id IN (" + stageIdsIn + ")";
        jdbcTemplate.update(sql, appId);
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
        String sql = "INSERT INTO gold_executors (id, app_id, executor_id, host, add_time, remove_time, total_cores, memory, is_active, rdd_blocks, storage_memory, on_heap_storage_memory, off_heap_storage_memory, peak_jvm_on_heap, peak_jvm_off_heap, peak_execution_on_heap, peak_execution_off_heap, peak_storage_on_heap, peak_storage_off_heap, peak_pool_direct, peak_pool_mapped, disk_used, resources, resource_profile_id, active_tasks, failed_tasks, completed_tasks, total_tasks, task_time_ms, gc_time_ms, input_bytes, shuffle_read_bytes, shuffle_write_bytes, exec_loss_reason, avg_task_duration_ms, cpu_utilization_ratio, max_peak_memory) SELECT uuid(), app_id, executor_id, host, add_time, remove_time, total_cores, 0, TRUE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL, 0, 0, COALESCE(m.failed_tasks, 0), COALESCE(m.done_tasks, 0), COALESCE(m.total_t, 0), COALESCE(m.total_duration, 0), COALESCE(m.total_gc, 0), COALESCE(m.total_input, 0), COALESCE(m.total_shuffle_read, 0), COALESCE(m.total_shuffle_write, 0), remove_reason, COALESCE(m.total_duration / NULLIF(m.total_t, 0), 0), COALESCE(m.total_cpu / NULLIF(m.total_run, 0), 0), COALESCE(m.peak_mem, 0) FROM silver_executors LEFT JOIN (SELECT app_id, executor_id, count(*) as total_t, sum(case when status = 'SUCCESS' or status = 'SUCCEEDED' then 1 else 0 end) as done_tasks, sum(case when status = 'FAILED' then 1 else 0 end) as failed_tasks, sum(duration) as total_duration, sum(gc_time) as total_gc, sum(input_bytes + output_bytes) as total_input, sum(shuffle_read_bytes) as total_shuffle_read, sum(shuffle_write_bytes) as total_shuffle_write, sum(executor_cpu_time) as total_cpu, sum(executor_run_time) as total_run, max(peak_execution_memory) as peak_mem FROM gold_tasks WHERE app_id = ? GROUP BY app_id, executor_id) m USING (app_id, executor_id) WHERE app_id = ?";
        jdbcTemplate.update(sql, appId, appId);
    }

    @MonitorStep(value = "Gold SQL", type = "APP")
    protected void aggregateSql(String appId) {
        String sql = "INSERT INTO gold_sql_executions (id, app_id, execution_id, description, details, physical_plan, plan_info, start_time, end_time, duration, status, performance_score) SELECT uuid(), s.app_id, s.execution_id, s.description, s.details, s.physical_plan, s.plan_info, s.start_time, s.end_time, s.duration_ms, s.status, COALESCE(avg(j.performance_score), 100.0) as performance_score FROM silver_sql_executions s LEFT JOIN gold_jobs j ON s.app_id = j.app_id AND s.execution_id = j.sql_execution_id WHERE s.app_id = ? GROUP BY s.app_id, s.execution_id, s.description, s.details, s.physical_plan, s.plan_info, s.start_time, s.end_time, s.duration_ms, s.status";
        jdbcTemplate.update(sql, appId);
    }

    @MonitorStep(value = "Gold Environment", type = "APP")
    protected void aggregateEnvironment(String appId) {
        jdbcTemplate.update("INSERT INTO gold_environment_configs (id, app_id, param_key, param_value, category) SELECT uuid(), app_id, param_key, param_value, category FROM silver_environment_configs WHERE app_id = ?", appId);
    }

    @MonitorStep(value = "Gold App", type = "APP")
    protected void aggregateApp(String appId) {
        String sql = "UPDATE gold_applications SET status = 'FINISHED', duration = (SELECT sum(duration) FROM gold_jobs WHERE app_id = ?), performance_score = (SELECT CAST(COALESCE(avg(performance_score), 100) AS INTEGER) FROM gold_jobs WHERE app_id = ?), total_tasks = (SELECT count(*) FROM gold_tasks WHERE app_id = ?), failed_tasks = (SELECT count(*) FROM gold_tasks WHERE app_id = ? AND status = 'FAILED'), total_input_bytes = (SELECT coalesce(sum(input_bytes + output_bytes), 0) FROM gold_tasks WHERE app_id = ?), total_shuffle_read_bytes = (SELECT coalesce(sum(shuffle_read_bytes), 0) FROM gold_tasks WHERE app_id = ?) WHERE app_id = ?";
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
