package com.spark.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoldAggregationService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void aggregate(String appId) {
        log.info("Starting Gold aggregation and Sync (SQL Recovery Mode) for app: {}", appId);

        cleanGoldData(appId);

        aggregateStages(appId);
        aggregateJobs(appId);
        aggregateExecutors(appId);
        aggregateSql(appId);
        aggregateEnvironment(appId);
        aggregateApp(appId);

        log.info("Finished Gold aggregation and Sync for app: {}", appId);
    }

    private void cleanGoldData(String appId) {
        log.info("Cleaning Gold data for app: {}", appId);
        // Clean the actual gold tables
        jdbcTemplate.update("DELETE FROM gold_jobs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_stages WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_tasks WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_executors WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_sql_executions WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM gold_environment_configs WHERE app_id = ?", appId);
    }

    private void aggregateStages(String appId) {
        String sql = """
            INSERT INTO gold_stages
            WITH base_metrics AS (
                SELECT
                    app_id, stage_id, stage_attempt_id,
                    quantile(duration_ms, 0.5) as p50,
                    quantile(duration_ms, 0.75) as p75,
                    quantile(duration_ms, 0.95) as p95,
                    quantile(duration_ms, 0.99) as p99,
                    max(duration_ms) as max_dur,
                    sum(gc_time) as total_gc,
                    sum(executor_run_time) as total_run,
                    sum(duration_ms) as total_duration,
                    sum(shuffle_write_time) as total_sw_time,
                    sum(shuffle_fetch_wait_time) as total_fetch_wait,
                    sum(executor_cpu_time) as total_cpu_time,
                    sum(executor_deserialize_time) as total_deser,
                    sum(result_serialization_time) as total_ser,
                    sum(getting_result_time) as total_get_res,
                    sum(scheduler_delay) as total_delay,
                    sum(disk_bytes_spilled) as total_disk_spill,
                    sum(memory_bytes_spilled) as total_mem_spill,
                    max(peak_execution_memory) as max_peak_mem,
                    sum(peak_execution_memory) as total_peak_mem,
                    count(case when status = 'SUCCESS' then 1 end) as done_tasks,
                    count(case when status = 'FAILED' then 1 end) as failed_tasks
                FROM silver_tasks WHERE app_id = ?
                GROUP BY app_id, stage_id, stage_attempt_id
            ),
            calculated_scores AS (
                SELECT *,
                    CASE WHEN p50 > 0 THEN CAST(max_dur AS DOUBLE) / p50 ELSE 1.0 END as skew_ratio,
                    CASE WHEN total_run > 0 THEN CAST(total_gc AS DOUBLE) / total_run ELSE 0.0 END as gc_ratio,
                    CASE WHEN total_duration > 0 THEN total_duration ELSE 1 END as dur_denom,
                    CASE WHEN total_run > 0 THEN total_run ELSE 1 END as run_denom
                FROM base_metrics
            )
            SELECT
                uuid(), s.app_id, s.stage_id, s.job_id, s.attempt_id, s.name, s.num_tasks,
                cs.done_tasks, cs.failed_tasks, s.submission_time, s.completion_time, s.duration_ms,
                s.input_bytes, 0, 0, s.shuffle_read_bytes, 0, 0, 0, 0,
                cs.total_gc, cs.total_duration, cs.total_deser, cs.total_ser, cs.total_get_res, cs.total_delay,
                cs.max_peak_mem, cs.total_peak_mem, cs.total_mem_spill, cs.total_disk_spill, cs.total_sw_time,
                cs.p50, cs.p75, cs.p95, cs.p99, cs.max_dur,
                s.status, (cs.skew_ratio > 2.0), cs.skew_ratio, cs.gc_ratio,
                replace(replace(replace(CAST(s.parent_ids AS VARCHAR), '[', ''), ']', ''), ' ', ''),
                s.rdd_info,
                (SELECT string_agg(locality || ': ' || cnt, ', ') FROM (
                   SELECT locality, count(*) as cnt 
                   FROM silver_tasks st 
                   WHERE st.app_id = s.app_id AND st.stage_id = s.stage_id AND st.stage_attempt_id = s.attempt_id AND locality IS NOT NULL 
                   GROUP BY locality
                ) loc),
                json_array(
                        json_object('dimension', 'GC Impact', 'score', CAST(greatest(0, 100 - (cs.gc_ratio * 200)) AS INTEGER)),
                        json_object('dimension', 'Shuffle Write Impact', 'score', CAST(greatest(0, 100 - (cs.total_sw_time / 1000000.0 * 100.0 / cs.dur_denom)) AS INTEGER)),
                        json_object('dimension', 'Shuffle Read Blocked', 'score', CAST(greatest(0, 100 - (cs.total_fetch_wait / 1000000.0 * 100.0 / cs.dur_denom)) AS INTEGER)),
                        json_object('dimension', 'I/O Wait', 'score', CAST(LEAST(100, (cs.total_cpu_time / 1000000.0 * 100.0 / cs.run_denom)) AS INTEGER)),
                        json_object('dimension', 'Serialization Impact', 'score', CAST(greatest(0, 100 - ((cs.total_ser + cs.total_deser) * 100.0 / cs.dur_denom)) AS INTEGER)),
                        json_object('dimension', 'Result Fetching', 'score', CAST(greatest(0, 100 - (cs.total_get_res * 100.0 / cs.dur_denom)) AS INTEGER)),
                        json_object('dimension', 'Scheduler Delay Impact', 'score', CAST(greatest(0, 100 - (cs.total_delay * 100.0 / cs.dur_denom)) AS INTEGER)),
                        json_object('dimension', 'Data Skew', 'score', CAST(greatest(0, 100 - (cs.skew_ratio - 1) * 10) AS INTEGER)),
                        json_object('dimension', 'Disk Spill', 'score', CASE WHEN cs.total_disk_spill > 0 THEN 0 ELSE 100 END)
                ),
                (
                    greatest(0, 100 - (cs.skew_ratio - 1) * 10) * 0.15 +
                    greatest(0, 100 - (cs.gc_ratio * 200)) * 0.15 +
                    greatest(0, 100 - (cs.total_sw_time / 1000000.0 * 100.0 / cs.dur_denom)) * 0.15 +
                    greatest(0, 100 - (cs.total_fetch_wait / 1000000.0 * 100.0 / cs.dur_denom)) * 0.15 +
                    (CASE WHEN cs.total_disk_spill > 0 THEN 0 ELSE 100 END) * 0.15 +
                    LEAST(100, (cs.total_cpu_time / 1000000.0 * 100.0 / cs.run_denom)) * 0.10 +
                    greatest(0, 100 - (cs.total_delay * 100.0 / cs.dur_denom)) * 0.05 +
                    greatest(0, 100 - ((cs.total_ser + cs.total_deser) * 100.0 / cs.dur_denom)) * 0.05 +
                    greatest(0, 100 - (cs.total_get_res * 100.0 / cs.dur_denom)) * 0.05
                ),
                greatest(0, 100 - (cs.skew_ratio - 1) * 10),
                greatest(0, 100 - (cs.gc_ratio * 200)),
                100.0,
                greatest(0, 100 - (cs.total_sw_time / 1000000.0 * 100.0 / cs.dur_denom)),
                greatest(0, 100 - (cs.total_fetch_wait / 1000000.0 * 100.0 / cs.dur_denom)),
                LEAST(100, (cs.total_cpu_time / 1000000.0 * 100.0 / cs.run_denom)),
                greatest(0, 100 - ((cs.total_ser + cs.total_deser) * 100.0 / cs.dur_denom)),
                greatest(0, 100 - (cs.total_get_res * 100.0 / cs.dur_denom)),
                greatest(0, 100 - (cs.total_delay * 100.0 / cs.dur_denom)),
                CASE WHEN cs.total_disk_spill > 0 THEN 0 ELSE 100 END
            FROM silver_stages s
            JOIN calculated_scores cs ON s.app_id = cs.app_id AND s.stage_id = cs.stage_id AND s.attempt_id = cs.stage_attempt_id
            WHERE s.app_id = ?
            """;
        jdbcTemplate.update(sql, appId, appId);

        jdbcTemplate.update("""
            INSERT INTO gold_tasks
            SELECT uuid(), app_id, stage_id, stage_attempt_id, task_id, index, executor_id, host, 
                   epoch(launch_time) * 1000, epoch(finish_time) * 1000, duration_ms, 
                   gc_time, scheduler_delay, getting_result_time, executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, peak_execution_memory,
                   input_bytes, 0, 0, 0, memory_bytes_spilled, disk_bytes_spilled, 
                   shuffle_read_bytes, 0, shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, 0, 0, 
                   speculative, status, locality
            FROM silver_tasks
            WHERE app_id = ?
            """, appId);
    }


    private void aggregateJobs(String appId) {
        String sql = """
            INSERT INTO gold_jobs
            WITH task_agg AS (
                SELECT s.job_id, 
                       count(*) as num_tasks,
                       count(case when t.status = 'SUCCESS' then 1 end) as num_completed,
                       count(case when t.status = 'FAILED' then 1 end) as num_failed
                FROM silver_tasks t
                JOIN silver_stages s ON t.app_id = s.app_id AND t.stage_id = s.stage_id AND t.stage_attempt_id = s.attempt_id
                WHERE t.app_id = ?
                GROUP BY s.job_id
            ),
            stage_agg AS (
                SELECT job_id,
                       count(*) as num_stages,
                       count(case when status = 'COMPLETED' then 1 end) as num_completed,
                       count(case when status = 'FAILED' then 1 end) as num_failed,
                       count(case when status = 'SKIPPED' then 1 end) as num_skipped
                FROM silver_stages
                WHERE app_id = ?
                GROUP BY job_id
            ),
            job_performance AS (
                SELECT j.job_id, avg(s.performance_score) as performance_score
                FROM silver_jobs j
                CROSS JOIN LATERAL (SELECT unnest(CAST(j.stage_ids AS INT[])) as sid) AS j_stages
                LEFT JOIN gold_stages s ON j.app_id = s.app_id AND j_stages.sid = s.stage_id
                WHERE j.app_id = ?
                GROUP BY j.job_id
            )
            SELECT 
                uuid(), j.app_id, j.job_id, j.submission_time, j.completion_time, j.duration_ms, j.status,
                COALESCE(sa.num_stages, 0),
                COALESCE(ta.num_tasks, 0),
                replace(replace(replace(CAST(j.stage_ids AS VARCHAR), '[', ''), ']', ''), ' ', ''),
                j.description, NULL,
                COALESCE(sa.num_completed, 0),
                COALESCE(sa.num_failed, 0),
                COALESCE(sa.num_skipped, 0),
                COALESCE(ta.num_completed, 0),
                COALESCE(ta.num_failed, 0),
                0, 0,
                j.sql_execution_id,
                COALESCE(jp.performance_score, 0.0)
            FROM silver_jobs j
            LEFT JOIN task_agg ta ON j.job_id = ta.job_id
            LEFT JOIN stage_agg sa ON j.job_id = sa.job_id
            LEFT JOIN job_performance jp ON j.job_id = jp.job_id
            WHERE j.app_id = ?
            """;
        jdbcTemplate.update(sql, appId, appId, appId, appId);
    }

    private void aggregateSql(String appId) {
        log.debug("Aggregating Gold SQL Metrics for app: {}", appId);
        String sql = """
            INSERT INTO gold_sql_executions
            SELECT 
                uuid(), s.app_id, s.execution_id, s.description, s.details, s.physical_plan, s.plan_info, 
                s.start_time, s.end_time, s.duration_ms, s.status,
                avg(j.performance_score) as performance_score
            FROM silver_sql_executions s
            LEFT JOIN gold_jobs j ON s.app_id = j.app_id AND s.execution_id = j.sql_execution_id
            WHERE s.app_id = ?
            GROUP BY s.app_id, s.execution_id, s.description, s.details, s.physical_plan, s.plan_info, s.start_time, s.end_time, s.duration_ms, s.status
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void aggregateExecutors(String appId) {
        String sql = """
            INSERT INTO gold_executors
            SELECT uuid(), app_id, executor_id, silver_executors.host, add_time, remove_time, total_cores, 0, TRUE,
                   0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL, 0,
                   0, count(case when status = 'FAILED' then 1 end), count(case when status = 'SUCCESS' then 1 end), count(*),
                   sum(duration_ms), sum(gc_time), sum(input_bytes), sum(shuffle_read_bytes), sum(shuffle_write_bytes),
                   remove_reason,
                   avg(duration_ms),
                   sum(executor_cpu_time / 1000000.0) / nullif(sum(executor_run_time), 0),
                   max(peak_execution_memory)
            FROM silver_executors
            JOIN silver_tasks USING (app_id, executor_id)
            WHERE app_id = ?
            GROUP BY app_id, executor_id, silver_executors.host, add_time, remove_time, total_cores, remove_reason
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void aggregateEnvironment(String appId) {
        jdbcTemplate.update("""
            INSERT INTO gold_environment_configs (id, app_id, param_key, param_value, category)
            SELECT uuid(), app_id, param_key, param_value, category
            FROM silver_environment_configs
            WHERE app_id = ?
            """, appId);
    }

    private void aggregateApp(String appId) {
        String sql = """
            UPDATE gold_applications
            SET 
                duration = (SELECT sum(duration) FROM gold_jobs WHERE app_id = ?),
                performance_score = (SELECT CAST(avg(performance_score) AS INTEGER) FROM gold_jobs WHERE app_id = ?),
                total_tasks = (SELECT count(*) FROM gold_tasks WHERE app_id = ?),
                failed_tasks = (SELECT count(*) FROM gold_tasks WHERE app_id = ? AND status = 'FAILED'),
                total_input_bytes = (SELECT coalesce(sum(input_bytes), 0) FROM gold_stages WHERE app_id = ?),
                total_shuffle_read_bytes = (SELECT coalesce(sum(shuffle_read_bytes), 0) FROM gold_stages WHERE app_id = ?)
            WHERE app_id = ?
            """;
        jdbcTemplate.update(sql, appId, appId, appId, appId, appId, appId, appId);
    }
}
