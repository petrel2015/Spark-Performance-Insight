package com.spark.insight.parser;

import com.spark.insight.model.EnvironmentConfigModel;
import com.spark.insight.model.StorageBlockModel;
import com.spark.insight.model.TaskModel;
import lombok.extern.slf4j.Slf4j;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
@Component
public class DuckDBAppenderRegistry {

    public void cleanApp(Connection conn, String appId) throws SQLException {
        String[] tables = {"tasks", "environment_configs", "storage_blocks"};
        for (String table : tables) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE app_id = ?")) {
                ps.setString(1, appId);
                ps.executeUpdate();
            }
        }
    }

    public DuckDBAppender createAppender(Connection conn, String tableName) throws SQLException {
        DuckDBConnection duckConn = conn.unwrap(DuckDBConnection.class);
        return duckConn.createAppender("main", tableName);
    }

    public void writeTask(DuckDBAppender appender, TaskModel task) throws SQLException {
        try {
            appender.beginRow();
            appender.append(task.getId()); // 1
            appender.append(task.getAppId()); // 2
            appender.append(task.getStageId()); // 3
            appender.append(task.getAttemptId() != null ? task.getAttemptId() : 0); // 4
            appendLong(appender, task.getTaskId()); // 5
            appender.append(task.getTaskIndex() != null ? task.getTaskIndex() : -1); // 6
            appender.append(task.getExecutorId()); // 7
            appender.append(task.getHost()); // 8
            
            // Use helper for Long fields to avoid unboxing NPE
            appendLong(appender, task.getLaunchTime()); // 9
            appendLong(appender, task.getFinishTime()); // 10
            appendLong(appender, task.getDuration()); // 11
            appendLong(appender, task.getGcTime()); // 12
            appendLong(appender, task.getSchedulerDelay()); // 13
            appendLong(appender, task.getGettingResultTime()); // 14
            appendLong(appender, task.getExecutorDeserializeTime()); // 15
            appendLong(appender, task.getExecutorRunTime()); // 16
            appendLong(appender, task.getResultSerializationTime()); // 17
            appendLong(appender, task.getExecutorCpuTime()); // 18
            appendLong(appender, task.getPeakExecutionMemory()); // 19
            appendLong(appender, task.getInputBytes()); // 20
            appendLong(appender, task.getInputRecords()); // 21
            appendLong(appender, task.getOutputBytes()); // 22
            appendLong(appender, task.getOutputRecords()); // 23
            appendLong(appender, task.getMemoryBytesSpilled()); // 24
            appendLong(appender, task.getDiskBytesSpilled()); // 25
            appendLong(appender, task.getShuffleReadBytes()); // 26
            appendLong(appender, task.getShuffleReadRecords()); // 27
            appendLong(appender, task.getShuffleFetchWaitTime()); // 28
            appendLong(appender, task.getShuffleWriteBytes()); // 29
            appendLong(appender, task.getShuffleWriteTime()); // 30
            appendLong(appender, task.getShuffleWriteRecords()); // 31
            appendLong(appender, task.getShuffleRemoteRead()); // 32
            
            appender.append(task.getSpeculative() != null ? task.getSpeculative() : false); // 33
            appender.append(task.getStatus()); // 34
            appender.append(task.getLocality()); // 35
            appender.endRow();
        } catch (Exception e) {
            log.error("Failed to append task row: {}", task.getId(), e);
            throw new SQLException("Appender error", e);
        }
    }

    private void appendLong(DuckDBAppender appender, Long value) throws SQLException {
        if (value == null) {
            appender.append(0L); // Default to 0 for BIGINT metrics
        } else {
            appender.append(value.longValue());
        }
    }

    public void writeEnvConfig(DuckDBAppender appender, EnvironmentConfigModel config) throws SQLException {
        try {
            appender.beginRow();
            appender.append(config.getId());
            appender.append(config.getAppId());
            appender.append(config.getParamKey());
            appender.append(config.getParamValue());
            appender.append(config.getCategory());
            appender.endRow();
        } catch (Exception e) {
            throw new SQLException("EnvConfig appender error", e);
        }
    }

    public void writeStorageBlock(DuckDBAppender appender, StorageBlockModel block) throws SQLException {
        try {
            appender.beginRow();
            appender.append(block.getId());
            appender.append(block.getAppId());
            appender.append(block.getRddId());
            appender.append(block.getBlockName());
            appender.append(block.getStorageLevel());
            appendLong(appender, block.getMemorySize());
            appendLong(appender, block.getDiskSize());
            appender.append(block.getExecutorId());
            appender.append(block.getHost());
            appender.endRow();
        } catch (Exception e) {
            throw new SQLException("StorageBlock appender error", e);
        }
    }
}
