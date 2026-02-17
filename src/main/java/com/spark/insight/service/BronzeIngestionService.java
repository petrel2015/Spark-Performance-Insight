package com.spark.insight.service;

import com.github.luben.zstd.ZstdInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
        // Step 3: Sort files by name to ensure sequence (important for V2 logs)
        List<File> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(Comparator.comparing(File::getName, this::naturalOrderCompare));

        long totalBytes = sortedFiles.stream().mapToLong(File::length).sum();
        long processedBytes = 0;
        
        log.info("Starting Bronze ingestion for app: {}, files: {}, total bytes: {}", appId, sortedFiles.size(), totalBytes);
        progressReporter.accept(0.0, "Bronze: Initializing...");
        
        jdbcTemplate.execute("INSTALL json; LOAD json;");

        log.debug("Cleaning up old bronze data for app: {}", appId);
        for (String tableName : EVENT_TABLE_MAP.values()) {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE app_id = ?", appId);
        }
        jdbcTemplate.update("DELETE FROM bronze_event_unknown WHERE app_id = ?", appId);

        for (File file : sortedFiles) {
            long fileStartBytes = processedBytes;
            long fileLength = file.length();
            
            ingestFileStreaming(appId, file, (p, m) -> {
                double overallProgress = calculateProgress(fileStartBytes + (long)(p / 100.0 * fileLength), totalBytes);
                progressReporter.accept(overallProgress, m);
            });
            
            processedBytes += fileLength;
        }
        
        log.info("Finished Bronze ingestion for app: {}", appId);
    }

    private void ingestFileStreaming(String appId, File file, BiConsumer<Double, String> fileProgressReporter) {
        String fileName = file.getName();
        boolean isZstd = fileName.endsWith(".zstd");
        
        log.info("Streaming ingestion for {} (Zstd: {})", fileName, isZstd);

        int batchSize = properties.getIngestion().getBatchSize();
        List<String> batch = new ArrayList<>(batchSize);

        try (InputStream fis = new FileInputStream(file);
             InputStream is = isZstd ? new ZstdInputStream(fis) : fis;
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            String line;
            long linesRead = 0;
            long fileLength = file.length();
            
            // Note: Progress calculation for compressed files based on bytes read from raw stream
            // But BufferedReader might buffer a lot. For simplicity, we use line-based progress if possible,
            // or just report periodic updates.
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                batch.add(line);
                linesRead++;

                if (batch.size() >= batchSize) {
                    processBatch(appId, fileName, batch);
                    batch.clear();
                    fileProgressReporter.accept(50.0, String.format("Bronze: Processing %s (%d lines)", fileName, linesRead));
                }
            }

            if (!batch.isEmpty()) {
                processBatch(appId, fileName, batch);
            }
            
            fileProgressReporter.accept(100.0, String.format("Bronze: Finished %s (%d lines)", fileName, linesRead));

        } catch (IOException e) {
            log.error("Failed to read log file: " + file.getAbsolutePath(), e);
            throw new RuntimeException("Ingestion failed for " + fileName, e);
        }
    }

    private void processBatch(String appId, String fileName, List<String> lines) {
        jdbcTemplate.execute("CREATE TEMPORARY TABLE IF NOT EXISTS temp_raw_lines (line VARCHAR)");
        jdbcTemplate.execute("DELETE FROM temp_raw_lines");

        // Batch insert lines into temporary table
        // For performance, we could use DuckDB Appender, but JdbcTemplate batchUpdate is easier for now
        jdbcTemplate.batchUpdate("INSERT INTO temp_raw_lines (line) VALUES (?)", lines, lines.size(), (ps, argument) -> {
            ps.setString(1, argument);
        });

        distributeFromTempTable(appId, fileName);
        jdbcTemplate.execute("DROP TABLE temp_raw_lines");
    }

    private void distributeFromTempTable(String appId, String fileName) {
        jdbcTemplate.execute("CREATE TEMPORARY TABLE IF NOT EXISTS temp_valid_events (line VARCHAR, event_type VARCHAR)");
        jdbcTemplate.execute("DELETE FROM temp_valid_events");

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
        jdbcTemplate.execute("DROP TABLE temp_valid_events");
    }

    private int naturalOrderCompare(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        
        int i = 0, j = 0;
        while (i < s1.length() && j < s2.length()) {
            char c1 = s1.charAt(i);
            char c2 = s2.charAt(j);
            
            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                // Both are digits, extract the full number part
                StringBuilder num1 = new StringBuilder();
                while (i < s1.length() && Character.isDigit(s1.charAt(i))) {
                    num1.append(s1.charAt(i++));
                }
                StringBuilder num2 = new StringBuilder();
                while (j < s2.length() && Character.isDigit(s2.charAt(j))) {
                    num2.append(s2.charAt(j++));
                }
                
                // Compare numeric values using BigInteger if they are very long, 
                // or just compare as strings with padding if same length
                if (num1.length() != num2.length()) {
                    return num1.length() - num2.length();
                }
                int res = num1.toString().compareTo(num2.toString());
                if (res != 0) return res;
            } else {
                if (c1 != c2) return c1 - c2;
                i++;
                j++;
            }
        }
        return s1.length() - s2.length();
    }

    private double calculateProgress(long processed, long total) {
        if (total <= 0) return 100.0;
        return (double) processed / total * 100.0;
    }
}
