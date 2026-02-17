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
    private final com.spark.insight.config.InsightProperties properties;

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
        EVENT_TABLE_MAP.put("SparkListenerBlockUpdated", "bronze_event_block_updated");
        EVENT_TABLE_MAP.put("SparkListenerUnpersistRDD", "bronze_event_unpersist_rdd");
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
            long fileStartBytes = processedBytes;
            long fileLength = file.length();
            
            ingestFileChunked(appId, file, (p, m) -> {
                // Map file-internal progress to overall Bronze progress
                double overallProgress = calculateProgress(fileStartBytes + (long)(p / 100.0 * fileLength), totalBytes);
                progressReporter.accept(overallProgress, m);
            });
            
            processedBytes += fileLength;
        }
        
        log.info("Finished Bronze ingestion for app: {}", appId);
    }

    private void ingestFileChunked(String appId, File file, BiConsumer<Double, String> fileProgressReporter) {
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        String escapedPath = filePath.replace("'", "''");

        // 1. Count total lines (fast in DuckDB)
        String countSql = String.format("SELECT count(*) FROM read_csv('%s', delim='\u0001', header=false, quote='', escape='', columns={'line': 'VARCHAR'})", escapedPath);
        Long totalLines = jdbcTemplate.queryForObject(countSql, Long.class);
        if (totalLines == null || totalLines == 0) return;

        log.info("Ingesting {} ({} lines) in chunks", fileName, totalLines);

        int chunkSize = properties.getIngestion().getBatchSize();
        jdbcTemplate.execute("CREATE TEMPORARY TABLE IF NOT EXISTS temp_raw_lines (line VARCHAR)");

        for (long offset = 0; offset < totalLines; offset += chunkSize) {
            long currentLimit = Math.min(chunkSize, totalLines - offset);
            
            // 2. Load chunk into temporary table
            jdbcTemplate.execute("DELETE FROM temp_raw_lines");
            String loadSql = String.format(
                "INSERT INTO temp_raw_lines SELECT * FROM read_csv('%s', delim='\u0001', header=false, quote='', escape='', columns={'line': 'VARCHAR'}) LIMIT %d OFFSET %d", 
                escapedPath, currentLimit, offset
            );
            jdbcTemplate.execute(loadSql);

            // 3. Distribute to target tables
            distributeFromTempTable(appId, fileName);

            // 4. Report progress
            double progress = (double) (offset + currentLimit) / totalLines * 100.0;
            String msg = String.format("Bronze: Processing %s (%.1f%%)", fileName, progress);
            log.info("appID:{}, msg:{}", appId, msg);
            fileProgressReporter.accept(progress, msg);
        }
        
        jdbcTemplate.execute("DROP TABLE temp_raw_lines");
    }

    private void distributeFromTempTable(String appId, String fileName) {
        // 1. Create a second temp table to hold ONLY valid lines and their event names
        // This physically separates valid data from malformed data to prevent evaluation errors
        jdbcTemplate.execute("CREATE TEMPORARY TABLE IF NOT EXISTS temp_valid_events (line VARCHAR, event_type VARCHAR)");
        jdbcTemplate.execute("DELETE FROM temp_valid_events");

        // 2. Safely extract event names using CASE to avoid calling json_extract_string on invalid rows
        String extractSql = """
            INSERT INTO temp_valid_events
            SELECT line, event_type
            FROM (
                SELECT line, 
                       CASE WHEN json_valid(line) THEN json_extract_string(line, '$.Event') ELSE NULL END as event_type
                FROM temp_raw_lines
            ) t
            WHERE event_type IS NOT NULL
            """;
        jdbcTemplate.update(extractSql);

        // 3. Distribute to target tables from the safe temp table
        for (Map.Entry<String, String> entry : EVENT_TABLE_MAP.entrySet()) {
            String eventName = entry.getKey();
            String tableName = entry.getValue();
            
            String insertSql = """
                INSERT INTO %s (app_id, file_name, raw_json)
                SELECT ?, ?, line
                FROM temp_valid_events
                WHERE event_type = ?
                """.formatted(tableName);
            
            jdbcTemplate.update(insertSql, appId, fileName, eventName);
        }

        // 4. Handle unknown events
        StringBuilder knownEventsPart = new StringBuilder();
        for (String event : EVENT_TABLE_MAP.keySet()) {
            if (knownEventsPart.length() > 0) knownEventsPart.append(", ");
            knownEventsPart.append("'").append(event).append("'");
        }

        String unknownSql = """
            INSERT INTO bronze_event_unknown (app_id, file_name, event_name, raw_json)
            SELECT ?, ?, event_type, line
            FROM temp_valid_events
            WHERE event_type NOT IN (%s)
            """.formatted(knownEventsPart.toString());
        
        jdbcTemplate.update(unknownSql, appId, fileName);
        
        // Cleanup this chunk's valid lines
        jdbcTemplate.execute("DROP TABLE temp_valid_events");
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
