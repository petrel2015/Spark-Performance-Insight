package com.spark.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingQueueService {

    private final JdbcTemplate jdbcTemplate;
    private final StatusBroadcaster broadcaster;
    
    @Lazy // Break circular dependency if any
    private final EventLogWatcherService eventLogWatcherService;

    @Transactional
    public void submit(String appId, String type) {
        log.info("Submitting app {} to parsing queue (Type: {})", appId, type);
        
        // Remove any existing QUEUED jobs for this app to avoid duplicates
        jdbcTemplate.update("DELETE FROM parsing_queue WHERE app_id = ? AND status = 'QUEUED'", appId);
        
        jdbcTemplate.update("INSERT INTO parsing_queue (id, app_id, type, status) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), appId, type, "QUEUED");
        
        broadcaster.broadcastStatus(appId, "QUEUED", 0.0, "Waiting in queue...");
        
        processQueue();
    }

    @Transactional
    public void cancel(String appId) {
        log.info("Cancelling app {} from parsing queue", appId);
        int rows = jdbcTemplate.update("DELETE FROM parsing_queue WHERE app_id = ? AND status = 'QUEUED'", appId);
        if (rows > 0) {
            broadcaster.broadcastStatus(appId, "CANCELLED", 0.0, "Cancelled by user");
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledProcess() {
        processQueue();
    }

    public synchronized void processQueue() {
        // Check if anything is running
        Integer runningCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM parsing_queue WHERE status = 'RUNNING'", Integer.class);
        
        if (runningCount != null && runningCount > 0) {
            return; // Busy
        }

        // Pick next
        List<Map<String, Object>> nextJobs = jdbcTemplate.queryForList(
                "SELECT id, app_id, type FROM parsing_queue WHERE status = 'QUEUED' ORDER BY submit_time ASC LIMIT 1");

        if (nextJobs.isEmpty()) {
            return;
        }

        Map<String, Object> job = nextJobs.get(0);
        String id = job.get("id").toString();
        String appId = job.get("app_id").toString();
        String type = job.get("type").toString();

        log.info("Picking app {} from queue to process (Job ID: {})", appId, id);

        // Mark Running (Workaround: DELETE + INSERT)
        // jdbcTemplate.update("UPDATE parsing_queue SET status = 'RUNNING', start_time = CURRENT_TIMESTAMP WHERE id = ?", id);
        Map<String, Object> currentJob = jdbcTemplate.queryForMap("SELECT * FROM parsing_queue WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM parsing_queue WHERE id = ?", id);
        jdbcTemplate.update("INSERT INTO parsing_queue (id, app_id, type, status, submit_time, start_time) VALUES (?, ?, ?, 'RUNNING', ?, CURRENT_TIMESTAMP)",
                id, appId, type, currentJob.get("submit_time"));
        
        // Execute (Async)
        eventLogWatcherService.executePipeline(appId, type, (success) -> {
            markFinished(id, success);
            // Trigger next
            processQueue();
        });
    }

    public Map<String, String> getQueueStatuses(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(appIds.size(), "?"));
        String sql = String.format("SELECT app_id, status FROM parsing_queue WHERE status = 'QUEUED' AND app_id IN (%s)", placeholders);
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, appIds.toArray());
        
        return results.stream().collect(java.util.stream.Collectors.toMap(
                row -> (String) row.get("app_id"),
                row -> (String) row.get("status"),
                (v1, v2) -> v1 // Keep first if duplicate
        ));
    }

    private void markFinished(String id, boolean success) {
        log.info("Job {} finished (Success: {})", id, success);
        // Workaround: DELETE + INSERT
        try {
            Map<String, Object> job = jdbcTemplate.queryForMap("SELECT * FROM parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("INSERT INTO parsing_queue (id, app_id, type, status, submit_time, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    id, job.get("app_id"), job.get("type"), success ? "COMPLETED" : "FAILED", job.get("submit_time"), job.get("start_time"));
        } catch (Exception e) {
            log.error("Failed to mark job finished: " + id, e);
        }
    }
}
