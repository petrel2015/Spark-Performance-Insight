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

        rebuildGoldTables();

        aggregateStages(appId);
        aggregateJobs(appId);
        aggregateExecutors(appId);
        aggregateSql(appId); // Added SQL aggregation
        aggregateApp(appId);

        syncToLegacyTables(appId);

        log.info("Finished Gold aggregation and Sync for app: {}", appId);
    }

    private void rebuildGoldTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS gold_app_metrics");
        jdbcTemplate.execute("""
            CREATE TABLE gold_app_metrics (
                app_id VARCHAR, total_duration_ms BIGINT, total_input_bytes BIGINT, 
                total_shuffle_read_bytes BIGINT, performance_score DOUBLE, 
                total_tasks INT, failed_tasks INT
            )""");

        jdbcTemplate.execute("DROP TABLE IF EXISTS gold_job_metrics");
        jdbcTemplate.execute("""
            CREATE TABLE gold_job_metrics (
                app_id VARCHAR, job_id INT, performance_score DOUBLE
            )""");

        jdbcTemplate.execute("DROP TABLE IF EXISTS gold_stage_metrics");
        jdbcTemplate.execute("""
            CREATE TABLE gold_stage_metrics (
                app_id VARCHAR, stage_id INT, attempt_id INT, duration_p50 BIGINT, 
                duration_p95 BIGINT, skew_ratio DOUBLE, gc_time_ratio DOUBLE, 
                score_skew DOUBLE, score_gc DOUBLE, score_locality DOUBLE, performance_score DOUBLE
            )""");

        jdbcTemplate.execute("DROP TABLE IF EXISTS gold_executor_metrics");
        jdbcTemplate.execute("""
            CREATE TABLE gold_executor_metrics (
                app_id VARCHAR, executor_id VARCHAR, avg_task_duration_ms DOUBLE, 
                cpu_utilization_ratio DOUBLE, total_tasks_handled INT, total_input_bytes BIGINT,
                total_shuffle_read_bytes BIGINT
            )""");
            
        jdbcTemplate.execute("DROP TABLE IF EXISTS gold_sql_metrics");
        jdbcTemplate.execute("""
            CREATE TABLE gold_sql_metrics (
                app_id VARCHAR, execution_id BIGINT, performance_score DOUBLE
            )""");
    }

    private void aggregateStages(String appId) {
        String sql = """
            INSERT INTO gold_stage_metrics
            WITH base_metrics AS (
                SELECT
                    app_id, stage_id, stage_attempt_id,
                    quantile(duration_ms, 0.5) as p50,
                    quantile(duration_ms, 0.95) as p95,
                    max(duration_ms) as max_dur,
                    sum(gc_time) as total_gc,
                    sum(executor_run_time) as total_run
                FROM silver_tasks WHERE app_id = ?
                GROUP BY app_id, stage_id, stage_attempt_id
            ),
            calculated_scores AS (
                SELECT *,
                    CASE WHEN p50 > 0 THEN CAST(max_dur AS DOUBLE) / p50 ELSE 1.0 END as skew_ratio,
                    CASE WHEN total_run > 0 THEN CAST(total_gc AS DOUBLE) / total_run ELSE 0.0 END as gc_ratio
                FROM base_metrics
            )
            SELECT
                app_id, stage_id, stage_attempt_id, p50, p95, skew_ratio, gc_ratio,
                -- Skew Score: Allow up to 10x skew before hitting 0. (10-1)*10 = 90. 100-90=10.
                -- Previously (skew-1)*20 -> 6x skew = 0.
                greatest(0, 100 - (skew_ratio - 1) * 10) as score_skew,
                -- GC Score: Allow up to 50% GC before hitting 0. (0.5 * 200) = 100.
                -- Previously gc * 500 -> 20% GC = 0.
                greatest(0, 100 - (gc_ratio * 200)) as score_gc,
                100.0 as score_locality,
                (greatest(0, 100 - (skew_ratio - 1) * 10) * 0.6 + greatest(0, 100 - (gc_ratio * 200)) * 0.4) as performance_score
            FROM calculated_scores
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void aggregateJobs(String appId) {
        String sql = """
            INSERT INTO gold_job_metrics
            SELECT app_id, job_id, avg(performance_score) as performance_score
            FROM (
                SELECT j.app_id, j.job_id, s.performance_score
                FROM silver_jobs j
                CROSS JOIN LATERAL (SELECT unnest(CAST(j.stage_ids AS INT[])) as sid) AS j_stages
                JOIN gold_stage_metrics s ON j.app_id = s.app_id AND j_stages.sid = s.stage_id
                WHERE j.app_id = ?
            ) GROUP BY app_id, job_id
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void aggregateSql(String appId) {
        log.debug("Aggregating Gold SQL Metrics for app: {}", appId);
        String sql = """
            INSERT INTO gold_sql_metrics
            SELECT 
                app_id, 
                sql_execution_id, 
                avg(performance_score) as performance_score
            FROM silver_jobs
            JOIN gold_job_metrics USING (app_id, job_id)
            WHERE app_id = ? AND sql_execution_id IS NOT NULL
            GROUP BY app_id, sql_execution_id
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void aggregateExecutors(String appId) {
        String sql = """
            INSERT INTO gold_executor_metrics
            SELECT app_id, executor_id, avg(duration_ms) as avg_task_duration,
                   sum(executor_cpu_time / 1000000.0) / nullif(sum(executor_run_time), 0) as cpu_utilization,
                   count(*), sum(input_bytes), sum(shuffle_read_bytes)
            FROM silver_tasks WHERE app_id = ?
            GROUP BY app_id, executor_id
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void aggregateApp(String appId) {
        String sql = """
            INSERT INTO gold_app_metrics
            SELECT j.app_id, sum(j.duration_ms), 
                   (SELECT coalesce(sum(input_bytes), 0) FROM silver_stages WHERE app_id = j.app_id),
                   (SELECT coalesce(sum(shuffle_read_bytes), 0) FROM silver_stages WHERE app_id = j.app_id),
                   avg(gm.performance_score),
                   (SELECT count(*) FROM silver_tasks WHERE app_id = j.app_id),
                   (SELECT count(*) FROM silver_tasks WHERE app_id = j.app_id AND status = 'FAILED')
            FROM silver_jobs j
            JOIN gold_job_metrics gm ON j.app_id = gm.app_id AND j.job_id = gm.job_id
            WHERE j.app_id = ? GROUP BY j.app_id
            """;
        jdbcTemplate.update(sql, appId);
    }

    private void syncToLegacyTables(String appId) {
        log.info("Syncing Medallion data to legacy tables for app: {}", appId);

        jdbcTemplate.update("DELETE FROM jobs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM stages WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM tasks WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM executors WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM sql_executions WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM environment_configs WHERE app_id = ?", appId);

        // 1. Applications
        jdbcTemplate.update("""
            UPDATE applications
            SET duration = g.total_duration_ms,
                performance_score = CAST(g.performance_score AS INTEGER)
            FROM gold_app_metrics g
            WHERE applications.app_id = g.app_id AND g.app_id = ?
            """, appId);

        // 2. Jobs - NOW INCLUDING sql_execution_id
        jdbcTemplate.update("""
            INSERT INTO jobs (id, app_id, job_id, submission_time, completion_time, duration, status, stage_ids, description, performance_score, sql_execution_id)
            SELECT uuid(), app_id, job_id, submission_time, completion_time, duration_ms, status, 
                   replace(replace(replace(CAST(stage_ids AS VARCHAR), '[', ''), ']', ''), ' ', '') as ids,
                   description, performance_score, sql_execution_id
            FROM silver_jobs s
            JOIN gold_job_metrics g USING (app_id, job_id)
            WHERE app_id = ?
            """, appId);

        // 3. Stages
        jdbcTemplate.update("""
            INSERT INTO stages (id, app_id, stage_id, attempt_id, job_id, stage_name, num_tasks, submission_time, completion_time, duration, status, 
                               input_bytes, shuffle_read_bytes, duration_p50, duration_p95, performance_score, parent_stage_ids, rdd_info)
            SELECT DISTINCT ON (app_id, stage_id)
                   uuid(), app_id, stage_id, attempt_id, job_id, name, num_tasks, submission_time, completion_time, duration_ms, status,
                   input_bytes, shuffle_read_bytes, duration_p50, duration_p95, performance_score, 
                   replace(replace(replace(CAST(parent_ids AS VARCHAR), '[', ''), ']', ''), ' ', ''), rdd_info
            FROM silver_stages s
            JOIN gold_stage_metrics g USING (app_id, stage_id, attempt_id)
            WHERE app_id = ?
            ORDER BY app_id, stage_id, attempt_id DESC
            """, appId);

        // 4. Tasks
        jdbcTemplate.update("""
            INSERT INTO tasks (id, app_id, stage_id, attempt_id, task_id, task_index, executor_id, host, launch_time, finish_time, duration, status,
                               gc_time, scheduler_delay, getting_result_time, executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, peak_execution_memory,
                               input_bytes, output_bytes, shuffle_read_bytes, shuffle_write_bytes, memory_bytes_spilled, disk_bytes_spilled, speculative, locality)
            SELECT uuid(), app_id, stage_id, stage_attempt_id, task_id, index, executor_id, host, 
                   epoch(launch_time) * 1000, epoch(finish_time) * 1000, duration_ms, status,
                   gc_time, scheduler_delay, getting_result_time, executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, peak_execution_memory,
                   input_bytes, output_bytes, shuffle_read_bytes, shuffle_write_bytes, memory_bytes_spilled, disk_bytes_spilled, speculative, locality
            FROM silver_tasks
            WHERE app_id = ?
            """, appId);

        // 5. Executors
        jdbcTemplate.update("""
            INSERT INTO executors (id, app_id, executor_id, host, add_time, remove_time, total_cores, exec_loss_reason, 
                                  completed_tasks, input_bytes, shuffle_read_bytes)
            SELECT uuid(), app_id, executor_id, host, add_time, remove_time, total_cores, remove_reason,
                   total_tasks_handled, total_input_bytes, total_shuffle_read_bytes
            FROM silver_executors
            JOIN gold_executor_metrics USING (app_id, executor_id)
            WHERE app_id = ?
            """, appId);

        // 6. SQL Executions - NOW INCLUDING performance_score
        jdbcTemplate.update("""
            INSERT INTO sql_executions (id, app_id, execution_id, description, details, physical_plan, plan_info, start_time, end_time, duration, status, performance_score)
            SELECT uuid(), app_id, execution_id, description, details, physical_plan, plan_info, start_time, end_time, duration_ms, status, performance_score
            FROM silver_sql_executions s
            LEFT JOIN gold_sql_metrics g USING (app_id, execution_id)
            WHERE app_id = ?
            """, appId);

        // 7. Environment Configs
        jdbcTemplate.update("""
            INSERT INTO environment_configs (id, app_id, param_key, param_value, category)
            SELECT uuid(), app_id, param_key, param_value, category
            FROM silver_environment_configs
            WHERE app_id = ?
            """, appId);
    }
}
