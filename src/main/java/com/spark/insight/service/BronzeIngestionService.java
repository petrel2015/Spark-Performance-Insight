package com.spark.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class BronzeIngestionService {

    private final JdbcTemplate jdbcTemplate;

    private static final Map<String, String> EVENT_TABLE_MAP = new HashMap<>();

    static {
        EVENT_TABLE_MAP.put("SparkListenerApplicationStart", "bronze_event_application_start");
        EVENT_TABLE_MAP.put("SparkListenerApplicationEnd", "bronze_event_application_end");
        EVENT_TABLE_MAP.put("SparkListenerJobStart", "bronze_event_job_start");
        EVENT_TABLE_MAP.put("SparkListenerJobEnd", "bronze_event_job_end");
        EVENT_TABLE_MAP.put("SparkListenerStageSubmitted", "bronze_event_stage_submitted");
        EVENT_TABLE_MAP.put("SparkListenerStageCompleted", "bronze_event_stage_completed");
        EVENT_TABLE_MAP.put("SparkListenerTaskStart", "bronze_event_task_start");
        EVENT_TABLE_MAP.put("SparkListenerTaskEnd", "bronze_event_task_end");
        EVENT_TABLE_MAP.put("SparkListenerExecutorAdded", "bronze_event_executor_added");
        EVENT_TABLE_MAP.put("SparkListenerExecutorRemoved", "bronze_event_executor_removed");
        EVENT_TABLE_MAP.put("SparkListenerBlockManagerAdded", "bronze_event_block_manager_added");
        EVENT_TABLE_MAP.put("org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionStart", "bronze_event_sql_execution_start");
        EVENT_TABLE_MAP.put("org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionEnd", "bronze_event_sql_execution_end");
        EVENT_TABLE_MAP.put("SparkListenerEnvironmentUpdate", "bronze_event_environment_update");
        EVENT_TABLE_MAP.put("SparkListenerLogStart", "bronze_event_log_start");
    }

    @Transactional
    public void ingest(String appId, List<File> files, BiConsumer<Double, String> progressReporter) {
        long totalBytes = files.stream().mapToLong(File::length).sum();
        long processedBytes = 0;
        
        log.info("Starting Bronze ingestion for app: {}, files: {}, total bytes: {}", appId, files.size(), totalBytes);
        progressReporter.accept(0.0, "Bronze: Initializing...");
        
        jdbcTemplate.execute("INSTALL json; LOAD json;");

        log.debug("Cleaning up old bronze data for app: {}", appId);
        for (String tableName : EVENT_TABLE_MAP.values()) {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE app_id = ?", appId);
        }
        jdbcTemplate.update("DELETE FROM bronze_event_unknown WHERE app_id = ?", appId);

        for (File file : files) {
            String fileName = file.getName();
            progressReporter.accept(
                calculateProgress(processedBytes, totalBytes), 
                String.format("Bronze: Reading %s...", fileName)
            );
            
            ingestFileOptimized(appId, file);
            
            processedBytes += file.length();
            progressReporter.accept(
                calculateProgress(processedBytes, totalBytes),
                String.format("Bronze: Processed %s", fileName)
            );
        }
        
        log.info("Finished Bronze ingestion for app: {}", appId);
    }

    // Retaining legacy signature for compatibility if needed, but updated to delegate
    public void ingest(String appId, List<File> files) {
        ingest(appId, files, (p, m) -> {});
    }

    private void ingestFileOptimized(String appId, File file) {
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        
        // 1. Load file into temporary table (IO bound, done once)
        jdbcTemplate.execute("CREATE TEMPORARY TABLE IF NOT EXISTS temp_raw_lines (line VARCHAR)");
        jdbcTemplate.execute("DELETE FROM temp_raw_lines"); // Clear previous file content
        
        String loadSql = String.format("INSERT INTO temp_raw_lines SELECT * FROM read_csv('%s', delim='\u0001', header=false, quote='', escape='', columns={'line': 'VARCHAR'})", filePath.replace("'", "''"));
        jdbcTemplate.execute(loadSql);

        // 2. Distribute to target tables (CPU/Memory bound, fast)
        for (Map.Entry<String, String> entry : EVENT_TABLE_MAP.entrySet()) {
            String eventName = entry.getKey();
            String tableName = entry.getValue();
            
            String insertSql = """
                INSERT INTO %s (app_id, file_name, raw_json)
                SELECT ?, ?, line
                FROM temp_raw_lines
                WHERE json_extract_string(line, '$.Event') = ?
                """.formatted(tableName);
            
            jdbcTemplate.update(insertSql, appId, fileName, eventName);
        }

        // 3. Handle unknown events
        StringBuilder knownEventsPart = new StringBuilder();
        for (String event : EVENT_TABLE_MAP.keySet()) {
            if (knownEventsPart.length() > 0) knownEventsPart.append(", ");
            knownEventsPart.append("'").append(event).append("'");
        }

        String unknownSql = """
            INSERT INTO bronze_event_unknown (app_id, file_name, event_name, raw_json)
            SELECT ?, ?, json_extract_string(line, '$.Event'), line
            FROM temp_raw_lines
            WHERE json_extract_string(line, '$.Event') NOT IN (%s)
            """.formatted(knownEventsPart.toString());
        
        jdbcTemplate.update(unknownSql, appId, fileName);
        
        // 4. Cleanup
        jdbcTemplate.execute("DELETE FROM temp_raw_lines");
    }

    private double calculateProgress(long processed, long total) {
        if (total == 0) return 100.0;
        // Map 0-100% of Bronze phase to 0-30% of Total Pipeline (assumed mapping by caller, 
        // but here we just return 0-100 relative to Bronze task)
        // Wait, caller (EventLogWatcher) maps this.
        // Actually, better to return 0-100 of THIS task.
        return (double) processed / total * 100.0;
    }
}
