package com.spark.insight.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.spark.insight.mapper.ParsedEventLogMapper;
import com.spark.insight.model.*;
import com.spark.insight.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class JacksonEventParser implements EventParser {

    private final ObjectMapper objectMapper;
    private final ApplicationService applicationService;
    private final StageService stageService;
    private final TaskService taskService;
    private final EnvironmentConfigService envService;
    private final JobService jobService;
    private final ExecutorService executorService;
    private final SqlExecutionService sqlExecutionService;
    private final StorageService storageService;
    private final ParsedEventLogMapper parsedLogMapper;
    private final StatusBroadcaster broadcaster;
    private final javax.sql.DataSource dataSource;
    // Use a single-threaded executor for ALL database writes to avoid DuckDB lock contention
    private final java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public JacksonEventParser(ApplicationService applicationService,
                              StageService stageService,
                              TaskService taskService,
                              EnvironmentConfigService envService,
                              JobService jobService,
                              ExecutorService executorService,
                              SqlExecutionService sqlExecutionService,
                              StorageService storageService,
                              ParsedEventLogMapper parsedLogMapper,
                              StatusBroadcaster broadcaster,
                              javax.sql.DataSource dataSource) {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();
        this.objectMapper = new ObjectMapper(factory);
        this.applicationService = applicationService;
        this.stageService = stageService;
        this.taskService = taskService;
        this.envService = envService;
        this.jobService = jobService;
        this.executorService = executorService;
        this.sqlExecutionService = sqlExecutionService;
        this.storageService = storageService;
        this.parsedLogMapper = parsedLogMapper;
        this.broadcaster = broadcaster;
        this.dataSource = dataSource;
    }

    @Override
    public void parse(File logFile, String appId) {
        long startTime = System.currentTimeMillis();
        long fileSize = logFile.length();
        log.info("Processing log: {}, appId: {}, size: {}",
                logFile.getName(), (appId != null ? appId : "unknown"), formatFileSize(fileSize));
        String currentAppId = appId;
        if (currentAppId != null) {
            updateParsingProgress(currentAppId, 0, fileSize);
        }

        try {
            InputStream inputStream = new FileInputStream(logFile);
            CountingInputStream countingIs = new CountingInputStream(inputStream);
            InputStream finalInputStream = countingIs;
            if (logFile.getName().endsWith(".zstd") || logFile.getName().endsWith(".zst")) {
                finalInputStream = new ZstdInputStream(countingIs);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalInputStream))) {
                String line;
                String versionFromLogStart = null;
                List<TaskModel> taskBatch = new ArrayList<>();
                List<EnvironmentConfigModel> envBatch = new ArrayList<>();
                List<ExecutorModel> executorBatch = new ArrayList<>();
                Map<Integer, Integer> stageToJobMap = new HashMap<>();

                long lineCount = 0;
                long lastUpdate = System.currentTimeMillis();

                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    // Update progress every 2 seconds
                    if (currentAppId != null && System.currentTimeMillis() - lastUpdate > 2000) {
                        updateParsingProgress(currentAppId, countingIs.getBytesRead(), fileSize);
                        lastUpdate = System.currentTimeMillis();
                    }

                    try {
                        JsonNode node = objectMapper.readTree(line);
                        if (!node.has("Event")) {
                            continue;
                        }
                        String eventType = node.get("Event").asText();

                        // 尝试从环境更新中提取/校正 App ID
                        if (eventType.equals("SparkListenerEnvironmentUpdate")) {
                            JsonNode sparkProps = node.get("Spark Properties");
                            if (sparkProps != null && sparkProps.has("spark.app.id")) {
                                String realAppId = sparkProps.get("spark.app.id").asText();
                                if (currentAppId == null || !currentAppId.equals(realAppId)) {
                                    log.info("Detected/Corrected App ID from EnvironmentUpdate: {} (previously: {})", realAppId, currentAppId);
                                    currentAppId = realAppId;
                                    updateImportingStatus(logFile.getName());
                                }

                                ApplicationModel app = applicationService.getById(currentAppId);
                                if (app == null) {
                                    app = new ApplicationModel();
                                    app.setAppId(currentAppId);
                                    app.setAppName(sparkProps.has("spark.app.name") ? sparkProps.get("spark.app.name").asText() : "Unknown App");
                                    app.setUserName(sparkProps.has("spark.user.name") ? sparkProps.get("spark.user.name").asText() : "unknown");
                                    app.setStartTime(parseTimestamp(System.currentTimeMillis()));
                                    app.setSparkVersion(versionFromLogStart != null ? versionFromLogStart : "unknown");
                                    app.setParsingStatus("PARSING");
                                    updateParsingProgress(currentAppId, countingIs.getBytesRead(), fileSize);
                                    applicationService.saveOrUpdate(app);
                                } else if (versionFromLogStart != null && (app.getSparkVersion() == null || app.getSparkVersion().equals("unknown"))) {
                                    app.setSparkVersion(versionFromLogStart);
                                    applicationService.updateById(app);
                                }
                            }
                        }

                        switch (eventType) {
                            case "SparkListenerLogStart":
                                if (node.has("Spark Version")) {
                                    versionFromLogStart = node.get("Spark Version").asText();
                                    if (currentAppId != null) {
                                        ApplicationModel app = applicationService.getById(currentAppId);
                                        if (app != null) {
                                            app.setSparkVersion(versionFromLogStart);
                                            applicationService.updateById(app);
                                        }
                                    }
                                }
                                break;
                            case "SparkListenerApplicationStart":
                                currentAppId = node.get("App ID").asText();
                                updateImportingStatus(logFile.getName());
                                handleAppStart(node, currentAppId, versionFromLogStart, fileSize);
                                break;
                            case "SparkListenerEnvironmentUpdate":
                                if (currentAppId != null) {
                                    handleEnvUpdate(node, currentAppId, envBatch);
                                    if (envBatch.size() > 500) {
                                        List<EnvironmentConfigModel> batchToSave = new ArrayList<>(envBatch);
                                        envBatch.clear();
                                        dbExecutor.submit(() -> saveDeduplicatedEnv(batchToSave));
                                    }
                                }
                                break;
                            case "SparkListenerJobStart":
                                if (currentAppId != null) {
                                    handleJobStart(node, currentAppId, stageToJobMap);
                                }
                                break;
                            case "SparkListenerJobEnd":
                                if (currentAppId != null) {
                                    handleJobEnd(node, currentAppId);
                                }
                                break;
                            case "SparkListenerExecutorAdded":
                                if (currentAppId != null) {
                                    handleExecutorAdded(node, currentAppId, executorBatch);
                                }
                                break;
                            case "SparkListenerExecutorRemoved":
                                if (currentAppId != null) {
                                    handleExecutorRemoved(node, currentAppId);
                                }
                                break;
                            case "SparkListenerStageSubmitted":
                                if (currentAppId != null) {
                                    handleStageSubmitted(node, currentAppId, stageToJobMap);
                                }
                                break;
                            case "SparkListenerStageCompleted":
                                if (currentAppId != null) {
                                    handleStageCompleted(node, currentAppId);
                                }
                                break;
                            case "SparkListenerTaskEnd":
                                if (currentAppId != null) {
                                    handleTaskEnd(node, currentAppId, taskBatch);
                                    if (taskBatch.size() >= 1000) {
                                        List<TaskModel> batchToSave = new ArrayList<>(taskBatch);
                                        taskBatch.clear();
                                        dbExecutor.submit(() -> saveDeduplicatedTasks(batchToSave));
                                    }
                                }
                                break;
                            case "SparkListenerApplicationEnd":
                                if (currentAppId != null) {
                                    handleAppEnd(node, currentAppId);
                                }
                                break;
                            case "org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionStart":
                                if (currentAppId != null) {
                                    handleSqlStart(node, currentAppId);
                                }
                                break;
                            case "org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionEnd":
                                if (currentAppId != null) {
                                    handleSqlEnd(node, currentAppId);
                                }
                                break;
                            case "SparkListenerBlockUpdated":
                                if (currentAppId != null) {
                                    handleBlockUpdated(node, currentAppId);
                                }
                                break;
                            case "SparkListenerUnpersistRDD":
                                if (currentAppId != null) {
                                    handleUnpersistRDD(node, currentAppId);
                                }
                                break;
                        }
                    } catch (Exception lineEx) {
                        log.error("[PARSER_ERROR] Error in file: {}, at line: {}", logFile.getName(), lineCount, lineEx);
                    }
                }
                // 扫尾
                if (!taskBatch.isEmpty()) {
                    List<TaskModel> batchToSave = new ArrayList<>(taskBatch);
                    dbExecutor.submit(() -> saveDeduplicatedTasks(batchToSave));
                }
                if (!envBatch.isEmpty()) {
                    List<EnvironmentConfigModel> batchToSave = new ArrayList<>(envBatch);
                    dbExecutor.submit(() -> saveDeduplicatedEnv(batchToSave));
                }

                // Update final progress for this specific file using dynamic counts if appId is available
                if (currentAppId != null) {
                    updateParsingProgress(currentAppId, countingIs.getBytesRead(), fileSize);
                }

                // 触发后期预计算 - 只在最后一个文件处理完后，或者每个文件都触发但标记 READY 要谨慎
                if (currentAppId != null) {
                    final String appIdFinal = currentAppId;

                    dbExecutor.submit(() -> {
                        try {
                            // Check if this is the last file dynamically
                            long totalFilesForApp = parsedLogMapper.selectCount(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParsedEventLogModel>()
                                            .eq(ParsedEventLogModel::getAppId, appIdFinal)
                            );
                            long processedFilesForApp = parsedLogMapper.selectCount(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParsedEventLogModel>()
                                            .eq(ParsedEventLogModel::getAppId, appIdFinal)
                                            .eq(ParsedEventLogModel::getStatus, EventLogStatus.SUCCESS)
                            );
                            final boolean isLastFile = processedFilesForApp >= totalFilesForApp;

                            if (!isLastFile) {
                                return;
                            }
                            log.info("Starting post-calculation for App: {} (Processed {}/{})", appIdFinal, processedFilesForApp, totalFilesForApp);
                            updatePostCalculationProgress(appIdFinal, "Calculating Stage/Job metrics...", 82.0);
                            stageService.calculateStageMetrics(appIdFinal);
                            updatePostCalculationProgress(appIdFinal, "Calculating SQL metrics...", 88.0);
                            jobService.calculateJobMetrics(appIdFinal);
                            sqlExecutionService.calculateSqlMetrics(appIdFinal);
                            updatePostCalculationProgress(appIdFinal, "Finalizing metrics...", 95.0);
                            executorService.calculateExecutorMetrics(appIdFinal);

                            finalizeAppQuality(appIdFinal);
                            broadcaster.broadcastStatus(appIdFinal, "SUCCESS", 100.0, "Analysis complete.");
                        } catch (Exception exception) {
                            log.error("Failed to complete post-calculation for App: " + appIdFinal, exception);
                            finalizeAppQuality(appIdFinal); // Ensure it's marked ready even on partial failure
                            broadcaster.broadcastStatus(appIdFinal, "FAILED", 100.0, "Analysis failed during post-calculation.");
                        }
                    });
                }
            }
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Finished processing log: {} in {}", logFile.getName(), formatDuration(durationMs));
        } catch (Exception exception) {
            log.error("Error parsing " + logFile.getPath(), exception);
        }
    }

    private void finalizeAppQuality(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null) {
            boolean isUpdated = false;
            if (app.getEndTime() == null) {
                app.setDataQualityStatus("INCOMPLETE");
                app.setDataQualityNote("Missing ApplicationEnd event. Log might be truncated.");
                isUpdated = true;
            } else {
                if (app.getDataQualityStatus() == null) {
                    app.setDataQualityStatus("GOOD");
                    isUpdated = true;
                }
            }

            if (!"READY".equals(app.getParsingStatus())) {
                app.setParsingStatus("SUCCESS");
                app.setParsingProgress(null); // Clear progress message when ready
                isUpdated = true;
            }

            if (isUpdated) {
                applicationService.updateById(app);
                log.info("Updated App Data Quality for {}: Status={}, ParsingStatus=SUCCESS", appId, app.getDataQualityStatus());
            }
        }
    }

    private void forceMarkReady(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null && !"SUCCESS".equals(app.getParsingStatus())) {
            app.setParsingStatus("SUCCESS");
            app.setParsingProgress(null);
            applicationService.updateById(app);
            log.warn("Force marked App {} as SUCCESS due to errors in post-calculation", appId);
        }
    }

    private void updateImportingStatus(String fileName) {
        ParsedEventLogModel logRecord = parsedLogMapper.selectById(fileName);
        if (logRecord != null && logRecord.getStatus() == EventLogStatus.PROCESSING) {
            logRecord.setStatus(EventLogStatus.IMPORTING);
            parsedLogMapper.updateById(logRecord);
        }
    }

    private void updateParsingProgress(String appId, long bytesRead, long currentFileTotalBytes) {
        ApplicationModel app = applicationService.getById(appId);
        
        // Handle potential race condition where record is still being committed
        if (app == null) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            app = applicationService.getById(appId);
        }

        if (app != null) {
            // Calculate progress based on total log size (which represents 0-80% of total job)
            long appTotalBytes = (app.getTotalLogSize() != null && app.getTotalLogSize() > 0) 
                    ? app.getTotalLogSize() : currentFileTotalBytes;
            
            // Sum sizes of already processed (SUCCESS) files for this specific App
            long previouslyProcessedBytes = parsedLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParsedEventLogModel>()
                            .eq(ParsedEventLogModel::getAppId, appId)
                            .eq(ParsedEventLogModel::getStatus, EventLogStatus.SUCCESS)
            ).stream().mapToLong(ParsedEventLogModel::getFileSize).sum();

            long totalBytesRead = previouslyProcessedBytes + bytesRead;
            double rawPercentage = appTotalBytes > 0 ? (totalBytesRead * 100.0 / appTotalBytes) : 0;
            
            // Map 0-100% of bytes to 1-80% of total progress (min 1% to ensure visibility)
            double mappedPercentage = Math.max(1.0, rawPercentage * 0.8);
            
            String msg = String.format("Loading logs: %s / %s (%.1f%%)",
                    formatFileSize(totalBytesRead), formatFileSize(appTotalBytes), rawPercentage);
            
            app.setParsingStatus("LOADING");
            app.setParsingProgress(msg);
            applicationService.updateById(app);
            
            broadcaster.broadcastStatus(appId, "LOADING", mappedPercentage, msg);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private void updatePostCalculationProgress(String appId, String stage, double progress) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null) {
            app.setParsingStatus("PRE_CALCULATING");
            app.setParsingProgress(stage);
            applicationService.updateById(app);
            broadcaster.broadcastStatus(appId, "PRE_CALCULATING", progress, stage);
        }
    }

    private void clearParsingProgress(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null) {
            app.setParsingProgress(null);
            applicationService.updateById(app);
        }
    }

    private void saveDeduplicatedTasks(List<TaskModel> batch) {
        Map<String, TaskModel> unique = new HashMap<>();
        for (TaskModel task : batch) {
            unique.put(task.getId(), task);
        }

        try {
            fastBatchInsertTasks(new ArrayList<>(unique.values()));
        } catch (Exception exception) {
            log.error("Fast batch insert failed, falling back to Service saveBatch. Error: {}", exception.getMessage());
            taskService.saveOrUpdateBatch(unique.values());
        }
    }

    private void fastBatchInsertTasks(List<TaskModel> tasks) throws java.sql.SQLException {
        if (tasks.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO tasks (" +
                "id, app_id, stage_id, attempt_id, task_id, task_index, executor_id, host, " +
                "launch_time, finish_time, duration, gc_time, scheduler_delay, getting_result_time, " +
                "executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, " +
                "peak_execution_memory, input_bytes, input_records, output_bytes, output_records, " +
                "memory_bytes_spilled, disk_bytes_spilled, shuffle_read_bytes, shuffle_read_records, " +
                "shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, shuffle_write_records, " +
                "shuffle_remote_read, speculative, status, locality" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {

            connection.setAutoCommit(false); // Optimize for batch

            for (TaskModel task : tasks) {
                int idx = 1;
                ps.setString(idx++, task.getId());
                ps.setString(idx++, task.getAppId());
                ps.setInt(idx++, task.getStageId());
                ps.setInt(idx++, task.getAttemptId());
                ps.setLong(idx++, task.getTaskId());
                ps.setInt(idx++, task.getTaskIndex());
                ps.setString(idx++, task.getExecutorId());
                ps.setString(idx++, task.getHost());
                ps.setLong(idx++, task.getLaunchTime());
                ps.setLong(idx++, task.getFinishTime());
                ps.setLong(idx++, task.getDuration());
                ps.setLong(idx++, task.getGcTime());
                ps.setLong(idx++, task.getSchedulerDelay());
                ps.setLong(idx++, task.getGettingResultTime());
                ps.setLong(idx++, task.getExecutorDeserializeTime());
                ps.setLong(idx++, task.getExecutorRunTime());
                ps.setLong(idx++, task.getResultSerializationTime());
                ps.setLong(idx++, task.getExecutorCpuTime());
                ps.setLong(idx++, task.getPeakExecutionMemory());
                ps.setLong(idx++, task.getInputBytes());
                ps.setLong(idx++, task.getInputRecords());
                ps.setLong(idx++, task.getOutputBytes());
                ps.setLong(idx++, task.getOutputRecords());
                ps.setLong(idx++, task.getMemoryBytesSpilled());
                ps.setLong(idx++, task.getDiskBytesSpilled());
                ps.setLong(idx++, task.getShuffleReadBytes());
                ps.setLong(idx++, task.getShuffleReadRecords());
                ps.setLong(idx++, task.getShuffleFetchWaitTime());
                ps.setLong(idx++, task.getShuffleWriteBytes());
                ps.setLong(idx++, task.getShuffleWriteTime());
                ps.setLong(idx++, task.getShuffleWriteRecords());
                ps.setLong(idx++, task.getShuffleRemoteRead());
                ps.setBoolean(idx++, task.getSpeculative());
                ps.setString(idx++, task.getStatus());
                ps.setString(idx++, task.getLocality());

                ps.addBatch();
            }

            ps.executeBatch();
            connection.commit();
        }
    }

    private void saveDeduplicatedEnv(List<EnvironmentConfigModel> batch) {
        Map<String, EnvironmentConfigModel> unique = new HashMap<>();
        for (EnvironmentConfigModel config : batch) {
            unique.put(config.getId(), config);
        }
        envService.upsertBatch(new ArrayList<>(unique.values()));
    }

    private void handleAppStart(JsonNode node, String appId, String versionFromLogStart, long fileSize) {
        ApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            app = new ApplicationModel();
            app.setAppId(appId);
        }
        if (!"READY".equals(app.getParsingStatus())) {
            app.setParsingStatus("PARSING");
            updateParsingProgress(appId, 0, fileSize);
        }
        app.setAppName(node.get("App Name").asText());
        app.setUserName(node.get("User").asText());
        app.setStartTime(parseTimestamp(node.get("Timestamp").asLong()));

        if (app.getSparkVersion() == null || app.getSparkVersion().equals("unknown")) {
            String version = node.has("Spark Version") ? node.get("Spark Version").asText() : versionFromLogStart;
            app.setSparkVersion(version != null ? version : "unknown");
        }

        applicationService.saveOrUpdate(app);
    }

    private void handleJobStart(JsonNode node, String appId, Map<Integer, Integer> stageToJobMap) {
        int jobId = node.get("Job ID").asInt();
        JobModel job = new JobModel();
        job.setId(appId + ":" + jobId);
        job.setAppId(appId);
        job.setJobId(jobId);
        job.setSubmissionTime(parseTimestamp(node.get("Submission Time").asLong()));
        job.setStatus("RUNNING");

        String description = null;
        if (node.has("Properties")) {
            JsonNode props = node.get("Properties");
            if (props.has("spark.job.description")) {
                description = props.get("spark.job.description").asText();
            } else if (props.has("spark.job.callSite")) {
                description = props.get("spark.job.callSite").asText();
            }

            if (props.has("spark.jobGroup.id")) {
                job.setJobGroup(props.get("spark.jobGroup.id").asText());
            }

            if (props.has("spark.sql.execution.id")) {
                job.setSqlExecutionId(props.get("spark.sql.execution.id").asLong());
            }
        }

        if (description == null || description.isEmpty()) {
            JsonNode stageInfos = node.get("Stage Infos");
            if (stageInfos != null && stageInfos.isArray() && stageInfos.size() > 0) {
                JsonNode firstStage = stageInfos.get(0);
                if (firstStage.has("Stage Name")) {
                    description = firstStage.get("Stage Name").asText();
                }
            }
        }

        if (description != null && description.contains("\n")) {
            description = description.split("\\n")[0];
        }

        if (description != null && description.length() > 250) {
            description = description.substring(0, 247) + "...";
        }

        job.setDescription(description);

        JsonNode stageInfos = node.get("Stage Infos");
        if (stageInfos != null && stageInfos.isArray()) {
            job.setNumStages(stageInfos.size());
            List<String> sids = new ArrayList<>();
            int totalTasks = 0;
            for (JsonNode stageNode : stageInfos) {
                int sid = stageNode.get("Stage ID").asInt();
                sids.add(String.valueOf(sid));
                stageToJobMap.put(sid, jobId);
                if (stageNode.has("Number of Tasks")) {
                    totalTasks += stageNode.get("Number of Tasks").asInt();
                }
            }
            job.setStageIds(String.join(",", sids));
            job.setNumTasks(totalTasks);
        }
        jobService.saveOrUpdate(job);
    }

    private void handleJobEnd(JsonNode node, String appId) {
        int jobId = node.get("Job ID").asInt();
        JobModel job = jobService.getById(appId + ":" + jobId);
        if (job != null) {
            LocalDateTime completionTime = parseTimestamp(node.get("Completion Time").asLong());
            job.setCompletionTime(completionTime);
            job.setStatus(node.get("Job Result").get("Result").asText().equals("JobSucceeded") ? "SUCCEEDED" : "FAILED");

            if (job.getSubmissionTime() != null && completionTime != null) {
                job.setDuration(java.time.Duration.between(job.getSubmissionTime(), completionTime).toMillis());
            }

            jobService.updateById(job);
        }
    }

    private void handleExecutorAdded(JsonNode node, String appId, List<ExecutorModel> batch) {
        String execId = node.get("Executor ID").asText();
        JsonNode info = node.get("Executor Info");
        long timestamp = node.get("Timestamp").asLong();

        ExecutorModel executor = new ExecutorModel();
        executor.setId(appId + ":" + execId);
        executor.setAppId(appId);
        executor.setExecutorId(execId);
        executor.setHost(info.get("Host").asText());
        executor.setAddTime(parseTimestamp(timestamp));
        executor.setTotalCores(info.get("Total Cores").asInt());
        executor.setMemory(info.has("Memory") ? info.get("Memory").asLong() : 0L);
        executor.setIsActive(true);
        executorService.saveOrUpdate(executor);
    }

    private void handleExecutorRemoved(JsonNode node, String appId) {
        String execId = node.get("Executor ID").asText();
        long timestamp = node.get("Timestamp").asLong();

        ExecutorModel executor = executorService.getById(appId + ":" + execId);
        if (executor != null) {
            executor.setRemoveTime(parseTimestamp(timestamp));
            executor.setIsActive(false);
            executor.setExecLossReason(node.has("Removed Reason") ? node.get("Removed Reason").asText() : "unknown");
            executorService.updateById(executor);
        }
    }

    private void handleEnvUpdate(JsonNode node, String appId, List<EnvironmentConfigModel> batch) {
        int initialSize = batch.size();
        extractProps(node, "Spark Properties", "spark_conf", appId, batch);
        extractProps(node, "JVM Information", "jvm_info", appId, batch);
        extractProps(node, "Hadoop Properties", "hadoop_conf", appId, batch);
        extractProps(node, "System Properties", "system_props", appId, batch);
        extractProps(node, "Metrics Properties", "metrics_props", appId, batch);
        extractProps(node, "Classpath Entries", "classpath_entries", appId, batch);
        log.info("Extracted {} environment properties for App: {}", batch.size() - initialSize, appId);
    }

    private void extractProps(JsonNode node, String fieldName, String category, String appId, List<EnvironmentConfigModel> batch) {
        JsonNode props = node.get(fieldName);
        if (props == null) {
            return;
        }

        if (props.isObject()) {
            props.fields().forEachRemaining(entry -> {
                addEnvConfig(appId, category, entry.getKey(), entry.getValue().asText(), batch);
            });
        } else if (props.isArray()) {
            for (JsonNode item : props) {
                if (item.isArray() && item.size() >= 2) {
                    addEnvConfig(appId, category, item.get(0).asText(), item.get(1).asText(), batch);
                } else if (item.isObject()) {
                    String name = item.has("Name") ? item.get("Name").asText() : (item.has("key") ? item.get("key").asText() : null);
                    String value = item.has("Value") ? item.get("Value").asText() : (item.has("value") ? item.get("value").asText() : "");
                    if (name != null) {
                        addEnvConfig(appId, category, name, value, batch);
                    }
                }
            }
        }
    }

    private void addEnvConfig(String appId, String category, String key, String value, List<EnvironmentConfigModel> batch) {
        EnvironmentConfigModel config = new EnvironmentConfigModel();
        config.setId(appId + ":" + category + ":" + key);
        config.setAppId(appId);
        config.setParamKey(key);
        config.setParamValue(value);
        config.setCategory(category);
        batch.add(config);
    }

    private void handleStageSubmitted(JsonNode node, String appId, Map<Integer, Integer> stageToJobMap) {
        JsonNode info = node.get("Stage Info");
        int stageId = info.get("Stage ID").asInt();
        int attemptId = info.get("Stage Attempt ID").asInt();
        StageModel stage = new StageModel();
        stage.setId(appId + ":" + stageId + ":" + attemptId);
        stage.setAppId(appId);
        stage.setStageId(stageId);
        stage.setAttemptId(attemptId);
        stage.setJobId(stageToJobMap.get(stageId));
        stage.setStageName(info.get("Stage Name").asText());
        stage.setNumTasks(info.get("Number of Tasks").asInt());
        stage.setSubmissionTime(parseTimestamp(info.get("Submission Time").asLong()));
        stage.setStatus("RUNNING");

        if (info.has("Parent IDs")) {
            JsonNode parents = info.get("Parent IDs");
            if (parents.isArray() && parents.size() > 0) {
                List<String> parentIds = new ArrayList<>();
                for (JsonNode parentNode : parents) {
                    parentIds.add(parentNode.asText());
                }
                stage.setParentStageIds(String.join(",", parentIds));
            }
        }

        if (info.has("RDD Info")) {
            JsonNode rddInfos = info.get("RDD Info");
            stage.setRddInfo(rddInfos.toString());

            // --- 提取 RDD 存储元数据 ---
            for (JsonNode rddNode : rddInfos) {
                if (rddNode.has("Storage Level")) {
                    String storageLevelStr = rddNode.get("Storage Level").toString();
                    // 只有设置了持久化的 RDD 才记录
                    if (storageLevelStr.contains("useMemory") || storageLevelStr.contains("useDisk")) {
                        StorageRddModel rdd = new StorageRddModel();
                        int rddId = rddNode.get("RDD ID").asInt();
                        rdd.setId(appId + ":" + rddId);
                        rdd.setAppId(appId);
                        rdd.setRddId(rddId);
                        rdd.setName(rddNode.get("Name").asText());
                        rdd.setStorageLevel(rddNode.get("Storage Level").get("description").asText());
                        rdd.setNumPartitions(rddNode.get("Number of Partitions").asInt());
                        rdd.setNumCached_partitions(rddNode.get("Number of Cached Partitions").asInt());
                        rdd.setMemorySize(rddNode.get("Memory Size").asLong());
                        rdd.setDiskSize(rddNode.get("Disk Size").asLong());

                        dbExecutor.submit(() -> {
                            try (java.sql.Connection connection = dataSource.getConnection();
                                 java.sql.PreparedStatement ps = connection.prepareStatement(
                                         "INSERT OR REPLACE INTO storage_rdds (id, app_id, rdd_id, name, storage_level, num_partitions, num_cached_partitions, memory_size, disk_size) " +
                                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                                ps.setString(1, rdd.getId());
                                ps.setString(2, rdd.getAppId());
                                ps.setInt(3, rdd.getRddId());
                                ps.setString(4, rdd.getName());
                                ps.setString(5, rdd.getStorageLevel());
                                ps.setInt(6, rdd.getNumPartitions());
                                ps.setInt(7, rdd.getNumCached_partitions());
                                ps.setLong(8, rdd.getMemorySize());
                                ps.setLong(9, rdd.getDiskSize());
                                ps.executeUpdate();
                            } catch (Exception exception) {
                                log.error("Failed to save RDD info", exception);
                            }
                        });
                    }
                }
            }
        }

        stageService.saveOrUpdate(stage);
    }

    private void handleStageCompleted(JsonNode node, String appId) {
        JsonNode info = node.get("Stage Info");
        int stageId = info.get("Stage ID").asInt();
        int attemptId = info.get("Stage Attempt ID").asInt();
        StageModel stage = stageService.getById(appId + ":" + stageId + ":" + attemptId);
        if (stage != null) {
            if (info.has("Completion Time")) {
                LocalDateTime completionTime = parseTimestamp(info.get("Completion Time").asLong());
                stage.setCompletionTime(completionTime);

                if (stage.getSubmissionTime() != null && completionTime != null) {
                    stage.setDuration(java.time.Duration.between(stage.getSubmissionTime(), completionTime).toMillis());
                }
            }
            if (info.has("Failure Reason")) {
                stage.setStatus("FAILED");
            } else {
                stage.setStatus("SUCCEEDED");
            }
            stageService.updateById(stage);
        }
    }

    private void handleTaskEnd(JsonNode node, String appId, List<TaskModel> batch) {
        JsonNode info = node.get("Task Info");
        JsonNode metrics = node.get("Task Metrics");
        if (info == null || info.isNull()) {
            return;
        }

        int stageId = node.has("Stage ID") ? node.get("Stage ID").asInt() : -1;
        int attemptId = node.has("Stage Attempt ID") ? node.get("Stage Attempt ID").asInt() : 0;
        long taskId = info.has("Task ID") ? info.get("Task ID").asLong() : -1L;
        int taskIndex = info.has("Index") ? info.get("Index").asInt() : -1;

        long launchTime = info.has("Launch Time") ? info.get("Launch Time").asLong() : 0L;
        long finishTime = info.has("Finish Time") ? info.get("Finish Time").asLong() : 0L;
        long duration = info.has("Duration") ? info.get("Duration").asLong() : 0L;

        if (duration <= 0 && finishTime > launchTime) {
            duration = finishTime - launchTime;
        }

        TaskModel task = new TaskModel();
        task.setId(appId + ":" + stageId + ":" + taskId);
        task.setAppId(appId);
        task.setStageId(stageId);
        task.setAttemptId(attemptId);
        task.setTaskId(taskId);
        task.setTaskIndex(taskIndex);
        task.setExecutorId(info.has("Executor ID") ? info.get("Executor ID").asText() : "unknown");
        task.setHost(info.has("Host") ? info.get("Host").asText() : "unknown");
        task.setLocality(info.has("Locality") ? info.get("Locality").asText() : "unknown");
        task.setLaunchTime(launchTime);
        task.setFinishTime(finishTime);
        task.setDuration(duration);
        task.setSpeculative(info.has("Speculative") ? info.get("Speculative").asBoolean() : false);

        String status = "unknown";
        if (node.has("Task End Reason")) {
            JsonNode reason = node.get("Task End Reason");
            if (reason.has("Reason") && reason.get("Reason").asText().equals("Success")) {
                status = "SUCCESS";
            } else {
                status = "FAILED";
            }
        }
        task.setStatus(status);

        if (metrics != null && !metrics.isNull()) {
            long executorDeserializeTime = metrics.has("Executor Deserialize Time") ? metrics.get("Executor Deserialize Time").asLong() : 0L;
            long executorRunTime = metrics.has("Executor Run Time") ? metrics.get("Executor Run Time").asLong() : 0L;
            long resultSerializationTime = metrics.has("Result Serialization Time") ? metrics.get("Result Serialization Time").asLong() : 0L;
            long executorCpuTime = metrics.has("Executor CPU Time") ? metrics.get("Executor CPU Time").asLong() : 0L;

            task.setExecutorDeserializeTime(executorDeserializeTime);
            task.setExecutorRunTime(executorRunTime);
            task.setResultSerializationTime(resultSerializationTime);
            task.setExecutorCpuTime(executorCpuTime);

            long gettingResultTime = info.has("Getting Result Time") ? info.get("Getting Result Time").asLong() : 0L;
            task.setGettingResultTime(gettingResultTime);

            long schedulerDelay = Math.max(0L, duration - executorDeserializeTime - executorRunTime - resultSerializationTime - gettingResultTime);
            task.setSchedulerDelay(schedulerDelay);

            task.setGcTime(metrics.has("JVM GC Time") ? metrics.get("JVM GC Time").asLong() : 0L);
            task.setPeakExecutionMemory(metrics.has("Peak Execution Memory") ? metrics.get("Peak Execution Memory").asLong() : 0L);

            JsonNode inputMetrics = metrics.get("Input Metrics");
            if (inputMetrics != null && !inputMetrics.isNull()) {
                task.setInputBytes(inputMetrics.has("Bytes Read") ? inputMetrics.get("Bytes Read").asLong() : 0L);
                task.setInputRecords(inputMetrics.has("Records Read") ? inputMetrics.get("Records Read").asLong() : 0L);
            }

            JsonNode outputMetrics = metrics.get("Output Metrics");
            if (outputMetrics != null && !outputMetrics.isNull()) {
                task.setOutputBytes(outputMetrics.has("Bytes Written") ? outputMetrics.get("Bytes Written").asLong() : 0L);
                task.setOutputRecords(outputMetrics.has("Records Written") ? outputMetrics.get("Records Written").asLong() : 0L);
            }

            task.setMemoryBytesSpilled(metrics.has("Memory Bytes Spilled") ? metrics.get("Memory Bytes Spilled").asLong() : 0L);
            task.setDiskBytesSpilled(metrics.has("Disk Bytes Spilled") ? metrics.get("Disk Bytes Spilled").asLong() : 0L);

            JsonNode srMetrics = metrics.get("Shuffle Read Metrics");
            if (srMetrics != null && !srMetrics.isNull()) {
                long remote = srMetrics.has("Remote Bytes Read") ? srMetrics.get("Remote Bytes Read").asLong() : 0L;
                long local = srMetrics.has("Local Bytes Read") ? srMetrics.get("Local Bytes Read").asLong() : 0L;
                task.setShuffleReadBytes(remote + local);
                task.setShuffleRemoteRead(remote);
                task.setShuffleReadRecords(srMetrics.has("Total Records Read") ? srMetrics.get("Total Records Read").asLong() : 0L);
                task.setShuffleFetchWaitTime(srMetrics.has("Fetch Wait Time") ? srMetrics.get("Fetch Wait Time").asLong() : 0L);
            }

            JsonNode swMetrics = metrics.get("Shuffle Write Metrics");
            if (swMetrics != null && !swMetrics.isNull()) {
                task.setShuffleWriteBytes(swMetrics.has("Shuffle Bytes Written") ? swMetrics.get("Shuffle Bytes Written").asLong() : 0L);
                task.setShuffleWriteRecords(swMetrics.has("Shuffle Records Written") ? swMetrics.get("Shuffle Records Written").asLong() : 0L);
                task.setShuffleWriteTime(swMetrics.has("Shuffle Write Time") ? swMetrics.get("Shuffle Write Time").asLong() : 0L);
            }
        }
        batch.add(task);
    }

    private void handleAppEnd(JsonNode node, String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null) {
            app.setEndTime(parseTimestamp(node.get("Timestamp").asLong()));
            app.setDuration(java.time.Duration.between(app.getStartTime(), app.getEndTime()).toMillis());
            app.setStatus("FINISHED");
            applicationService.updateById(app);
        }
    }

    private void handleSqlStart(JsonNode node, String appId) {
        long executionId = node.get("executionId").asLong();
        SqlExecutionModel sql = new SqlExecutionModel();
        sql.setId(appId + ":" + executionId);
        sql.setAppId(appId);
        sql.setExecutionId(executionId);
        sql.setDescription(node.get("description").asText());
        sql.setDetails(node.get("details").asText());
        sql.setPhysicalPlan(node.get("physicalPlanDescription").asText());

        if (node.has("sparkPlanInfo")) {
            sql.setPlanInfo(node.get("sparkPlanInfo").toString());
        }

        sql.setStartTime(parseTimestamp(node.get("time").asLong()));
        sql.setStatus("RUNNING");
        sqlExecutionService.saveOrUpdate(sql);
    }

    private void handleSqlEnd(JsonNode node, String appId) {
        long executionId = node.get("executionId").asLong();
        SqlExecutionModel sql = sqlExecutionService.getById(appId + ":" + executionId);
        if (sql != null) {
            LocalDateTime endTime = parseTimestamp(node.get("time").asLong());
            sql.setEndTime(endTime);
            if (sql.getStartTime() != null && endTime != null) {
                sql.setDuration(java.time.Duration.between(sql.getStartTime(), endTime).toMillis());
            }
            sql.setStatus("SUCCEEDED"); // We don't easily have 'failed' here without more info
            sqlExecutionService.updateById(sql);
        }
    }

    private void handleBlockUpdated(JsonNode node, String appId) {
        JsonNode blockInfo = node.get("Block Updated Info");
        String blockId = blockInfo.get("Block ID").asText();
        if (blockId.startsWith("rdd_")) {
            // 解析 rdd_1_5 -> rddId=1
            String[] parts = blockId.split("_");
            int rddId = Integer.parseInt(parts[1]);

            StorageBlockModel block = new StorageBlockModel();
            block.setId(appId + ":" + rddId + ":" + blockId);
            block.setAppId(appId);
            block.setRddId(rddId);
            block.setBlockName(blockId);
            block.setStorageLevel(blockInfo.get("Storage Level").get("description").asText());
            block.setMemorySize(blockInfo.get("Memory Size").asLong());
            block.setDiskSize(blockInfo.get("Disk Size").asLong());
            block.setExecutorId(blockInfo.get("Block Manager ID").get("Executor ID").asText());
            block.setHost(blockInfo.get("Block Manager ID").get("Host").asText());

            dbExecutor.submit(() -> {
                try (java.sql.Connection connection = dataSource.getConnection();
                     java.sql.PreparedStatement ps = connection.prepareStatement(
                             "INSERT OR REPLACE INTO storage_blocks (id, app_id, rdd_id, block_name, storage_level, memory_size, disk_size, executor_id, host) " +
                                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, block.getId());
                    ps.setString(2, block.getAppId());
                    ps.setInt(3, block.getRddId());
                    ps.setString(4, block.getBlockName());
                    ps.setString(5, block.getStorageLevel());
                    ps.setLong(6, block.getMemorySize());
                    ps.setLong(7, block.getDiskSize());
                    ps.setString(8, block.getExecutorId());
                    ps.setString(9, block.getHost());
                    ps.executeUpdate();

                    // 核心修复：先确保 storage_rdds 表中有这一行
                    try (java.sql.PreparedStatement psInsert = connection.prepareStatement(
                            "INSERT OR IGNORE INTO storage_rdds (id, app_id, rdd_id, name, storage_level, num_partitions, num_cached_partitions) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        psInsert.setString(1, appId + ":" + rddId);
                        psInsert.setString(2, appId);
                        psInsert.setInt(3, rddId);
                        psInsert.setString(4, "RDD " + rddId); // 默认名称，后续可通过其他事件丰富
                        psInsert.setString(5, block.getStorageLevel());
                        psInsert.setInt(6, 0); // 暂不可知总分区数
                        psInsert.setInt(7, 0);
                        psInsert.executeUpdate();
                    }

                    // 顺便更新汇总表中的缓存分区计数和大小
                    updateRddSummary(appId, rddId);
                } catch (Exception exception) {
                    log.error("Failed to update block info", exception);
                }
            });
        }
    }

    private void updateRddSummary(String appId, int rddId) {
        // 在 DuckDB 中直接执行聚合更新
        String sql = "UPDATE storage_rdds SET " +
                "num_cached_partitions = (SELECT count(*) FROM storage_blocks WHERE app_id = ? AND rdd_id = ? AND (memory_size > 0 OR disk_size > 0)), " +
                "memory_size = (SELECT COALESCE(sum(memory_size), 0) FROM storage_blocks WHERE app_id = ? AND rdd_id = ?), " +
                "disk_size = (SELECT COALESCE(sum(disk_size), 0) FROM storage_blocks WHERE app_id = ? AND rdd_id = ?) " +
                "WHERE app_id = ? AND rdd_id = ?";
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setInt(2, rddId);
            ps.setString(3, appId);
            ps.setInt(4, rddId);
            ps.setString(5, appId);
            ps.setInt(6, rddId);
            ps.setString(7, appId);
            ps.setInt(8, rddId);
            ps.executeUpdate();
        } catch (Exception exception) {
            log.error("Failed to update RDD summary", exception);
        }
    }

    private void handleUnpersistRDD(JsonNode node, String appId) {
        int rddId = node.get("RDD ID").asInt();
        dbExecutor.submit(() -> {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM storage_blocks WHERE app_id = ? AND rdd_id = ?")) {
                    ps.setString(1, appId);
                    ps.setInt(2, rddId);
                    ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = connection.prepareStatement("DELETE FROM storage_rdds WHERE app_id = ? AND rdd_id = ?")) {
                    ps.setString(1, appId);
                    ps.setInt(2, rddId);
                    ps.executeUpdate();
                }
            } catch (Exception exception) {
                log.error("Failed to unpersist RDD", exception);
            }
        });
    }

    @Nullable
    private LocalDateTime parseTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), TimeZone.getDefault().toZoneId());
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = (ms / (1000 * 60 * 60));

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        if (hours == 0 && ms % 1000 > 0) {
            sb.append(" ").append(ms % 1000).append("ms");
        }
        return sb.toString();
    }

    @Override
    public boolean supports(String version) {
        return true;
    }

    private static class CountingInputStream extends FilterInputStream {
        private long bytesRead = 0;

        protected CountingInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public int read() throws IOException {
            int byteRead = super.read();
            if (byteRead != -1) {
                bytesRead++;
            }
            return byteRead;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int bytesReadNow = super.read(buffer, offset, length);
            if (bytesReadNow != -1) {
                bytesRead += bytesReadNow;
            }
            return bytesReadNow;
        }

        public long getBytesRead() {
            return bytesRead;
        }
    }
}
