package com.fluffyeti.spark.performance.insight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import com.fluffyeti.spark.performance.insight.mapper.EventLogScanMapper;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogWatcherService {

    private final SystemProperties properties;
    private final ApplicationService applicationService;
    private final EventLogScanMapper scanMapper;
    private final StatusBroadcaster broadcaster;
    private final ApplicationLogService logService;
    private final JdbcTemplate jdbcTemplate;
    
    private final BronzeIngestionService bronzeIngestionService;
    private final SilverTransformationService silverTransformationService;
    private final GoldAggregationService goldAggregationService;

    private final ExecutorService pipelineExecutor = Executors.newFixedThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern APP_ID_PATTERN = Pattern.compile("(spark-[a-zA-Z0-9-]+)");

    @Data
    public static class FileMetadata {
        private String name;
        private String md5;
        private long size;
        public FileMetadata() {}
        public FileMetadata(String name, String md5, long size) {
            this.name = name; this.md5 = md5; this.size = size;
        }
    }

    @Scheduled(fixedDelayString = "${insight.scheduler.scan-interval-seconds}000")
    public void scan() {
        if (!properties.getScheduler().isEnabled()) return;
        log.info(">>> Starting log directory scan...");
        File directory = new File(properties.getEventLogPath());
        if (!directory.exists() || !directory.isDirectory()) return;

        List<File> allFiles = new ArrayList<>();
        collectFiles(directory, allFiles);
        
        Map<String, List<File>> appGroups = new HashMap<>();
        for (File file : allFiles) {
            String appId = inferAppId(file.getName());
            if (appId != null) appGroups.computeIfAbsent(appId, k -> new ArrayList<>()).add(file);
        }

        appGroups.forEach((appId, files) -> {
            long totalSize = files.stream().mapToLong(File::length).sum();
            String compression = detectCompression(files);
            GoldApplicationModel existingApp = applicationService.getById(appId);
            if (existingApp == null) handleNewApp(appId, files, totalSize, compression);
            else handleExistingApp(existingApp, files, totalSize, compression);
        });
        log.info("<<< Log directory scan completed.");
    }

    private void handleNewApp(String appId, List<File> files, long totalSize, String compression) {
        logService.logEvent(appId, "SCAN", "New Application Detected", "Files: " + files.size());
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId(appId);
        app.setAppName("Unparsed Application");
        app.setParsingStatus("PENDING_LOAD");
        app.setTotalLogSize(totalSize);
        app.setCompressionFormat(compression);
        try {
            app.setSourceFileMetadata(objectMapper.writeValueAsString(generateMetadata(files)));
        } catch (Exception e) { log.error("Metadata error", e); }
        applicationService.save(app);
        broadcaster.broadcastStatus(appId, "PENDING_LOAD", 0.0, "Ready to import", app.getAppName(), null);
    }

    private void handleExistingApp(GoldApplicationModel app, List<File> files, long totalSize, String compression) {
        String currentStatus = app.getParsingStatus();
        if (List.of("INGESTING_BRONZE", "TRANSFORMING_SILVER", "AGGREGATING_GOLD").contains(currentStatus)) return;
        try {
            List<FileMetadata> currentMetadata = generateMetadata(files);
            boolean changed = app.getSourceFileMetadata() == null || !isMetadataEqual(currentMetadata, objectMapper.readValue(app.getSourceFileMetadata(), new TypeReference<List<FileMetadata>>() {}));
            if (changed && !"PENDING_REIMPORT".equals(currentStatus)) {
                app.setParsingStatus("PENDING_REIMPORT");
                app.setTotalLogSize(totalSize);
                app.setCompressionFormat(compression);
                applicationService.updateById(app);
                broadcaster.broadcastStatus(app.getAppId(), "PENDING_REIMPORT", 0.0, "Log files changed", app.getAppName(), null);
            }
        } catch (Exception e) { log.error("Check error", e); }
    }

    public void executePipeline(String appId, String type, java.util.function.Consumer<Boolean> onComplete) {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) { onComplete.accept(false); return; }
        List<File> allFiles = new ArrayList<>();
        collectFiles(new File(properties.getEventLogPath()), allFiles);
        List<File> appFiles = allFiles.stream().filter(f -> Objects.equals(inferAppId(f.getName()), appId)).sorted(Comparator.comparing(File::getName)).collect(Collectors.toList());
        
        app.setParsingStartTime(LocalDateTime.now());
        applicationService.updateById(app);

        pipelineExecutor.submit(() -> {
            if ("FULL".equals(type)) executeFullPipelineSync(appId, appFiles, onComplete);
            else if ("BRONZE_TO_GOLD".equals(type)) executeBronzeToGold(appId, appFiles, onComplete);
            else if ("SILVER_TO_GOLD".equals(type)) executeSilverToGold(appId, appFiles, onComplete);
            else executeFullPipelineSync(appId, appFiles, onComplete);
        });
    }

    public boolean executeFullPipelineSync(String appId, List<File> files) {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        executeFullPipelineSync(appId, files, (res) -> {
            success[0] = res;
            latch.countDown();
        });
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return success[0];
    }

    private void executeFullPipelineSync(String appId, List<File> files, java.util.function.Consumer<Boolean> onComplete) {
        try {
            logService.logEvent(appId, "IMPORT", "Bronze Start", "Starting pipeline");
            GoldApplicationModel app = applicationService.getById(appId);
            LocalDateTime start = LocalDateTime.now();
            
            bronzeIngestionService.ingest(appId, files, (p, m) -> updateStatus(appId, "INGESTING_BRONZE", p, m, start));
            logService.logEvent(appId, "IMPORT", "Bronze Finished", "Duration: " + getDurationSeconds(appId, "Bronze Start") + "s");

            silverTransformationService.transform(appId, (p, m) -> updateStatus(appId, "TRANSFORMING_SILVER", p, m, start));
            logService.logEvent(appId, "IMPORT", "Silver Finished", "Duration: " + getDurationSeconds(appId, "Silver Start") + "s");

            goldAggregationService.aggregate(appId, (p, m) -> updateStatus(appId, "AGGREGATING_GOLD", p, m, start));
            logService.logEvent(appId, "IMPORT", "Gold Finished", "Duration: " + getDurationSeconds(appId, "Gold Start") + "s");

            finalizeSuccess(appId, files, app);
            onComplete.accept(true);
        } catch (Exception e) {
            handleFailure(appId, e);
            onComplete.accept(false);
        }
    }

    private void executeBronzeToGold(String appId, List<File> files, java.util.function.Consumer<Boolean> onComplete) {
        try {
            LocalDateTime start = LocalDateTime.now();
            silverTransformationService.transform(appId, (p, m) -> updateStatus(appId, "TRANSFORMING_SILVER", p, m, start));
            goldAggregationService.aggregate(appId, (p, m) -> updateStatus(appId, "AGGREGATING_GOLD", p, m, start));
            finalizeSuccess(appId, files, applicationService.getById(appId));
            onComplete.accept(true);
        } catch (Exception e) { handleFailure(appId, e); onComplete.accept(false); }
    }

    private void executeSilverToGold(String appId, List<File> files, java.util.function.Consumer<Boolean> onComplete) {
        try {
            LocalDateTime start = LocalDateTime.now();
            goldAggregationService.aggregate(appId, (p, m) -> updateStatus(appId, "AGGREGATING_GOLD", p, m, start));
            finalizeSuccess(appId, files, applicationService.getById(appId));
            onComplete.accept(true);
        } catch (Exception e) { handleFailure(appId, e); onComplete.accept(false); }
    }

    private void finalizeSuccess(String appId, List<File> files, GoldApplicationModel app) throws Exception {
        if (app == null) return;
        app.setParsingStatus("SUCCESS");
        app.setParsingProgressValue(100.0);
        app.setParsingProgress("Pipeline completed successfully.");
        app.setSourceFileMetadata(objectMapper.writeValueAsString(generateMetadata(files)));
        app.setParsingEndTime(LocalDateTime.now());
        applicationService.updateById(app);
        broadcaster.broadcastStatus(appId, "SUCCESS", 100.0, "Completed", app.getAppName(), app.getParsingStartTime());
        logService.logEvent(appId, "SUCCESS", "Pipeline Finished", "App data fully processed.");
    }

    private void handleFailure(String appId, Exception e) {
        log.error("Pipeline failed: " + appId, e);
        applicationService.lambdaUpdate().eq(GoldApplicationModel::getAppId, appId).set(GoldApplicationModel::getParsingEndTime, LocalDateTime.now()).update();
        updateStatus(appId, "FAILED", 0.0, "Error: " + e.getMessage(), null);
        logService.logEvent(appId, "FAILED", "Pipeline Error", e.getMessage());
    }

    private void updateStatus(String appId, String status, double progress, String msg, LocalDateTime start) {
        applicationService.updateStatusAtomic(appId, status, progress, String.format("%.0f%% %s", progress, msg), start);
        GoldApplicationModel app = applicationService.getById(appId);
        broadcaster.broadcastStatus(appId, status, progress, msg, app != null ? app.getAppName() : null, (start != null ? start : (app != null ? app.getParsingStartTime() : null)));
    }

    private String getDurationSeconds(String appId, String eventName) {
        try {
            java.sql.Timestamp time = jdbcTemplate.queryForObject("SELECT created_at FROM sys_application_logs WHERE app_id = ? AND event_name = ? ORDER BY created_at DESC LIMIT 1", java.sql.Timestamp.class, appId, eventName);
            return time != null ? String.valueOf((System.currentTimeMillis() - time.getTime()) / 1000.0) : "0";
        } catch (Exception e) { return "0"; }
    }

    private List<FileMetadata> generateMetadata(List<File> files) {
        return files.stream().sorted(Comparator.comparing(File::getName)).map(f -> new FileMetadata(f.getName(), calculateMD5(f), f.length())).collect(Collectors.toList());
    }

    private boolean isMetadataEqual(List<FileMetadata> l1, List<FileMetadata> l2) {
        if (l1.size() != l2.size()) return false;
        for (int i = 0; i < l1.size(); i++) {
            if (!l1.get(i).getName().equals(l2.get(i).getName()) || !l1.get(i).getMd5().equals(l2.get(i).getMd5()) || l1.get(i).getSize() != l2.get(i).getSize()) return false;
        }
        return true;
    }

    private String calculateMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return ""; }
    }

    private void collectFiles(File file, List<File> result) {
        if (file.isDirectory()) {
            if (file.getName().startsWith(".") || file.getName().endsWith(".crc")) return;
            File[] files = file.listFiles();
            if (files != null) for (File f : files) collectFiles(f, result);
        } else if (file.isFile() && isValidLogFile(file)) result.add(file);
    }

    public String inferAppId(String filename) {
        if (filename == null) return null;
        String name = filename.replace(".zstd", "").replace(".lz4", "").replace(".gz", "");
        java.util.regex.Matcher m = APP_ID_PATTERN.matcher(name);
        if (m.find()) return m.group(1);
        if (name.startsWith("event_")) {
            String[] p = name.split("_", 3);
            return p.length >= 3 ? p[2] : null;
        }
        if (name.startsWith("application_")) return name.split("\\.")[0];
        return null;
    }

    private boolean isValidLogFile(File file) {
        String n = file.getName();
        return !n.startsWith(".") && !n.endsWith(".crc") && (n.startsWith("event") || n.startsWith("application_"));
    }

    private String detectCompression(List<File> files) {
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".zstd")) return "ZSTD";
            if (n.endsWith(".lz4")) return "LZ4";
            if (n.endsWith(".gz")) return "GZIP";
        }
        return "None";
    }
}
