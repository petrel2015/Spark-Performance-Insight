package com.spark.insight.parser;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationService applicationService;
    private final StageService stageService;
    private final TaskService taskService;
    private final EnvironmentConfigService envService;
    private final JobService jobService;
    private final ExecutorService executorService;

    public JacksonEventParser(ApplicationService applicationService, 
                              StageService stageService, 
                              TaskService taskService,
                              EnvironmentConfigService envService,
                              JobService jobService,
                              ExecutorService executorService) {
        this.applicationService = applicationService;
        this.stageService = stageService;
        this.taskService = taskService;
        this.envService = envService;
        this.jobService = jobService;
        this.executorService = executorService;
    }

    @Override
    public void parse(File logFile) {
        log.info("Processing log: {}", logFile.getName());
        try {
            InputStream is = new FileInputStream(logFile);
            if (logFile.getName().endsWith(".zstd") || logFile.getName().endsWith(".zst")) {
                is = new ZstdInputStream(is);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                String currentAppId = null;
                List<TaskModel> taskBatch = new ArrayList<>();
                List<EnvironmentConfigModel> envBatch = new ArrayList<>();
                List<ExecutorModel> executorBatch = new ArrayList<>();
                
                while ((line = reader.readLine()) != null) {
                    JsonNode node = objectMapper.readTree(line);
                    if (!node.has("Event")) continue;
                    String eventType = node.get("Event").asText();

                    // 尝试从环境更新中提取 App ID (针对 Spark V2 日志)
                    if (currentAppId == null && eventType.equals("SparkListenerEnvironmentUpdate")) {
                        JsonNode sparkProps = node.get("Spark Properties");
                        if (sparkProps != null && sparkProps.has("spark.app.id")) {
                            currentAppId = sparkProps.get("spark.app.id").asText();
                            log.info("Detected App ID from EnvironmentUpdate: {}", currentAppId);
                            
                            // Ensure App exists
                            if (applicationService.getById(currentAppId) == null) {
                                ApplicationModel app = new ApplicationModel();
                                app.setAppId(currentAppId);
                                app.setAppName(sparkProps.has("spark.app.name") ? sparkProps.get("spark.app.name").asText() : "Unknown App");
                                if (sparkProps.has("spark.user.name")) {
                                    app.setUserName(sparkProps.get("spark.user.name").asText());
                                } else if (node.has("User")) { // Fallback if User field exists in EnvUpdate (unlikely but possible)
                                    app.setUserName(node.get("User").asText());
                                } else {
                                    app.setUserName(System.getProperty("user.name", "unknown"));
                                }
                                app.setStartTime(parseTimestamp(System.currentTimeMillis())); // Approximate start time if event missing
                                app.setSparkVersion("unknown"); // Will be updated if AppStart or LogStart processed
                                applicationService.saveOrUpdate(app);
                            }
                        }
                    }

                    switch (eventType) {
                        case "SparkListenerApplicationStart":
                            currentAppId = node.get("App ID").asText();
                            handleAppStart(node, currentAppId);
                            break;

                        case "SparkListenerEnvironmentUpdate":
                            if (currentAppId != null) {
                                handleEnvUpdate(node, currentAppId, envBatch);
                            }
                            break;

                        case "SparkListenerJobStart":
                            if (currentAppId != null) handleJobStart(node, currentAppId);
                            break;

                        case "SparkListenerJobEnd":
                            if (currentAppId != null) handleJobEnd(node, currentAppId);
                            break;

                        case "SparkListenerExecutorAdded":
                            if (currentAppId != null) handleExecutorAdded(node, currentAppId, executorBatch);
                            break;

                        case "SparkListenerStageSubmitted":
                            if (currentAppId != null) handleStageSubmitted(node, currentAppId);
                            break;

                        case "SparkListenerStageCompleted":
                            if (currentAppId != null) handleStageCompleted(node, currentAppId);
                            break;

                        case "SparkListenerTaskEnd":
                            if (currentAppId != null) {
                                handleTaskEnd(node, currentAppId, taskBatch);
                                if (taskBatch.size() >= 1000) {
                                    saveDeduplicatedTasks(taskBatch);
                                    taskBatch.clear();
                                }
                            }
                            break;

                        case "SparkListenerApplicationEnd":
                            if (currentAppId != null) handleAppEnd(node, currentAppId);
                            break;
                    }
                }
                // 扫尾
                if (!taskBatch.isEmpty()) saveDeduplicatedTasks(taskBatch);
                if (!envBatch.isEmpty()) saveDeduplicatedEnv(envBatch);
                
                // 触发后期预计算
                if (currentAppId != null) {
                    log.info("Starting post-calculation for App: {}", currentAppId);
                    stageService.calculateStageMetrics(currentAppId);
                    jobService.calculateJobMetrics(currentAppId);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing " + logFile.getPath(), e);
        }
    }

    private void saveDeduplicatedTasks(List<TaskModel> batch) {
        Map<String, TaskModel> unique = new HashMap<>();
        for (TaskModel t : batch) unique.put(t.getId(), t);
        taskService.saveOrUpdateBatch(unique.values());
    }

    private void saveDeduplicatedEnv(List<EnvironmentConfigModel> batch) {
        Map<String, EnvironmentConfigModel> unique = new HashMap<>();
        for (EnvironmentConfigModel e : batch) unique.put(e.getId(), e);
        envService.upsertBatch(new ArrayList<>(unique.values()));
    }

    private void handleAppStart(JsonNode node, String appId) {
        ApplicationModel app = new ApplicationModel();
        app.setAppId(appId);
        app.setAppName(node.get("App Name").asText());
        app.setUserName(node.get("User").asText());
        app.setStartTime(parseTimestamp(node.get("Timestamp").asLong()));
        app.setSparkVersion(node.has("Spark Version") ? node.get("Spark Version").asText() : "unknown");
        applicationService.saveOrUpdate(app);
    }

    private void handleJobStart(JsonNode node, String appId) {
        int jobId = node.get("Job ID").asInt();
        JobModel job = new JobModel();
        job.setId(appId + ":" + jobId);
        job.setAppId(appId);
        job.setJobId(jobId);
        job.setSubmissionTime(parseTimestamp(node.get("Submission Time").asLong()));
        job.setStatus("RUNNING");
        
        if (node.has("Properties")) {
            JsonNode props = node.get("Properties");
            if (props.has("spark.job.description")) {
                job.setDescription(props.get("spark.job.description").asText());
            }
            if (props.has("spark.jobGroup.id")) {
                job.setJobGroup(props.get("spark.jobGroup.id").asText());
            }
        }
        
        JsonNode stageInfos = node.get("Stage Infos");
        if (stageInfos != null && stageInfos.isArray()) {
            job.setNumStages(stageInfos.size());
            List<String> sids = new ArrayList<>();
            int totalTasks = 0;
            for (JsonNode s : stageInfos) {
                sids.add(s.get("Stage ID").asText());
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
            job.setCompletionTime(parseTimestamp(node.get("Completion Time").asLong()));
            job.setStatus(node.get("Job Result").get("Result").asText().equals("JobSucceeded") ? "SUCCEEDED" : "FAILED");
            jobService.updateById(job);
        }
    }

    private void handleExecutorAdded(JsonNode node, String appId, List<ExecutorModel> batch) {
        String execId = node.get("Executor ID").asText();
        JsonNode info = node.get("Executor Info");
        ExecutorModel executor = new ExecutorModel();
        executor.setId(appId + ":" + execId);
        executor.setAppId(appId);
        executor.setExecutorId(execId);
        executor.setHost(info.get("Host").asText());
        executor.setTotalCores(info.get("Total Cores").asInt());
        executor.setMemory(info.has("Memory") ? info.get("Memory").asLong() : 0L);
        executor.setIsActive(true);
        executorService.saveOrUpdate(executor);
    }

    private void handleEnvUpdate(JsonNode node, String appId, List<EnvironmentConfigModel> batch) {
        JsonNode sparkConf = node.get("Spark Properties");
        if (sparkConf != null && sparkConf.isObject()) {
            sparkConf.fields().forEachRemaining(entry -> {
                EnvironmentConfigModel config = new EnvironmentConfigModel();
                config.setId(appId + ":" + entry.getKey());
                config.setAppId(appId);
                config.setParamKey(entry.getKey());
                config.setParamValue(entry.getValue().asText());
                config.setCategory("spark_conf");
                batch.add(config);
            });
        }
    }

    private void handleStageSubmitted(JsonNode node, String appId) {
        JsonNode info = node.get("Stage Info");
        int stageId = info.get("Stage ID").asInt();
        int attemptId = info.get("Stage Attempt ID").asInt();
        StageModel stage = new StageModel();
        stage.setId(appId + ":" + stageId + ":" + attemptId);
        stage.setAppId(appId);
        stage.setStageId(stageId);
        stage.setAttemptId(attemptId);
        stage.setStageName(info.get("Stage Name").asText());
        stage.setNumTasks(info.get("Number of Tasks").asInt());
        stage.setSubmissionTime(parseTimestamp(info.get("Submission Time").asLong()));
        stageService.saveOrUpdate(stage);
    }

    private void handleStageCompleted(JsonNode node, String appId) {
        JsonNode info = node.get("Stage Info");
        int stageId = info.get("Stage ID").asInt();
        int attemptId = info.get("Stage Attempt ID").asInt();
        StageModel stage = stageService.getById(appId + ":" + stageId + ":" + attemptId);
        if (stage != null) {
            if (info.has("Completion Time")) {
                stage.setCompletionTime(parseTimestamp(info.get("Completion Time").asLong()));
            }
            // 提取累加器指标（可选）
            stageService.updateById(stage);
        }
    }

    private void handleTaskEnd(JsonNode node, String appId, List<TaskModel> batch) {
        JsonNode info = node.get("Task Info");
        JsonNode metrics = node.get("Task Metrics");
        if (info == null || info.isNull()) return;

        int stageId = node.has("Stage ID") ? node.get("Stage ID").asInt() : -1;
        long taskId = info.has("Task ID") ? info.get("Task ID").asLong() : -1L;
        int taskIndex = info.has("Index") ? info.get("Index").asInt() : -1;
        
        TaskModel task = new TaskModel();
        task.setId(appId + ":" + stageId + ":" + taskId);
        task.setAppId(appId);
        task.setStageId(stageId);
        task.setTaskId(taskId);
        task.setTaskIndex(taskIndex);
        task.setExecutorId(info.has("Executor ID") ? info.get("Executor ID").asText() : "unknown");
        task.setHost(info.has("Host") ? info.get("Host").asText() : "unknown");
        task.setDuration(info.has("Duration") ? info.get("Duration").asLong() : 0L);
        task.setSpeculative(info.has("Speculative") ? info.get("Speculative").asBoolean() : false);
        task.setStatus(info.has("Status") ? info.get("Status").asText() : "unknown");

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

            long duration = task.getDuration();
            long schedulerDelay = Math.max(0L, duration - executorDeserializeTime - executorRunTime - resultSerializationTime - gettingResultTime);
            task.setSchedulerDelay(schedulerDelay);

            task.setGcTime(metrics.has("JVM GC Time") ? metrics.get("JVM GC Time").asLong() : 0L);
            task.setPeakExecutionMemory(metrics.has("Peak Execution Memory") ? metrics.get("Peak Execution Memory").asLong() : 0L);
            
            JsonNode inputMetrics = metrics.get("Input Metrics");
            if (inputMetrics != null && !inputMetrics.isNull()) {
                task.setInputBytes(inputMetrics.has("Bytes Read") ? inputMetrics.get("Bytes Read").asLong() : 0L);
                task.setInputRecords(inputMetrics.has("Records Read") ? inputMetrics.get("Records Read").asLong() : 0L);
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

    @Override
    public boolean supports(String version) { return true; }
}