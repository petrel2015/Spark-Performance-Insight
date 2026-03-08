package com.fluffyeti.spark.performance.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingQueueService {

    private final JdbcTemplate jdbcTemplate;
    private final StatusBroadcaster broadcaster;
    private final ApplicationService applicationService;
    
    @Lazy // Break circular dependency if any
    private final EventLogWatcherService eventLogWatcherService;

    public void submit(String appId, String type) {
        log.info("Submitting app {} to parsing queue (Type: {})", appId, type);
        
        // Remove any existing QUEUED jobs for this app to avoid duplicates
        jdbcTemplate.update("DELETE FROM sys_parsing_queue WHERE app_id = ? AND status = 'QUEUED'", appId);
        
        // Clear parsing_start_time to avoid stale timing in UI
        jdbcTemplate.update("UPDATE gold_applications SET parsing_start_time = NULL WHERE app_id = ?", appId);
        
        // Update gold_applications status to QUEUED
        applicationService.updateStatusAtomic(appId, "QUEUED", 0.0, "Waiting in queue...", null);

        long now = System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO sys_parsing_queue (id, app_id, type, status, submit_time) VALUES (?, ?, ?, 'QUEUED', ?)",
                UUID.randomUUID(), appId, type, now);
        
        broadcaster.broadcastStatus(appId, "QUEUED", 0.0, "Waiting in queue...", getAppName(appId), null);
        
        // Use virtual thread to trigger processQueue outside of current execution flow
        Thread.ofVirtual().start(this::processQueue);
    }

    @Transactional
    public void cancel(String appId) {
        log.info("Cancelling app {} from parsing queue", appId);
        int rows = jdbcTemplate.update("DELETE FROM sys_parsing_queue WHERE app_id = ? AND status = 'QUEUED'", appId);
        if (rows > 0) {
            // Revert status to something that allows re-triggering, like READY/PENDING_LOAD or previous status if we had it.
            // For now, let's use a special CANCELLED status that UI can handle, or just PENDING_LOAD.
            applicationService.updateStatusAtomic(appId, "PENDING_LOAD", 0.0, "Cancelled by user");
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

    private Long getStartTime(String appId) {
        try {
            return jdbcTemplate.queryForObject("SELECT parsing_start_time FROM gold_applications WHERE app_id = ?", Long.class, appId);
        } catch (Exception e) {
            return null;
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledProcess() {
        processQueue();
    }

    public synchronized void processQueue() {
        // Check if anything is running
        List<String> runningApps = jdbcTemplate.queryForList(
                "SELECT COALESCE(ga.app_name, 'Unparsed') || ' [' || q.app_id || ']' " +
                "FROM sys_parsing_queue q " +
                "LEFT JOIN gold_applications ga ON q.app_id = ga.app_id " +
                "WHERE q.status = 'RUNNING'", String.class);
        
        if (!runningApps.isEmpty()) {
            return; // Busy
        }

        // Pick next
        List<Map<String, Object>> nextJobs = jdbcTemplate.queryForList(
                "SELECT id, app_id, type FROM sys_parsing_queue WHERE status = 'QUEUED' ORDER BY submit_time ASC, id ASC LIMIT 1");

        if (nextJobs.isEmpty()) {
            return;
        }

        Map<String, Object> job = nextJobs.get(0);
        String id = job.get("id").toString();
        String appId = job.get("app_id").toString();
        String type = job.get("type").toString();

        log.info("Picking app {} from queue to process (Job ID: {})", appId, id);

        try {
            Map<String, Object> currentJob = jdbcTemplate.queryForMap("SELECT * FROM sys_parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM sys_parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("INSERT INTO sys_parsing_queue (id, app_id, type, status, submit_time, start_time) VALUES (?, ?, ?, 'RUNNING', ?, ?)",
                    id, appId, type, currentJob.get("submit_time"), System.currentTimeMillis());
            
            log.info("Job {} marked as RUNNING, starting pipeline executor...", id);
            
            eventLogWatcherService.executePipeline(appId, type, (success) -> {
                log.info("Pipeline execution finished for Job {}, success: {}. Marking as COMPLETED/FAILED.", id, success);
                markFinished(id, success);
                processQueue();
            });
        } catch (Exception e) {
            log.error("Failed to start job from queue: " + id, e);
            try {
                Map<String, Object> failedJob = jdbcTemplate.queryForMap("SELECT * FROM sys_parsing_queue WHERE id = ?", id);
                jdbcTemplate.update("DELETE FROM sys_parsing_queue WHERE id = ?", id);
                jdbcTemplate.update("INSERT INTO sys_parsing_queue (id, app_id, type, status, submit_time, start_time, end_time) VALUES (?, ?, ?, 'FAILED', ?, ?, ?)",
                        id, appId, type, failedJob.get("submit_time"), failedJob.get("start_time"), System.currentTimeMillis());
            } catch (Exception ignore) {}
        }
    }

    public Map<String, String> getQueueStatuses(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(appIds.size(), "?"));
        String sql = String.format("SELECT app_id, status FROM sys_parsing_queue WHERE status = 'QUEUED' AND app_id IN (%s)", placeholders);
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, appIds.toArray());
        return results.stream().collect(java.util.stream.Collectors.toMap(row -> (String) row.get("app_id"), row -> (String) row.get("status"), (v1, v2) -> v1));
    }

    private void markFinished(String id, boolean success) {
        log.info("Job {} finished (Success: {})", id, success);
        try {
            Map<String, Object> job = jdbcTemplate.queryForMap("SELECT * FROM sys_parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM sys_parsing_queue WHERE id = ?", id);
            jdbcTemplate.update("INSERT INTO sys_parsing_queue (id, app_id, type, status, submit_time, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, job.get("app_id"), job.get("type"), success ? "COMPLETED" : "FAILED", job.get("submit_time"), job.get("start_time"), System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Failed to mark job finished: " + id, e);
        }
    }
}
