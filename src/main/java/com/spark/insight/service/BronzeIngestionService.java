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

        for (Map.Entry<String, String> entry : EVENT_TABLE_MAP.entrySet()) {
            String eventName = entry.getKey();
            String tableName = entry.getValue();
            
            // Using \u0001 (SOH) as delimiter to avoid SQL truncation and JSON splitting
            String sql = """
                INSERT INTO %s (app_id, file_name, raw_json)
                SELECT ?, ?, line
                FROM read_csv(?, delim='\u0001', header=false, quote='', escape='', columns={'line': 'VARCHAR'})
                WHERE json_extract_string(line, '$.Event') = ?
                """.formatted(tableName);
            
            jdbcTemplate.update(sql, appId, fileName, filePath, eventName);
        }

        // Unknown events ingestion
        StringBuilder knownEventsPart = new StringBuilder();
        for (String event : EVENT_TABLE_MAP.keySet()) {
            if (knownEventsPart.length() > 0) {
                knownEventsPart.append(", ");
            }
            knownEventsPart.append("'").append(event).append("'");
        }

        String unknownSql = """
            INSERT INTO bronze_event_unknown (app_id, file_name, event_name, raw_json)
            SELECT ?, ?, json_extract_string(line, '$.Event'), line
            FROM read_csv(?, delim='\u0001', header=false, quote='', escape='', columns={'line': 'VARCHAR'})
            WHERE json_extract_string(line, '$.Event') NOT IN (%s)
            """.formatted(knownEventsPart.toString());
        
        jdbcTemplate.update(unknownSql, appId, fileName, filePath);
    }
}
