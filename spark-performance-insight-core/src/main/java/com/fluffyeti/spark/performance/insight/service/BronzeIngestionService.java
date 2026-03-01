package com.fluffyeti.spark.performance.insight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluffyeti.spark.performance.insight.annotation.MonitorStep;
import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import com.github.luben.zstd.ZstdInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.duckdb.DuckDBAppender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class BronzeIngestionService {

    private final JdbcTemplate jdbcTemplate;
    private final DuckDBManagerService duckDBManager;
    private final SystemProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Lazy
    private BronzeIngestionService self;

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
        List<File> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(Comparator.comparing(File::getName, this::naturalOrderCompare));

        long totalBytes = sortedFiles.stream().mapToLong(File::length).sum();
        long processedBytes = 0;
        
        log.info("Starting High-Performance Bronze ingestion for app: {}", appId);
        progressReporter.accept(0.0, "Bronze: Initializing...");
        
        jdbcTemplate.execute("INSTALL json; LOAD json;");

        for (String tableName : EVENT_TABLE_MAP.values()) {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE app_id = ?", appId);
        }
        jdbcTemplate.update("DELETE FROM bronze_event_unknown WHERE app_id = ?", appId);

        for (File file : sortedFiles) {
            long fileStartBytes = processedBytes;
            long fileLength = file.length();
            
            self.ingestFileStreaming(appId, file, (p, m) -> {
                double overallProgress = calculateProgress(fileStartBytes + (long)(p / 100.0 * fileLength), totalBytes);
                progressReporter.accept(overallProgress, m);
            });
            
            processedBytes += fileLength;
        }
        
        log.info("Finished Bronze ingestion for app: {}", appId);
    }

    @MonitorStep(value = "Bronze File Ingestion (Appender)", type = "FILE")
    protected void ingestFileStreaming(String appId, File file, BiConsumer<Double, String> fileProgressReporter) {
        String fileName = file.getName();
        boolean isZstd = fileName.endsWith(".zstd");
        
        log.info("Streaming ingestion via Appender for {} (Zstd: {})", fileName, isZstd);

        duckDBManager.executeNative(conn -> {
            Map<String, DuckDBAppender> appenderMap = new HashMap<>();
            try {
                for (String tableName : EVENT_TABLE_MAP.values()) {
                    appenderMap.put(tableName, conn.createAppender("main", tableName));
                }
                DuckDBAppender unknownAppender = conn.createAppender("main", "bronze_event_unknown");

                try (FileInputStream fis = new FileInputStream(file);
                     InputStream is = isZstd ? new ZstdInputStream(fis) : fis;
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    
                    String line;
                    long linesRead = 0;
                    long fileLength = file.length();
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        
                        try {
                            JsonNode node = objectMapper.readTree(line);
                            String eventType = node.get("Event") != null ? node.get("Event").asText() : null;
                            String tableName = EVENT_TABLE_MAP.get(eventType);
                            
                            if (tableName != null) {
                                appendRow(appenderMap.get(tableName), appId, fileName, line, null);
                            } else {
                                appendRow(unknownAppender, appId, fileName, line, eventType);
                            }
                        } catch (Exception e) {
                            appendRow(unknownAppender, appId, fileName, line, "INVALID_JSON");
                        }
                        
                        linesRead++;
                        if (linesRead % 10000 == 0) {
                            long diskPos = fis.getChannel().position();
                            double filePercent = fileLength > 0 ? Math.min(99.9, (double) diskPos / fileLength * 100.0) : 0.0;
                            fileProgressReporter.accept(filePercent, String.format("Bronze: Processing %s (%d lines)", fileName, linesRead));
                        }
                    }
                }
                
                fileProgressReporter.accept(100.0, String.format("Bronze: Finished %s", fileName));
                return null;
            } catch (Exception e) {
                log.error("Fatal appender error for file {}: {}", fileName, e.getMessage());
                throw new RuntimeException("Appender ingestion failed", e);
            } finally {
                appenderMap.values().forEach(a -> { try { a.close(); } catch (Exception ignored) {} });
            }
        });
    }

    private void appendRow(DuckDBAppender appender, String appId, String fileName, String json, String eventName) throws java.sql.SQLException {
        appender.beginRow();
        // 1. id (UUID)
        appender.append(UUID.randomUUID().toString());
        // 2. app_id (VARCHAR)
        appender.append(appId);
        // 3. file_name (VARCHAR)
        appender.append(fileName);
        // 4. event_name (VARCHAR) - ONLY for bronze_event_unknown
        if (eventName != null) {
            appender.append(eventName);
        }
        // 5. raw_json (JSON)
        appender.append(json);
        // 6. ingested_at (TIMESTAMP) - Use formatted string
        appender.append(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        appender.endRow();
    }

    private int naturalOrderCompare(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        int i = 0, j = 0;
        while (i < s1.length() && j < s2.length()) {
            char c1 = s1.charAt(i); char c2 = s2.charAt(j);
            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                StringBuilder num1 = new StringBuilder(); while (i < s1.length() && Character.isDigit(s1.charAt(i))) num1.append(s1.charAt(i++));
                StringBuilder num2 = new StringBuilder(); while (j < s2.length() && Character.isDigit(s2.charAt(j))) num2.append(s2.charAt(j++));
                if (num1.length() != num2.length()) return num1.length() - num2.length();
                int res = num1.toString().compareTo(num2.toString()); if (res != 0) return res;
            } else { if (c1 != c2) return c1 - c2; i++; j++; }
        }
        return s1.length() - s2.length();
    }

    private double calculateProgress(long processed, long total) {
        if (total <= 0) return 100.0;
        return (double) processed / total * 100.0;
    }
}
