package com.spark.insight.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.spark.insight.mapper.ParsedEventLogMapper;
import com.spark.insight.model.*;
import com.spark.insight.service.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.duckdb.DuckDBAppender;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class JacksonEventParser implements EventParser {

    private final ObjectMapper objectMapper;
    private final ApplicationService applicationService;
    private final StageService stageService;
    private final TaskService taskService;
    private final EnvironmentConfigService envService;
    private final JobService jobService;
    private final SparkExecutorService sparkExecutorService;
    private final SqlExecutionService sqlExecutionService;
    private final StorageService storageService;
    private final ParsedEventLogMapper parsedLogMapper;
    private final StatusBroadcaster broadcaster;
    private final DataSource dataSource;
    private final DuckDBAppenderRegistry appenderRegistry;
    private final ApplicationLogService logService;

    private final BlockingQueue<EventEnvelope> rawQueue = new LinkedBlockingQueue<>(100000);
    private final BlockingQueue<Object> writeQueue = new LinkedBlockingQueue<>(100000);

    private Thread writerThread;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, AppParsingContext> activeApps = new ConcurrentHashMap<>();

    public JacksonEventParser(ApplicationService applicationService,
                              StageService stageService,
                              TaskService taskService,
                              EnvironmentConfigService envService,
                              JobService jobService,
                              SparkExecutorService sparkExecutorService,
                              SqlExecutionService sqlExecutionService,
                              StorageService storageService,
                              ParsedEventLogMapper parsedLogMapper,
                              StatusBroadcaster broadcaster,
                              DataSource dataSource,
                              DuckDBAppenderRegistry appenderRegistry,
                              ApplicationLogService logService) {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();
        this.objectMapper = new ObjectMapper(factory);
        this.applicationService = applicationService;
        this.stageService = stageService;
        this.taskService = taskService;
        this.envService = envService;
        this.jobService = jobService;
        this.sparkExecutorService = sparkExecutorService;
        this.sqlExecutionService = sqlExecutionService;
        this.storageService = storageService;
        this.parsedLogMapper = parsedLogMapper;
        this.broadcaster = broadcaster;
        this.dataSource = dataSource;
        this.appenderRegistry = appenderRegistry;
        this.logService = logService;
    }

    @PostConstruct
    public void init() {
        writerThread = new Thread(this::writerLoop, "duckdb-writer");
        writerThread.start();

        int consumerCount = Math.max(2, Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < consumerCount; i++) {
            Thread.ofVirtual().name("event-consumer-" + i).start(this::consumerLoop);
        }

        scheduler.scheduleAtFixedRate(this::broadcastAllProgress, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }

    @Override
    public void parse(File logFile, String appId) throws InterruptedException {
        parseFiles(Collections.singletonList(logFile), appId);
    }

    @Override
    public void parseFiles(List<File> logFiles, String appId) throws InterruptedException {
        long totalSize = logFiles.stream().mapToLong(File::length).sum();
        log.info("Starting to parse {} files for app {}, total size: {}", logFiles.size(), appId, formatFileSize(totalSize));
        logService.logEvent(appId, "PARSE", "Parsing Started", String.format("Parsing %d files, total size: %s", logFiles.size(), formatFileSize(totalSize)));
        long startTime = System.currentTimeMillis();
        AppParsingContext context = new AppParsingContext(appId, totalSize);
        activeApps.put(appId, context);

        context.activeProducers.set(logFiles.size());
        for (File file : logFiles) {
            Thread.ofVirtual().start(() -> {
                try {
                    readAndEnqueue(file, appId, context);
                } catch (Exception e) {
                    log.error("Error reading file: " + file.getName(), e);
                    logService.logEvent(appId, "ERROR", "File Read Error", "Failed to read " + file.getName() + ": " + e.getMessage());
                } finally {
                    if (context.activeProducers.decrementAndGet() == 0) {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("Finished reading all log files for app {} in {}ms", appId, duration);
                        logService.logEvent(appId, "PARSE", "Parsing Finished", String.format("Completed file reading in %dms", duration));
                        checkAndSendEoa(context);
                    }
                }
            });
        }
    }

    private void checkAndSendEoa(AppParsingContext context) {
        if (context.activeProducers.get() == 0 && context.pendingTasks.get() == 0) {
            if (context.eoaSent.compareAndSet(false, true)) {
                try {
                    writeQueue.put(EventEnvelope.eoa(context.appId));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void readAndEnqueue(File logFile, String appId, AppParsingContext context) throws IOException, InterruptedException {
        InputStream inputStream = new FileInputStream(logFile);
        if (logFile.getName().endsWith(".zstd") || logFile.getName().endsWith(".zst")) {
            inputStream = new ZstdInputStream(inputStream);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                context.bytesRead.addAndGet(line.length() + 1);
                context.pendingTasks.incrementAndGet();
                rawQueue.put(EventEnvelope.data(appId, line));
            }
        }
        rawQueue.put(EventEnvelope.eof(appId, logFile.getName()));
    }

    private void consumerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                EventEnvelope envelope = rawQueue.take();
                if (envelope.getType() == EventEnvelope.Type.DATA) {
                    processLine(envelope);
                    AppParsingContext context = activeApps.get(envelope.getAppId());
                    if (context != null) {
                        context.pendingTasks.decrementAndGet();
                        checkAndSendEoa(context);
                    }
                } else {
                    writeQueue.put(envelope);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in consumer loop", e);
            }
        }
    }

    private void processLine(EventEnvelope envelope) {
        try {
            JsonNode node = objectMapper.readTree(envelope.getContent());
            if (!node.has("Event")) return;
            String eventType = node.get("Event").asText();
            String appId = envelope.getAppId();
            AppParsingContext context = activeApps.get(appId);
            if (context == null) return;

            switch (eventType) {
                case "SparkListenerLogStart":
                    if (node.has("Spark Version")) context.sparkVersion = node.get("Spark Version").asText();
                    break;
                case "SparkListenerApplicationStart":
                    enqueue(handleAppStart(node, appId, context.sparkVersion));
                    break;
                case "SparkListenerEnvironmentUpdate":
                    List<EnvironmentConfigModel> envConfigs = new ArrayList<>();
                    handleEnvUpdate(node, appId, envConfigs);
                    for (EnvironmentConfigModel config : envConfigs) enqueue(config);
                    break;
                case "SparkListenerJobStart":
                    enqueue(handleJobStart(node, appId, context.stageToJobMap));
                    break;
                case "SparkListenerJobEnd":
                    enqueue(handleJobEnd(node, appId));
                    break;
                case "SparkListenerExecutorAdded":
                    enqueue(handleExecutorAdded(node, appId));
                    break;
                case "SparkListenerExecutorRemoved":
                    enqueue(handleExecutorRemoved(node, appId));
                    break;
                case "SparkListenerStageSubmitted":
                    enqueue(handleStageSubmitted(node, appId, context.stageToJobMap));
                    break;
                case "SparkListenerStageCompleted":
                    enqueue(handleStageCompleted(node, appId));
                    break;
                case "SparkListenerTaskEnd":
                    enqueue(handleTaskEnd(node, appId));
                    break;
                case "SparkListenerApplicationEnd":
                    enqueue(handleAppEnd(node, appId));
                    break;
                case "org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionStart":
                    enqueue(handleSqlStart(node, appId));
                    break;
                case "org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionEnd":
                    enqueue(handleSqlEnd(node, appId));
                    break;
                case "SparkListenerBlockUpdated":
                    enqueue(handleBlockUpdated(node, appId));
                    break;
                case "SparkListenerUnpersistRDD":
                    enqueue(new UnpersistRDDEvent(appId, node.get("RDD ID").asInt()));
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to parse event line", e);
        }
    }

    private void enqueue(Object item) throws InterruptedException {
        if (item != null) {
            writeQueue.put(item);
        }
    }

    private void writerLoop() {
        try (Connection conn = dataSource.getConnection()) {
            DuckDBAppender taskAppender = null;
            DuckDBAppender envAppender = null;
            DuckDBAppender blockAppender = null;
            long taskWriteCount = 0;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Object item = writeQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (item == null) {
                        if (taskAppender != null) { taskAppender.close(); taskAppender = null; }
                        if (envAppender != null) { envAppender.close(); envAppender = null; }
                        if (blockAppender != null) { blockAppender.close(); blockAppender = null; }
                        continue;
                    }

                    if (item instanceof EventEnvelope sig) {
                        if (sig.getType() == EventEnvelope.Type.EOF) {
                            handleFileSuccess(sig.getFileName(), sig.getAppId());
                        } else if (sig.getType() == EventEnvelope.Type.EOA) {
                            log.info("EOA received for {}, closing appenders and committing...", sig.getAppId());
                            if (taskAppender != null) { taskAppender.close(); taskAppender = null; }
                            if (envAppender != null) { envAppender.close(); envAppender = null; }
                            if (blockAppender != null) { blockAppender.close(); blockAppender = null; }
                            
                            // Force checkpoint/commit in DuckDB to ensure data visibility
                            try (java.sql.Statement st = conn.createStatement()) {
                                st.execute("CHECKPOINT");
                            }
                            
                            finalizeApp(sig.getAppId(), conn);
                            taskWriteCount = 0;
                        }
                        continue;
                    }

                    String appId = getAppIdFromModel(item);
                    if (appId != null) {
                        AppParsingContext ctx = activeApps.get(appId);
                        if (ctx != null && ctx.cleaned.compareAndSet(false, true)) {
                            appenderRegistry.cleanApp(conn, appId);
                        }
                    }

                    if (item instanceof TaskModel task) {
                        if (taskAppender == null) { taskAppender = appenderRegistry.createAppender(conn, "tasks"); }
                        try {
                            appenderRegistry.writeTask(taskAppender, task);
                            taskWriteCount++;
                            if (taskWriteCount % 5000 == 0) {
                                log.info("Appended {} tasks for current batch", taskWriteCount);
                            }
                        } catch (Exception e) {
                            log.error("Task write failed, resetting appender", e);
                            taskAppender.close(); taskAppender = null;
                        }
                    } else if (item instanceof EnvironmentConfigModel env) {
                        if (envAppender == null) { envAppender = appenderRegistry.createAppender(conn, "environment_configs"); }
                        try {
                            appenderRegistry.writeEnvConfig(envAppender, env);
                        } catch (Exception e) {
                            log.error("EnvConfig write failed, resetting appender", e);
                            envAppender.close(); envAppender = null;
                        }
                    } else if (item instanceof StorageBlockModel block) {
                        if (blockAppender == null) { blockAppender = appenderRegistry.createAppender(conn, "storage_blocks"); }
                        try {
                            ensureRddExists(conn, block.getAppId(), block.getRddId(), block.getStorageLevel());
                            appenderRegistry.writeStorageBlock(blockAppender, block);
                            updateRddSummary(conn, block.getAppId(), block.getRddId());
                        } catch (Exception e) {
                            log.error("StorageBlock write failed, resetting appender", e);
                            blockAppender.close(); blockAppender = null;
                        }
                    } else if (item instanceof ApplicationModel app) {
                        applicationService.saveOrUpdate(app);
                    } else if (item instanceof JobModel job) {
                        jobService.saveOrUpdate(job);
                    } else if (item instanceof StageModel stage) {
                        stageService.saveOrUpdate(stage);
                    } else if (item instanceof ExecutorModel exec) {
                        sparkExecutorService.saveOrUpdate(exec);
                    } else if (item instanceof SqlExecutionModel sql) {
                        sqlExecutionService.saveOrUpdate(sql);
                    } else if (item instanceof UnpersistRDDEvent unpersist) {
                        handleUnpersistRDD(conn, unpersist.appId, unpersist.rddId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Writer loop error", e);
                }
            }
        } catch (SQLException e) {
            log.error("Fatal: Writer thread connection failed", e);
        }
    }

    private String getAppIdFromModel(Object item) {
        if (item instanceof TaskModel m) return m.getAppId();
        if (item instanceof EnvironmentConfigModel m) return m.getAppId();
        if (item instanceof StorageBlockModel m) return m.getAppId();
        if (item instanceof ApplicationModel m) return m.getAppId();
        if (item instanceof JobModel m) return m.getAppId();
        if (item instanceof StageModel m) return m.getAppId();
        if (item instanceof ExecutorModel m) return m.getAppId();
        if (item instanceof SqlExecutionModel m) return m.getAppId();
        if (item instanceof UnpersistRDDEvent m) return m.appId;
        return null;
    }

    private void finalizeApp(String appId, Connection conn) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Starting finalization for App: {}", appId);
            logService.logEvent(appId, "FINALIZE", "Finalization Started", "Starting metric aggregation and pre-calculations");
            
            long stepStart = System.currentTimeMillis();
            fixStageJobIds(appId, conn);
            log.info("Step [Fix Stage Job IDs] finished in {}ms", System.currentTimeMillis() - stepStart);

            updatePostCalculationProgress(appId, "Aggregating metrics...", 84.0);

            // Log task count for verification using the provided connection
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM tasks WHERE app_id = ?")) {
                ps.setString(1, appId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long count = rs.getLong(1);
                        log.info("Finalizing App: {}, Task count in DB: {}", appId, count);
                        logService.logEvent(appId, "FINALIZE", "Data Verification", "Task count in DB: " + count);
                    }
                }
            }

            stepStart = System.currentTimeMillis();
            stageService.calculateStageMetrics(appId);
            log.info("Step [Stage Metrics Aggregation] finished in {}ms", System.currentTimeMillis() - stepStart);
            
            updatePostCalculationProgress(appId, "Aggregating metrics...", 88.0);
            
            stepStart = System.currentTimeMillis();
            jobService.calculateJobMetrics(appId);
            log.info("Step [Job Metrics Aggregation] finished in {}ms", System.currentTimeMillis() - stepStart);
            
            updatePostCalculationProgress(appId, "Aggregating metrics...", 92.0);
            
            stepStart = System.currentTimeMillis();
            sqlExecutionService.calculateSqlMetrics(appId);
            log.info("Step [SQL Metrics Aggregation] finished in {}ms", System.currentTimeMillis() - stepStart);
            
            updatePostCalculationProgress(appId, "Aggregating metrics...", 96.0);
            
            stepStart = System.currentTimeMillis();
            sparkExecutorService.calculateExecutorMetrics(appId);
            log.info("Step [Executor Metrics Aggregation] finished in {}ms", System.currentTimeMillis() - stepStart);
            
            updatePostCalculationProgress(appId, "Aggregating metrics...", 98.0);
            
            stepStart = System.currentTimeMillis();
            applicationService.updateAppMetrics(appId);
            log.info("Step [App Metrics Update] finished in {}ms", System.currentTimeMillis() - stepStart);
            
            finalizeAppQuality(appId);
            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("App {} finalization complete. Total time: {}ms", appId, totalDuration);
            logService.logEvent(appId, "SUCCESS", "Analysis Complete", String.format("Total finalization time: %dms", totalDuration));
            broadcaster.broadcastStatus(appId, "SUCCESS", 100.0, "Analysis complete.");
        } catch (Exception e) {
            log.error("Finalization failed for " + appId, e);
            logService.logEvent(appId, "FAILED", "Analysis Failed", e.getMessage());
            broadcaster.broadcastStatus(appId, "FAILED", 100.0, "Analysis failed.");
        } finally {
            activeApps.remove(appId);
        }
    }

    private void fixStageJobIds(String appId, Connection conn) throws SQLException {
        String sql = "UPDATE stages s SET job_id = (SELECT j.job_id FROM jobs j WHERE j.app_id = s.app_id AND j.stage_ids LIKE '%' || CAST(s.stage_id AS VARCHAR) || '%' LIMIT 1) WHERE s.app_id = ? AND s.job_id IS NULL";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.executeUpdate();
        }
    }

    private void handleFileSuccess(String fileName, String appId) {
        ParsedEventLogModel logRecord = parsedLogMapper.selectById(fileName);
        if (logRecord != null) {
            logRecord.setStatus(EventLogStatus.SUCCESS);
            parsedLogMapper.updateById(logRecord);
        }
    }

    private void broadcastAllProgress() {
        activeApps.forEach((appId, context) -> {
            long total = context.totalLogSize;
            long read = context.bytesRead.get();
            double rawPercentage = total > 0 ? (read * 100.0 / total) : 0;
            double mappedPercentage = Math.max(1.0, Math.min(80.0, rawPercentage * 0.8));
            String progressText = String.format("Parsing: %s / %s (%.1f%%)", formatFileSize(read), formatFileSize(total), rawPercentage);
            
            broadcaster.broadcastStatus(appId, "LOADING", mappedPercentage, progressText);
            
            // Sync to database
            ApplicationModel app = context.applicationModel;
            app.setParsingProgress(progressText);
            enqueueIgnoreError(app);
        });
    }

    private void enqueueIgnoreError(Object item) {
        try {
            if (item != null) writeQueue.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ApplicationModel handleAppStart(JsonNode node, String appId, String version) {
        AppParsingContext context = activeApps.get(appId);
        if (context == null) return null;
        ApplicationModel app = context.applicationModel;
        app.setAppName(node.get("App Name").asText());
        app.setUserName(node.get("User").asText());
        app.setStartTime(parseTimestamp(node.get("Timestamp").asLong()));
        app.setSparkVersion(node.has("Spark Version") ? node.get("Spark Version").asText() : version);
        app.setParsingStatus("LOADING");
        return app;
    }

    private JobModel handleJobStart(JsonNode node, String appId, Map<Integer, Integer> stageToJobMap) {
        int jobId = node.get("Job ID").asInt();
        JobModel job = new JobModel();
        job.setId(appId + ":" + jobId);
        job.setAppId(appId);
        job.setJobId(jobId);
        job.setSubmissionTime(parseTimestamp(node.get("Submission Time").asLong()));
        job.setStatus("RUNNING");
        if (node.has("Properties")) {
            JsonNode properties = node.get("Properties");
            String description = properties.has("spark.job.description") ? properties.get("spark.job.description").asText() : properties.path("spark.job.callSite").asText();
            job.setDescription(description.split("\n")[0]);
            if (properties.has("spark.sql.execution.id")) job.setSqlExecutionId(properties.get("spark.sql.execution.id").asLong());
        }
        JsonNode stages = node.get("Stage Infos");
        if (stages != null && stages.isArray()) {
            List<String> stageIds = new ArrayList<>();
            for (JsonNode stageInfo : stages) {
                int stageId = stageInfo.get("Stage ID").asInt();
                stageIds.add(String.valueOf(stageId));
                stageToJobMap.put(stageId, jobId);
            }
            job.setStageIds(String.join(",", stageIds));
            job.setNumStages(stages.size());
        }
        return job;
    }

    private TaskModel handleTaskEnd(JsonNode node, String appId) {
        JsonNode info = node.get("Task Info");
        if (info == null || info.isNull()) {
            log.warn("Missing 'Task Info' in SparkListenerTaskEnd for app: {}", appId);
            return null;
        }
        if (log.isTraceEnabled()) {
            log.trace("Parsed TaskEnd: tid={}, app={}", info.path("Task ID").asLong(-1), appId);
        }
        JsonNode metrics = node.get("Task Metrics");
        int stageId = node.path("Stage ID").asInt(-1);
        long taskId = info.path("Task ID").asLong(-1);
        
        TaskModel task = new TaskModel();
        task.setId(appId + ":" + stageId + ":" + taskId);
        task.setAppId(appId);
        task.setStageId(stageId);
        task.setAttemptId(node.path("Stage Attempt ID").asInt(0));
        task.setTaskId(taskId);
        task.setTaskIndex(info.path("Index").asInt(-1));
        task.setExecutorId(info.path("Executor ID").asText("unknown"));
        task.setHost(info.path("Host").asText("unknown"));
        task.setLaunchTime(info.path("Launch Time").asLong(0));
        task.setFinishTime(info.path("Finish Time").asLong(0));
        task.setDuration(task.getFinishTime() - task.getLaunchTime());
        task.setStatus(node.path("Task End Reason").path("Reason").asText("unknown").equals("Success") ? "SUCCESS" : "FAILED");
        task.setLocality(info.path("Locality").asText("unknown"));
        task.setSpeculative(info.path("Speculative").asBoolean(false));

        if (metrics != null && !metrics.isNull()) {
            task.setExecutorDeserializeTime(metrics.path("Executor Deserialize Time").asLong(0));
            task.setExecutorRunTime(metrics.path("Executor Run Time").asLong(0));
            task.setResultSerializationTime(metrics.path("Result Serialization Time").asLong(0));
            task.setExecutorCpuTime(metrics.path("Executor CPU Time").asLong(0));
            task.setGcTime(metrics.path("JVM GC Time").asLong(0));
            task.setPeakExecutionMemory(metrics.path("Peak Execution Memory").asLong(0));
            task.setMemoryBytesSpilled(metrics.path("Memory Bytes Spilled").asLong(0));
            task.setDiskBytesSpilled(metrics.path("Disk Bytes Spilled").asLong(0));
            
            JsonNode input = metrics.get("Input Metrics");
            if (input != null) {
                task.setInputBytes(input.path("Bytes Read").asLong(0));
                task.setInputRecords(input.path("Records Read").asLong(0));
            }
            
            JsonNode output = metrics.get("Output Metrics");
            if (output != null) {
                task.setOutputBytes(output.path("Bytes Written").asLong(0));
                task.setOutputRecords(output.path("Records Written").asLong(0));
            }

            JsonNode shuffleRead = metrics.get("Shuffle Read Metrics");
            if (shuffleRead != null) {
                long remoteBytes = shuffleRead.path("Remote Bytes Read").asLong(0);
                task.setShuffleReadBytes(remoteBytes + shuffleRead.path("Local Bytes Read").asLong(0));
                task.setShuffleRemoteRead(remoteBytes);
                task.setShuffleReadRecords(shuffleRead.path("Total Records Read").asLong(0));
                task.setShuffleFetchWaitTime(shuffleRead.path("Fetch Wait Time").asLong(0));
            }
            JsonNode shuffleWrite = metrics.get("Shuffle Write Metrics");
            if (shuffleWrite != null) {
                task.setShuffleWriteBytes(shuffleWrite.path("Shuffle Bytes Written").asLong(0));
                task.setShuffleWriteRecords(shuffleWrite.path("Shuffle Records Written").asLong(0));
                task.setShuffleWriteTime(shuffleWrite.path("Shuffle Write Time").asLong(0));
            }
        }
        return task;
    }

    private StageModel handleStageSubmitted(JsonNode node, String appId, Map<Integer, Integer> stageToJobMap) {
        JsonNode info = node.get("Stage Info");
        int stageId = info.get("Stage ID").asInt();
        StageModel stage = new StageModel();
        stage.setId(appId + ":" + stageId + ":" + info.get("Stage Attempt ID").asInt());
        stage.setAppId(appId);
        stage.setStageId(stageId);
        stage.setJobId(stageToJobMap.get(stageId));
        stage.setStageName(info.get("Stage Name").asText());
        stage.setNumTasks(info.get("Number of Tasks").asInt());
        stage.setSubmissionTime(parseTimestamp(info.get("Submission Time").asLong()));
        stage.setStatus("RUNNING");

        if (info.has("Parent IDs")) {
            JsonNode parents = info.get("Parent IDs");
            if (parents.isArray() && !parents.isEmpty()) {
                List<String> parentIds = new ArrayList<>();
                for (JsonNode parentNode : parents) {
                    parentIds.add(parentNode.asText());
                }
                stage.setParentStageIds(String.join(",", parentIds));
            }
        }

        if (info.has("RDD Info")) {
            stage.setRddInfo(info.get("RDD Info").toString());
        }

        return stage;
    }

    private ExecutorModel handleExecutorAdded(JsonNode node, String appId) {
        String executorId = node.get("Executor ID").asText();
        JsonNode info = node.get("Executor Info");
        ExecutorModel executor = new ExecutorModel();
        executor.setId(appId + ":" + executorId);
        executor.setAppId(appId);
        executor.setExecutorId(executorId);
        executor.setHost(info.get("Host").asText());
        executor.setAddTime(parseTimestamp(node.get("Timestamp").asLong()));
        executor.setTotalCores(info.get("Total Cores").asInt());
        executor.setMemory(info.path("Memory").asLong(0));
        executor.setIsActive(true);
        return executor;
    }

    private SqlExecutionModel handleSqlStart(JsonNode node, String appId) {
        long executionId = node.get("executionId").asLong();
        SqlExecutionModel sql = new SqlExecutionModel();
        sql.setId(appId + ":" + executionId);
        sql.setAppId(appId);
        sql.setExecutionId(executionId);
        sql.setDescription(node.get("description").asText());
        sql.setDetails(node.path("details").asText(""));
        sql.setPhysicalPlan(node.path("physicalPlanDescription").asText(""));
        if (node.has("sparkPlanInfo")) {
            sql.setPlanInfo(node.get("sparkPlanInfo").toString());
        }
        sql.setStartTime(parseTimestamp(node.get("time").asLong()));
        sql.setStatus("RUNNING");
        return sql;
    }


    private void handleEnvUpdate(JsonNode node, String appId, List<EnvironmentConfigModel> batch) {
        extractProps(node, "Spark Properties", "spark_conf", appId, batch);
        extractProps(node, "JVM Information", "jvm_info", appId, batch);
        extractProps(node, "Hadoop Properties", "hadoop_conf", appId, batch);
        extractProps(node, "System Properties", "system_props", appId, batch);
    }

    private void extractProps(JsonNode node, String f, String cat, String appId, List<EnvironmentConfigModel> batch) {
        JsonNode p = node.get(f);
        if (p != null && p.isObject()) {
            p.fields().forEachRemaining(e -> {
                EnvironmentConfigModel c = new EnvironmentConfigModel();
                c.setId(appId + ":" + cat + ":" + e.getKey());
                c.setAppId(appId);
                c.setParamKey(e.getKey());
                c.setParamValue(e.getValue().asText());
                c.setCategory(cat);
                batch.add(c);
            });
        }
    }

    private Object handleBlockUpdated(JsonNode node, String appId) {
        JsonNode info = node.get("Block Updated Info");
        String blockId = info.get("Block ID").asText();
        if (blockId.startsWith("rdd_")) {
            String[] parts = blockId.split("_");
            int rddId = Integer.parseInt(parts[1]);
            StorageBlockModel block = new StorageBlockModel();
            block.setId(appId + ":" + rddId + ":" + blockId);
            block.setAppId(appId);
            block.setRddId(rddId);
            block.setBlockName(blockId);
            block.setStorageLevel(info.path("Storage Level").path("description").asText("unknown"));
            block.setMemorySize(info.get("Memory Size").asLong());
            block.setDiskSize(info.get("Disk Size").asLong());
            block.setExecutorId(info.path("Block Manager ID").path("Executor ID").asText("unknown"));
            block.setHost(info.path("Block Manager ID").path("Host").asText("unknown"));
            return block;
        }
        return null;
    }

    private void handleUnpersistRDD(Connection conn, String appId, int rid) throws SQLException {
        try (java.sql.PreparedStatement ps = conn.prepareStatement("DELETE FROM storage_blocks WHERE app_id = ? AND rdd_id = ?")) {
            ps.setString(1, appId);
            ps.setInt(2, rid);
            ps.executeUpdate();
        }
    }

    private void updateRddSummary(Connection conn, String appId, int rddId) throws SQLException {
        String sql = "UPDATE storage_rdds SET " +
                "num_cached_partitions = (SELECT count(*) FROM storage_blocks WHERE app_id = ? AND rdd_id = ? AND (memory_size > 0 OR disk_size > 0)), " +
                "memory_size = (SELECT COALESCE(sum(memory_size), 0) FROM storage_blocks WHERE app_id = ? AND rdd_id = ?), " +
                "disk_size = (SELECT COALESCE(sum(disk_size), 0) FROM storage_blocks WHERE app_id = ? AND rdd_id = ?) " +
                "WHERE app_id = ? AND rdd_id = ?";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId); ps.setInt(2, rddId);
            ps.setString(3, appId); ps.setInt(4, rddId);
            ps.setString(5, appId); ps.setInt(6, rddId);
            ps.setString(7, appId); ps.setInt(8, rddId);
            ps.executeUpdate();
        }
    }

    private void ensureRddExists(Connection conn, String appId, int rddId, String storageLevel) throws SQLException {
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO storage_rdds (id, app_id, rdd_id, name, storage_level, num_partitions, num_cached_partitions) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, appId + ":" + rddId);
            ps.setString(2, appId);
            ps.setInt(3, rddId);
            ps.setString(4, "RDD " + rddId);
            ps.setString(5, storageLevel);
            ps.setInt(6, 0);
            ps.setInt(7, 0);
            ps.executeUpdate();
        }
    }

    private void finalizeAppQuality(String appId) {
        AppParsingContext context = activeApps.get(appId);
        if (context == null) return;
        ApplicationModel app = context.applicationModel;
        if (app.getEndTime() == null) {
            app.setDataQualityStatus("INCOMPLETE");
            app.setDataQualityNote("Missing ApplicationEnd event.");
        } else {
            app.setDataQualityStatus("GOOD");
        }
        app.setParsingStatus("SUCCESS");
        app.setParsingProgress("100%");
        applicationService.updateById(app);
    }

    private record UnpersistRDDEvent(String appId, int rddId) {}
    private record RddSummaryUpdateEvent(String appId, int rddId) {}

    @Override public boolean supports(String v) { return true; }

    private String formatFileSize(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private LocalDateTime parseTimestamp(long t) {
        return t <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault());
    }

    private void updatePostCalculationProgress(String id, String s, double p) {
        broadcaster.broadcastStatus(id, "PRE_CALCULATING", p, s);
    }

    private JobModel handleJobEnd(JsonNode node, String appId) {
        int jobId = node.get("Job ID").asInt();
        JobModel job = jobService.getById(appId + ":" + jobId);
        if (job != null) {
            job.setCompletionTime(parseTimestamp(node.get("Completion Time").asLong()));
            job.setStatus(node.get("Job Result").get("Result").asText().equals("JobSucceeded") ? "SUCCEEDED" : "FAILED");
        }
        return job;
    }

    private StageModel handleStageCompleted(JsonNode node, String appId) {
        JsonNode info = node.get("Stage Info");
        int stageId = info.get("Stage ID").asInt();
        int attemptId = info.get("Stage Attempt ID").asInt();
        StageModel stage = stageService.getById(appId + ":" + stageId + ":" + attemptId);
        if (stage == null) {
            stage = new StageModel();
            stage.setId(appId + ":" + stageId + ":" + attemptId);
            stage.setAppId(appId);
            stage.setStageId(stageId);
            stage.setAttemptId(attemptId);
        }
        stage.setCompletionTime(parseTimestamp(info.path("Completion Time").asLong(0)));
        stage.setStatus(info.has("Failure Reason") ? "FAILED" : "SUCCEEDED");
        
        // Extract metrics from Accumulables if available (sometimes tasks report 0 but stage totals are here)
        JsonNode accumulables = info.get("Accumulables");
        if (accumulables != null && accumulables.isArray()) {
            for (JsonNode accumulator : accumulables) {
                String name = accumulator.path("Name").asText("");
                long value = accumulator.path("Value").asLong(0);
                switch (name) {
                    case "internal.metrics.input.bytesRead": stage.setInputBytes(value); break;
                    case "internal.metrics.input.recordsRead": stage.setInputRecords(value); break;
                    case "internal.metrics.output.bytesWritten": stage.setOutputBytes(value); break;
                    case "internal.metrics.output.recordsWritten": stage.setOutputRecords(value); break;
                    case "internal.metrics.shuffle.read.remoteBytesRead":
                    case "internal.metrics.shuffle.read.localBytesRead":
                        stage.setShuffleReadBytes(stage.getShuffleReadBytes() + value); break;
                    case "internal.metrics.shuffle.read.recordsRead": stage.setShuffleReadRecords(value); break;
                    case "internal.metrics.shuffle.write.bytesWritten": stage.setShuffleWriteBytes(value); break;
                    case "internal.metrics.shuffle.write.recordsWritten": stage.setShuffleWriteRecords(value); break;
                }
            }
        }
        return stage;
    }

    private ApplicationModel handleAppEnd(JsonNode node, String appId) {
        AppParsingContext context = activeApps.get(appId);
        if (context == null) return null;
        ApplicationModel app = context.applicationModel;
        app.setEndTime(parseTimestamp(node.get("Timestamp").asLong()));
        app.setStatus("FINISHED");
        return app;
    }

    private SqlExecutionModel handleSqlEnd(JsonNode node, String appId) {
        long executionId = node.get("executionId").asLong();
        SqlExecutionModel sql = sqlExecutionService.getById(appId + ":" + executionId);
        if (sql != null) {
            sql.setEndTime(parseTimestamp(node.get("time").asLong()));
            sql.setStatus("SUCCEEDED");
        }
        return sql;
    }

    private ExecutorModel handleExecutorRemoved(JsonNode node, String appId) {
        String executorId = node.get("Executor ID").asText();
        ExecutorModel executor = sparkExecutorService.getById(appId + ":" + executorId);
        if (executor != null) {
            executor.setRemoveTime(parseTimestamp(node.get("Timestamp").asLong()));
            executor.setIsActive(false);
        }
        return executor;
    }

    private static class AppParsingContext {
        final String appId;
        final long totalLogSize;
        final AtomicLong bytesRead = new AtomicLong(0);
        final AtomicInteger activeProducers = new AtomicInteger(0);
        final AtomicLong pendingTasks = new AtomicLong(0);
        final AtomicBoolean eoaSent = new AtomicBoolean(false);
        final AtomicBoolean cleaned = new AtomicBoolean(false);
        final Map<Integer, Integer> stageToJobMap = new ConcurrentHashMap<>();
        volatile String sparkVersion = "unknown";
        final ApplicationModel applicationModel = new ApplicationModel();

        AppParsingContext(String id, long s) {
            this.appId = id;
            this.totalLogSize = s;
            this.applicationModel.setAppId(id);
            this.applicationModel.setParsingStatus("LOADING");
            this.applicationModel.setParsingProgress("0%");
        }
    }
}
