package com.spark.insight.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.spark.insight.model.*;
import com.spark.insight.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class JacksonEventParser implements EventParser {

    private static final java.util.regex.Pattern APP_ID_PATTERN = java.util.regex.Pattern.compile("(spark-[a-zA-Z0-9\\-]+)");

    private final ObjectMapper objectMapper;
    private final ApplicationService applicationService;
    private final StageService stageService;
    private final TaskService taskService;
    private final EnvironmentConfigService envService;
    private final JobService jobService;
    private final ExecutorService executorService;
    private final javax.sql.DataSource dataSource;
    private final java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(); // Use Virtual Threads for IO

    public JacksonEventParser(ApplicationService applicationService,
                              StageService stageService,
                              TaskService taskService,
                              EnvironmentConfigService envService,
                              JobService jobService,
                              ExecutorService executorService,
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
        this.dataSource = dataSource;
    }

    @Override
    public void parse(File logFile) {
        parse(logFile, 1, 1);
    }

    @Override
    public void parse(File logFile, int currentFileIndex, int totalFiles) {
        long startTime = System.currentTimeMillis();
        log.info("Processing log: {} ({}/{})", logFile.getName(), currentFileIndex, totalFiles);

        // 尝试从文件名推断 App ID (支持滚动日志)
        String inferredAppId = null;
        if (logFile.getName().startsWith("event")) {
            String[] parts = logFile.getName().split("_", 3);
            if (parts.length >= 3) {
                inferredAppId = parts[2];
            }
        }
        if (inferredAppId == null) {
            java.util.regex.Matcher matcher = APP_ID_PATTERN.matcher(logFile.getName());
            if (matcher.find()) {
                inferredAppId = matcher.group(1);
            }
        }

        try {
            InputStream is = new FileInputStream(logFile);
            if (logFile.getName().endsWith(".zstd") || logFile.getName().endsWith(".zst")) {
                is = new ZstdInputStream(is);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                String currentAppId = inferredAppId;
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
                        updateParsingProgress(currentAppId, currentFileIndex, totalFiles, lineCount);
                        lastUpdate = System.currentTimeMillis();
                    }

                    try {
                        JsonNode node = objectMapper.readTree(line);
                        if (!node.has("Event")){
                            continue;
                        }
                        String eventType = node.get("Event").asText();

                        // 尝试从环境更新中提取 App ID
                        if (currentAppId == null && eventType.equals("SparkListenerEnvironmentUpdate")) {
                            JsonNode sparkProps = node.get("Spark Properties");
                            if (sparkProps != null && sparkProps.has("spark.app.id")) {
                                currentAppId = sparkProps.get("spark.app.id").asText();
                                log.info("Detected App ID from EnvironmentUpdate: {}", currentAppId);

                                ApplicationModel app = applicationService.getById(currentAppId);
                                if (app == null) {
                                    app = new ApplicationModel();
                                    app.setAppId(currentAppId);
                                    app.setAppName(sparkProps.has("spark.app.name") ? sparkProps.get("spark.app.name").asText() : "Unknown App");
                                    app.setUserName(sparkProps.has("spark.user.name") ? sparkProps.get("spark.user.name").asText() : "unknown");
                                    app.setStartTime(parseTimestamp(System.currentTimeMillis()));
                                    app.setSparkVersion(versionFromLogStart != null ? versionFromLogStart : "unknown");
                                    app.setParsingStatus("PARSING");
                                    updateParsingProgress(app, currentFileIndex, totalFiles, lineCount);
                                    applicationService.saveOrUpdate(app);
                                } else if (versionFromLogStart != null && (app.getSparkVersion() == null || app.getSparkVersion().equals("unknown"))) {
                                    app.setSparkVersion(versionFromLogStart);
                                    app.setParsingStatus("PARSING");
                                    updateParsingProgress(app, currentFileIndex, totalFiles, lineCount);
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
                                handleAppStart(node, currentAppId, currentFileIndex, totalFiles);
                                break;
                            case "SparkListenerEnvironmentUpdate":
                                if (currentAppId != null) {
                                    handleEnvUpdate(node, currentAppId, envBatch);
                                }
                                break;
                            case "SparkListenerJobStart":
                                if (currentAppId != null) handleJobStart(node, currentAppId, stageToJobMap);
                                break;
                            case "SparkListenerJobEnd":
                                if (currentAppId != null) handleJobEnd(node, currentAppId);
                                break;
                            case "SparkListenerExecutorAdded":
                                if (currentAppId != null) handleExecutorAdded(node, currentAppId, executorBatch);
                                break;
                            case "SparkListenerExecutorRemoved":
                                if (currentAppId != null) handleExecutorRemoved(node, currentAppId);
                                break;
                            case "SparkListenerStageSubmitted":
                                if (currentAppId != null) handleStageSubmitted(node, currentAppId, stageToJobMap);
                                break;
                            case "SparkListenerStageCompleted":
                                if (currentAppId != null) handleStageCompleted(node, currentAppId);
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
                                if (currentAppId != null) handleAppEnd(node, currentAppId);
                                break;
                        }
                    } catch (Exception lineEx) {
                        log.warn("Failed to parse line in {}: {}", logFile.getName(), lineEx.getMessage());
                    }
                }
                // 扫尾
                if (!taskBatch.isEmpty()) {
                    List<TaskModel> batchToSave = new ArrayList<>(taskBatch);
                    dbExecutor.submit(() -> saveDeduplicatedTasks(batchToSave));
                }
                if (!envBatch.isEmpty()) saveDeduplicatedEnv(envBatch);

                // 触发后期预计算
                if (currentAppId != null) {
                    final String appIdFinal = currentAppId;
                    dbExecutor.submit(() -> {
                        log.info("Starting post-calculation for App: {}", appIdFinal);
                        stageService.calculateStageMetrics(appIdFinal);
                        jobService.calculateJobMetrics(appIdFinal);
                        executorService.calculateExecutorMetrics(appIdFinal);

                        // Finalize Data Quality Check
                        finalizeAppQuality(appIdFinal);
                    });
                }
            }
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Finished processing log: {} in {}", logFile.getName(), formatDuration(durationMs));
        } catch (Exception e) {
            log.error("Error parsing " + logFile.getPath(), e);
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
                app.setParsingStatus("READY");
                isUpdated = true;
            }

            if (isUpdated) {
                applicationService.updateById(app);
                log.info("Updated App Data Quality for {}: Status={}, ParsingStatus=READY", appId, app.getDataQualityStatus());
            }
        }
    }

    private void updateParsingProgress(String appId, int fileIdx, int totalFiles, long lineCount) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null) {
            updateParsingProgress(app, fileIdx, totalFiles, lineCount);
            applicationService.updateById(app);
        }
    }

    private void updateParsingProgress(ApplicationModel app, int fileIdx, int totalFiles, long lineCount) {
        String msg = String.format("Processing file %d/%d (Lines processed: %d)", fileIdx, totalFiles, lineCount);
        app.setParsingProgress(msg);
    }

    private void saveDeduplicatedTasks(List<TaskModel> batch) {
        Map<String, TaskModel> unique = new HashMap<>();
        for (TaskModel t : batch) unique.put(t.getId(), t);
        
        try {
            fastBatchInsertTasks(new ArrayList<>(unique.values()));
        } catch (Exception e) {
            log.error("Fast batch insert failed, falling back to Service saveBatch. Error: {}", e.getMessage());
            taskService.saveOrUpdateBatch(unique.values());
        }
    }

    private void fastBatchInsertTasks(List<TaskModel> tasks) throws java.sql.SQLException {
        if (tasks.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO tasks (" +
                "id, app_id, stage_id, attempt_id, task_id, task_index, executor_id, host, " +
                "launch_time, finish_time, duration, gc_time, scheduler_delay, getting_result_time, " +
                "executor_deserialize_time, executor_run_time, result_serialization_time, executor_cpu_time, " +
                "peak_execution_memory, input_bytes, input_records, output_bytes, output_records, " +
                "memory_bytes_spilled, disk_bytes_spilled, shuffle_read_bytes, shuffle_read_records, " +
                "shuffle_fetch_wait_time, shuffle_write_bytes, shuffle_write_time, shuffle_write_records, " +
                "shuffle_remote_read, speculative, status, locality" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false); // Optimize for batch

            for (TaskModel t : tasks) {
                int idx = 1;
                ps.setString(idx++, t.getId());
                ps.setString(idx++, t.getAppId());
                ps.setInt(idx++, t.getStageId());
                ps.setInt(idx++, t.getAttemptId());
                ps.setLong(idx++, t.getTaskId());
                ps.setInt(idx++, t.getTaskIndex());
                ps.setString(idx++, t.getExecutorId());
                ps.setString(idx++, t.getHost());
                ps.setLong(idx++, t.getLaunchTime());
                ps.setLong(idx++, t.getFinishTime());
                ps.setLong(idx++, t.getDuration());
                ps.setLong(idx++, t.getGcTime());
                ps.setLong(idx++, t.getSchedulerDelay());
                ps.setLong(idx++, t.getGettingResultTime());
                ps.setLong(idx++, t.getExecutorDeserializeTime());
                ps.setLong(idx++, t.getExecutorRunTime());
                ps.setLong(idx++, t.getResultSerializationTime());
                ps.setLong(idx++, t.getExecutorCpuTime());
                ps.setLong(idx++, t.getPeakExecutionMemory());
                ps.setLong(idx++, t.getInputBytes());
                ps.setLong(idx++, t.getInputRecords());
                ps.setLong(idx++, t.getOutputBytes());
                ps.setLong(idx++, t.getOutputRecords());
                ps.setLong(idx++, t.getMemoryBytesSpilled());
                ps.setLong(idx++, t.getDiskBytesSpilled());
                ps.setLong(idx++, t.getShuffleReadBytes());
                ps.setLong(idx++, t.getShuffleReadRecords());
                ps.setLong(idx++, t.getShuffleFetchWaitTime());
                ps.setLong(idx++, t.getShuffleWriteBytes());
                ps.setLong(idx++, t.getShuffleWriteTime());
                ps.setLong(idx++, t.getShuffleWriteRecords());
                ps.setLong(idx++, t.getShuffleRemoteRead());
                ps.setBoolean(idx++, t.getSpeculative());
                ps.setString(idx++, t.getStatus());
                ps.setString(idx++, t.getLocality());
                
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();
        }
    }

    private void saveDeduplicatedEnv(List<EnvironmentConfigModel> batch) {
        Map<String, EnvironmentConfigModel> unique = new HashMap<>();
        for (EnvironmentConfigModel e : batch) unique.put(e.getId(), e);
        envService.upsertBatch(new ArrayList<>(unique.values()));
    }

    private void handleAppStart(JsonNode node, String appId, int fileIdx, int totalFiles) {
        ApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            app = new ApplicationModel();
            app.setAppId(appId);
        }
        app.setParsingStatus("PARSING");
        updateParsingProgress(app, fileIdx, totalFiles, 0);
        app.setAppName(node.get("App Name").asText());
        app.setUserName(node.get("User").asText());
        app.setStartTime(parseTimestamp(node.get("Timestamp").asLong()));

        if (app.getSparkVersion() == null || app.getSparkVersion().equals("unknown")) {
            app.setSparkVersion(node.has("Spark Version") ? node.get("Spark Version").asText() : "unknown");
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
            for (JsonNode s : stageInfos) {
                int sid = s.get("Stage ID").asInt();
                sids.add(String.valueOf(sid));
                stageToJobMap.put(sid, jobId);
                if (s.has("Number of Tasks")) {
                    totalTasks += s.get("Number of Tasks").asInt();
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
        extractProps(node, "Spark Properties", "spark_conf", appId, batch);
        extractProps(node, "JVM Information", "jvm_info", appId, batch);
        extractProps(node, "Hadoop Properties", "hadoop_conf", appId, batch);
        extractProps(node, "System Properties", "system_props", appId, batch);
        extractProps(node, "Metrics Properties", "metrics_props", appId, batch);
        extractProps(node, "Classpath Entries", "classpath_entries", appId, batch);
    }

    private void extractProps(JsonNode node, String fieldName, String category, String appId, List<EnvironmentConfigModel> batch) {
        JsonNode props = node.get(fieldName);
        if (props != null && props.isObject()) {
            props.fields().forEachRemaining(entry -> {
                EnvironmentConfigModel config = new EnvironmentConfigModel();
                config.setId(appId + ":" + category + ":" + entry.getKey());
                config.setAppId(appId);
                config.setParamKey(entry.getKey());
                config.setParamValue(entry.getValue().asText());
                config.setCategory(category);
                batch.add(config);
            });
        }
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
                List<String> pIds = new ArrayList<>();
                for (JsonNode p : parents) {
                    pIds.add(p.asText());
                }
                stage.setParentStageIds(String.join(",", pIds));
            }
        }

        if (info.has("RDD Info")) {
            stage.setRddInfo(info.get("RDD Info").toString());
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
        if (info == null || info.isNull()) return;

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

    private LocalDateTime parseTimestamp(long timestamp) {
        if (timestamp <= 0) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), TimeZone.getDefault().toZoneId());
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        long s = (ms / 1000) % 60;
        long m = (ms / (1000 * 60)) % 60;
        long h = (ms / (1000 * 60 * 60));

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        if (h == 0 && ms % 1000 > 0) {
            sb.append(" ").append(ms % 1000).append("ms");
        }
        return sb.toString();
    }

    @Override
    public boolean supports(String version) {
        return true;
    }
}