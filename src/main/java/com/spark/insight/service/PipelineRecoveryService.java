package com.spark.insight.service;

import com.spark.insight.model.GoldApplicationModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineRecoveryService {

    private final ApplicationService applicationService;
    private final ApplicationLogService logService;
    private final JdbcTemplate jdbcTemplate;

    private static final List<String> ACTIVE_STATUSES = Arrays.asList(
            "INGESTING_BRONZE", "TRANSFORMING_SILVER", "AGGREGATING_GOLD", "LOADING", "RUNNING", "QUEUED", "PENDING_TO_LOADING"
    );

    @PostConstruct
    public void recoverInterruptedPipelines() {
        log.info("Starting Pipeline Recovery Service...");

        // 1. 扫描所有处于“中间态”的应用记录
        List<GoldApplicationModel> interruptedApps = applicationService.lambdaQuery()
                .in(GoldApplicationModel::getParsingStatus, ACTIVE_STATUSES)
                .list();

        if (interruptedApps.isEmpty()) {
            log.info("No interrupted pipelines found in gold_applications.");
        } else {
            log.warn("Found {} interrupted pipelines. Marking as FAILED with recovery hint.", interruptedApps.size());
            for (GoldApplicationModel app : interruptedApps) {
                String appId = app.getAppId();
                String lastStatus = app.getParsingStatus();
                
                // 构造更详细的错误信息，存入 parsing_progress
                String recoveryHint = "Process interrupted during [" + lastStatus + "]. Please re-import (You can resume 'From Bronze' if Bronze was successful).";
                
                applicationService.updateStatusAtomic(appId, "FAILED", 0.0, recoveryHint);
                
                // 在应用日志中记录详细原因
                logService.logEvent(appId, "FAILED", "Pipeline Interrupted", 
                        String.format("System restarted while application was in %s state. Pipeline state invalidated.", lastStatus));
            }
        }

        // 2. 清理任务队列表 (sys_parsing_queue)，防止重启后队列逻辑死锁
        // 使用 DELETE + INSERT 模式以规避某些 DuckDB 环境下的 UPDATE PK 约束错误
        try {
            List<Map<String, Object>> staleTasks = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_parsing_queue WHERE status IN ('QUEUED', 'RUNNING')");
            
            for (Map<String, Object> task : staleTasks) {
                String taskId = task.get("id").toString();
                jdbcTemplate.update("DELETE FROM sys_parsing_queue WHERE id = ?", taskId);
                
                jdbcTemplate.update("""
                    INSERT INTO sys_parsing_queue (id, app_id, type, status, submit_time, start_time, end_time) 
                    VALUES (?, ?, ?, 'FAILED', ?, ?, CURRENT_TIMESTAMP)
                    """,
                    taskId, 
                    task.get("app_id"), 
                    task.get("type"), 
                    task.get("submit_time"), 
                    task.get("start_time")
                );
            }
            
            if (!staleTasks.isEmpty()) {
                log.info("Cleaned up {} stale tasks from sys_parsing_queue using recovery re-insertion.", staleTasks.size());
            }
        } catch (Exception e) {
            log.error("Failed to clean up sys_parsing_queue table during recovery", e);
        }
    }
}
