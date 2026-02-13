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
        log.info("Starting Gold aggregation (with Robust Scoring) for app: {}", appId);

        // 1. Stage Level with sub-scores
        aggregateStages(appId);

        // 2. Job Level with total score
        aggregateJobs(appId);

        // 3. Executor Level
        aggregateExecutors(appId);

        // 4. App Level rollup
        aggregateApp(appId);

        log.info("Finished Gold aggregation for app: {}", appId);
    }

    private void aggregateStages(String appId) {
        log.debug("Aggregating Gold Stage Metrics and Scores for app: {}", appId);
        jdbcTemplate.update("DELETE FROM gold_stage_metrics WHERE app_id = ?", appId);

        String sql = """
            INSERT INTO gold_stage_metrics
            WITH base_metrics AS (
                SELECT
                    app_id,
                    stage_id,
                    stage_attempt_id,
                    quantile(duration_ms, 0.5) as p50,
                    quantile(duration_ms, 0.95) as p95,
                    max(duration_ms) as max_dur,
                    sum(gc_time) as total_gc,
                    sum(executor_run_time) as total_run
                FROM silver_tasks
                WHERE app_id = ?
                GROUP BY app_id, stage_id, stage_attempt_id
            ),
            calculated_scores AS (
                SELECT
                    *,
                    CASE WHEN p50 > 0 THEN max_dur / p50 ELSE 1.0 END as skew_ratio,
                    CASE WHEN total_run > 0 THEN CAST(total_gc AS DOUBLE) / total_run ELSE 0.0 END as gc_ratio
                FROM base_metrics
            )
            SELECT
                app_id,
                stage_id,
                stage_attempt_id,
                p50,
                p95,
                skew_ratio,
                gc_ratio,
                greatest(0, 100 - (skew_ratio - 1) * 20) as score_skew,
                greatest(0, 100 - (gc_ratio * 500)) as score_gc,
                100.0 as score_locality,
                (greatest(0, 100 - (skew_ratio - 1) * 20) * 0.6 + greatest(0, 100 - (gc_ratio * 500)) * 0.4) as performance_score
            FROM calculated_scores
            """;

        jdbcTemplate.update(sql, appId);
    }

    private void aggregateJobs(String appId) {
        log.debug("Aggregating Gold Job Metrics for app: {}", appId);
        jdbcTemplate.update("DELETE FROM gold_job_metrics WHERE app_id = ?", appId);

        String sql = """
            INSERT INTO gold_job_metrics
            SELECT 
                app_id,
                job_id,
                avg(performance_score) as performance_score
            FROM (
                SELECT 
                    j.app_id,
                    j.job_id,
                    s.performance_score
                FROM silver_jobs j
                CROSS JOIN LATERAL (SELECT unnest(CAST(j.stage_ids AS INT[])) as sid) AS j_stages
                JOIN gold_stage_metrics s ON j.app_id = s.app_id AND j_stages.sid = s.stage_id
                WHERE j.app_id = ?
            )
            GROUP BY app_id, job_id
            """;

        jdbcTemplate.update(sql, appId);
    }

    private void aggregateExecutors(String appId) {
        log.debug("Aggregating Gold Executor Metrics for app: {}", appId);
        jdbcTemplate.update("DELETE FROM gold_executor_metrics WHERE app_id = ?", appId);

        String sql = """
            INSERT INTO gold_executor_metrics
            SELECT
                app_id,
                executor_id,
                avg(duration_ms) as avg_task_duration,
                sum(executor_cpu_time / 1000000.0) / nullif(sum(executor_run_time), 0) as cpu_utilization
            FROM silver_tasks
            WHERE app_id = ?
            GROUP BY app_id, executor_id
            """;

        jdbcTemplate.update(sql, appId);
    }

    private void aggregateApp(String appId) {
        log.debug("Aggregating Gold App Metrics for app: {}", appId);
        jdbcTemplate.update("DELETE FROM gold_app_metrics WHERE app_id = ?", appId);

        String sql = """
            INSERT INTO gold_app_metrics
            SELECT
                j.app_id,
                sum(j.duration_ms) as total_duration,
                (SELECT coalesce(sum(input_bytes), 0) FROM silver_stages WHERE app_id = j.app_id) as total_input,
                (SELECT coalesce(sum(shuffle_read_bytes), 0) FROM silver_stages WHERE app_id = j.app_id) as total_shuffle,
                avg(gm.performance_score) as performance_score,
                (SELECT count(*) FROM silver_tasks WHERE app_id = j.app_id) as total_tasks,
                (SELECT count(*) FROM silver_tasks WHERE app_id = j.app_id AND status = 'FAILED') as failed_tasks
            FROM silver_jobs j
            JOIN gold_job_metrics gm ON j.app_id = gm.app_id AND j.job_id = gm.job_id
            WHERE j.app_id = ?
            GROUP BY j.app_id
            """;

        jdbcTemplate.update(sql, appId);
    }
}
