package com.fluffyeti.spark.performance.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineProgressService {

    private final JdbcTemplate jdbcTemplate;

    public void markCompleted(String appId, String phase, String taskType, String taskId) {
        String sql = """
            INSERT INTO sys_pipeline_progress (app_id, phase, task_type, task_id, status, updated_at)
            VALUES (?, ?, ?, ?, 'COMPLETED', now())
            ON CONFLICT (app_id, phase, task_type, task_id) 
            DO UPDATE SET status = 'COMPLETED', updated_at = now()
            """;
        jdbcTemplate.update(sql, appId, phase, taskType, taskId);
    }

    public boolean isCompleted(String appId, String phase, String taskType, String taskId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_pipeline_progress WHERE app_id = ? AND phase = ? AND task_type = ? AND task_id = ? AND status = 'COMPLETED'",
            Integer.class, appId, phase, taskType, taskId);
        return count != null && count > 0;
    }

    public Set<String> getCompletedTaskIds(String appId, String phase, String taskType) {
        List<String> ids = jdbcTemplate.queryForList(
            "SELECT task_id FROM sys_pipeline_progress WHERE app_id = ? AND phase = ? AND task_type = ? AND status = 'COMPLETED'",
            String.class, appId, phase, taskType);
        return new HashSet<>(ids);
    }

    public void clearProgress(String appId, String phase) {
        log.info("Clearing pipeline progress for app: {}, phase: {}", appId, phase);
        jdbcTemplate.update("DELETE FROM sys_pipeline_progress WHERE app_id = ? AND phase = ?", appId, phase);
    }

    public void clearAllProgress(String appId) {
        log.info("Clearing all pipeline progress for app: {}", appId);
        jdbcTemplate.update("DELETE FROM sys_pipeline_progress WHERE app_id = ?", appId);
    }
}
