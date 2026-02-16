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

    public void submit(String appId, String type) {
        log.info("Submitting app {} to parsing queue (Type: {})", appId, type);
        
        // Remove any existing QUEUED jobs for this app to avoid duplicates
        jdbcTemplate.update("DELETE FROM parsing_queue WHERE app_id = ? AND status = 'QUEUED'", appId);
        
        jdbcTemplate.update("INSERT INTO parsing_queue (id, app_id, type, status) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), appId, type, "QUEUED");
        
        broadcaster.broadcastStatus(appId, "QUEUED", 0.0, "Waiting in queue...", getAppName(appId), getStartTime(appId));
        
        // Use virtual thread to trigger processQueue outside of current execution flow
        Thread.ofVirtual().start(this::processQueue);
    }

    @Transactional
    public void cancel(String appId) {
        log.info("Cancelling app {} from parsing queue", appId);
        int rows = jdbcTemplate.update("DELETE FROM parsing_queue WHERE app_id = ? AND status = 'QUEUED'", appId);
        if (rows > 0) {
            broadcaster.broadcastStatus(appId, "CANCELLED", 0.0, "Cancelled by user", getAppName(appId), getStartTime(appId));
        }
    }

    private String getAppName(String appId) {
        try {
            return jdbcTemplate.queryForObject("SELECT app_name FROM gold_applications WHERE app_id = ?", String.class, appId);
        } catch (Exception e) {
            return null;
        }
    }

    private java.time.LocalDateTime getStartTime(String appId) {
        try {
            return jdbcTemplate.queryForObject("SELECT parsing_start_time FROM gold_applications WHERE app_id = ?", java.time.LocalDateTime.class, appId);
        } catch (Exception e) {
            return null;
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledProcess() {
        processQueue();
    }

    public synchronized void processQueue() {
        // Check if anything is running - Fetch names for better logging
        List<String> runningApps = jdbcTemplate.queryForList(
                "SELECT COALESCE(ga.app_name, q.app_id) FROM parsing_queue q " +
                "LEFT JOIN gold_applications ga ON q.app_id = ga.app_id " +
                "WHERE q.status = 'RUNNING'", String.class);
        
        if (!runningApps.isEmpty()) {
            log.debug("Parsing queue busy: {} jobs running ({}).", runningApps.size(), String.join(", ", runningApps));
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

        try {
            // Mark Running (Workaround: DELETE + INSERT)
            Map<String, Object> currentJob = jdbcTemplate.queryForMap("SELECT * FROM parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("INSERT INTO parsing_queue (id, app_id, type, status, submit_time, start_time) VALUES (?, ?, ?, 'RUNNING', ?, CURRENT_TIMESTAMP)",
                    id, appId, type, currentJob.get("submit_time"));
            
            log.info("Job {} marked as RUNNING, starting pipeline executor...", id);
            
            // Execute (Async)
            eventLogWatcherService.executePipeline(appId, type, (success) -> {
                log.info("Pipeline execution finished for Job {}, success: {}. Marking as COMPLETED/FAILED.", id, success);
                markFinished(id, success);
                // Trigger next
                processQueue();
            });
        } catch (Exception e) {
            log.error("Failed to start job from queue: " + id, e);
            // Revert status to FAILED in queue to avoid blocking
            jdbcTemplate.update("UPDATE parsing_queue SET status = 'FAILED' WHERE id = ?", id);
        }
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
