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
        EVENT_TABLE_MAP.put("SparkListenerSQLExecutionStart", "bronze_event_sql_execution_start");
        EVENT_TABLE_MAP.put("SparkListenerSQLExecutionEnd", "bronze_event_sql_execution_end");
        EVENT_TABLE_MAP.put("SparkListenerEnvironmentUpdate", "bronze_event_environment_update");
        EVENT_TABLE_MAP.put("SparkListenerLogStart", "bronze_event_log_start");
    }

    @Transactional
    public void ingest(String appId, List<File> files) {
        log.info("Starting Bronze ingestion for app: {}, files: {}", appId, files.size());
        
        // 1. Ensure JSON extension is loaded
        jdbcTemplate.execute("INSTALL json; LOAD json;");

        for (File file : files) {
            ingestFile(appId, file);
        }
        
        log.info("Finished Bronze ingestion for app: {}", appId);
    }

    private void ingestFile(String appId, File file) {
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        
        log.debug("Ingesting file into Bronze: {}", fileName);

        // 2. Ingest known events
        for (Map.Entry<String, String> entry : EVENT_TABLE_MAP.entrySet()) {
            String eventName = entry.getKey();
            String tableName = entry.getValue();
            
            String sql = String.format(
                "INSERT INTO %s (app_id, file_name, raw_json) " +
                "SELECT '%s', '%s', line " +
                "FROM read_text('%s') " +
                "WHERE json_extract_string(line, '$.Event') = '%s'",
                tableName, appId, fileName, filePath, eventName
            );
            
            jdbcTemplate.execute(sql);
        }

        // 3. Ingest unknown events
        StringBuilder knownEvents = new StringBuilder();
        for (String event : EVENT_TABLE_MAP.keySet()) {
            if (knownEvents.length() > 0) {
                knownEvents.append(", ");
            }
            knownEvents.append("'").append(event).append("'");
        }

        String unknownSql = String.format(
            "INSERT INTO bronze_event_unknown (app_id, file_name, event_name, raw_json) " +
            "SELECT '%s', '%s', json_extract_string(line, '$.Event'), line " +
            "FROM read_text('%s') " +
            "WHERE json_extract_string(line, '$.Event') NOT IN (%s)",
            appId, fileName, filePath, knownEvents.toString()
        );
        
        jdbcTemplate.execute(unknownSql);
    }
}
