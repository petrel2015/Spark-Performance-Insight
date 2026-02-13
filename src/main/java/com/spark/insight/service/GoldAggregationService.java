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
        log.info("Starting Gold aggregation and Sync (Full Overlay Mode) for app: {}", appId);

        // 1. Force rebuild gold tables
        rebuildGoldTables();

        // 2. Core Analytics
        aggregateStages(appId);
        aggregateJobs(appId);
        aggregateExecutors(appId);
        aggregateApp(appId);

        // 3. Sync results back to legacy tables (INSERT instead of UPDATE)
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
                cpu_utilization_ratio DOUBLE
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
                    CASE WHEN p50 > 0 THEN max_dur / p50 ELSE 1.0 END as skew_ratio,
                    CASE WHEN total_run > 0 THEN CAST(total_gc AS DOUBLE) / total_run ELSE 0.0 END as gc_ratio
                FROM base_metrics
            )
            SELECT
                app_id, stage_id, stage_attempt_id, p50, p95, skew_ratio, gc_ratio,
                greatest(0, 100 - (skew_ratio - 1) * 20) as score_skew,
                greatest(0, 100 - (gc_ratio * 500)) as score_gc,
                100.0 as score_locality,
                (greatest(0, 100 - (skew_ratio - 1) * 20) * 0.6 + greatest(0, 100 - (gc_ratio * 500)) * 0.4) as performance_score
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

    private void aggregateExecutors(String appId) {
        String sql = """
            INSERT INTO gold_executor_metrics
            SELECT app_id, executor_id, avg(duration_ms) as avg_task_duration,
                   sum(executor_cpu_time / 1000000.0) / nullif(sum(executor_run_time), 0) as cpu_utilization
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

        // Clear legacy data for this app
        jdbcTemplate.update("DELETE FROM jobs WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM stages WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM tasks WHERE app_id = ?", appId);
        jdbcTemplate.update("DELETE FROM executors WHERE app_id = ?", appId);

        // 1. Sync Applications (Already exists, so UPDATE)
        jdbcTemplate.update("""
            UPDATE applications
            SET duration = g.total_duration_ms,
                performance_score = CAST(g.performance_score AS INTEGER)
            FROM gold_app_metrics g
            WHERE applications.app_id = g.app_id AND g.app_id = ?
            """, appId);

        // 2. Sync Jobs (INSERT)
        jdbcTemplate.update("""
            INSERT INTO jobs (id, app_id, job_id, submission_time, completion_time, duration, status, stage_ids, description, performance_score)
            SELECT app_id || ':' || job_id, app_id, job_id, submission_time, completion_time, duration_ms, status, 
                   CAST(stage_ids AS VARCHAR), description, performance_score
            FROM silver_jobs s
            JOIN gold_job_metrics g USING (app_id, job_id)
            WHERE app_id = ?
            """, appId);

        // 3. Sync Stages (INSERT)
        jdbcTemplate.update("""
            INSERT INTO stages (id, app_id, stage_id, attempt_id, stage_name, num_tasks, submission_time, completion_time, duration, status, 
                               input_bytes, shuffle_read_bytes, duration_p50, duration_p95, performance_score)
            SELECT app_id || ':' || stage_id || ':' || attempt_id, app_id, stage_id, attempt_id, name, num_tasks, submission_time, completion_time, duration_ms, status,
                   input_bytes, shuffle_read_bytes, duration_p50, duration_p95, performance_score
            FROM silver_stages s
            JOIN gold_stage_metrics g USING (app_id, stage_id, attempt_id)
            WHERE app_id = ?
            """, appId);

        // 4. Sync Tasks (INSERT)
        jdbcTemplate.update("""
            INSERT INTO tasks (id, app_id, stage_id, attempt_id, task_id, task_index, executor_id, host, launch_time, finish_time, duration, status)
            SELECT app_id || ':' || task_id, app_id, stage_id, stage_attempt_id, task_id, index, executor_id, host, 
                   epoch(launch_time) * 1000, epoch(finish_time) * 1000, duration_ms, status
            FROM silver_tasks
            WHERE app_id = ?
            """, appId);

        // 5. Sync Executors (INSERT)
        jdbcTemplate.update("""
            INSERT INTO executors (id, app_id, executor_id, host, add_time, remove_time, total_cores, exec_loss_reason)
            SELECT app_id || ':' || executor_id, app_id, executor_id, host, add_time, remove_time, total_cores, remove_reason
            FROM silver_executors
            WHERE app_id = ?
            """, appId);
            
        log.debug("Sync to legacy tables completed.");
    }
}
