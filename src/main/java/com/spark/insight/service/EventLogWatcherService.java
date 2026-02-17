package com.spark.insight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.insight.config.InsightProperties;
import com.spark.insight.mapper.EventLogScanMapper;
import com.spark.insight.model.GoldApplicationModel;
import com.spark.insight.model.EventLogStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogWatcherService {

    private final InsightProperties properties;
    private final ApplicationService applicationService;
    private final EventLogScanMapper scanMapper;
    private final StatusBroadcaster broadcaster;
    private final ApplicationLogService logService;
    
    private final BronzeIngestionService bronzeIngestionService;
    private final SilverTransformationService silverTransformationService;
    private final GoldAggregationService goldAggregationService;

    private final ExecutorService pipelineExecutor = Executors.newFixedThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Using [0-9] instead of \d to avoid escaping hell
    private static final Pattern APP_ID_PATTERN = Pattern.compile("(spark-[a-zA-Z0-9-]+)");
    private static final Pattern INDEX_PATTERN = Pattern.compile("[-_]([0-9]+)[-_]");

    @Data
    public static class FileMetadata {
        private String name;
        private String md5;
        private long size;

        public FileMetadata() {}

        public FileMetadata(String name, String md5, long size) {
            this.name = name;
            this.md5 = md5;
            this.size = size;
        }
    }

    @Scheduled(fixedDelayString = "${insight.scheduler.scan-interval-seconds:10}000")
    public void scan() {
        if (!properties.getScheduler().isEnabled()) {
            return;
        }

        String logPath = properties.getEventLogPath();
        File directory = new File(logPath);
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        List<File> allFiles = new ArrayList<>();
        collectFiles(directory, allFiles);

        Map<String, List<File>> appGroups = new HashMap<>();
        for (File file : allFiles) {
            String appId = inferAppId(file.getName());
            if (appId != null) {
                appGroups.computeIfAbsent(appId, k -> new ArrayList<>()).add(file);
            }
        }

        appGroups.forEach((appId, files) -> {
            long totalSize = files.stream().mapToLong(File::length).sum();
            GoldApplicationModel existingApp = applicationService.getById(appId);

            if (existingApp == null) {
                handleNewApp(appId, files, totalSize);
            } else {
                handleExistingApp(existingApp, files, totalSize);
            }
        });
    }

    private void handleNewApp(String appId, List<File> files, long totalSize) {
        logService.logEvent(appId, "SCAN", "New Application Detected", 
                String.format("Found %d files, total size: %.2f MB", files.size(), totalSize / (1024.0 * 1024.0)));
        
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId(appId);
        app.setAppName("Unparsed Application"); // Will be updated during processing
        app.setParsingStatus("PENDING_LOAD");
        app.setTotalLogSize(totalSize);
        
        try {
            List<FileMetadata> metadataList = generateMetadata(files);
            app.setSourceFileMetadata(objectMapper.writeValueAsString(metadataList));
        } catch (Exception e) {
            log.error("Failed to generate metadata for app: " + appId, e);
        }

        applicationService.save(app);
        broadcaster.broadcastStatus(appId, "PENDING_LOAD", 0.0, "Ready to import", app.getAppName(), app.getParsingStartTime());
    }

    private void handleExistingApp(GoldApplicationModel app, List<File> files, long totalSize) {
        String currentStatus = app.getParsingStatus();
        // Skip if currently processing
        if ("INGESTING_BRONZE".equals(currentStatus) || 
            "TRANSFORMING_SILVER".equals(currentStatus) || 
            "AGGREGATING_GOLD".equals(currentStatus)) {
            return;
        }

        // Check if files changed
        try {
            List<FileMetadata> currentMetadata = generateMetadata(files);
            String storedMetadataJson = app.getSourceFileMetadata();
            
            boolean changed = false;
            if (storedMetadataJson == null || storedMetadataJson.isEmpty()) {
                // Backward compatibility: if no metadata stored, assume changed if it was previously SUCCESS/FAILED
                // or maybe we should just update the metadata if it matches the files?
                // For safety, let's treat null metadata as "changed" if we have files now.
                changed = true;
            } else {
                List<FileMetadata> storedMetadata = objectMapper.readValue(storedMetadataJson, new TypeReference<List<FileMetadata>>() {});
                if (!isMetadataEqual(currentMetadata, storedMetadata)) {
                    changed = true;
                }
            }

            if (changed && !"PENDING_REIMPORT".equals(currentStatus)) {
                logService.logEvent(app.getAppId(), "SCAN", "Log File Changed", "File content or list changed.");
                app.setParsingStatus("PENDING_REIMPORT");
                // Update metadata to current? No, keep old metadata until re-import? 
                // Plan says "Update status PENDING_REIMPORT". 
                // We should probably NOT update the source_file_metadata in DB yet, 
                // so we know what was the "original" vs "current". 
                // But wait, if we don't update it, next scan will still see it as changed.
                // Actually, the `scan()` runs periodically. If we update status to PENDING_REIMPORT, 
                // we don't need to keep updating it. 
                
                applicationService.updateById(app);
                broadcaster.broadcastStatus(app.getAppId(), "PENDING_REIMPORT", 0.0, "Log files changed", app.getAppName(), app.getParsingStartTime());
            }

        } catch (Exception e) {
            log.error("Error checking existing app: " + app.getAppId(), e);
        }
    }

    public void executePipeline(String appId, String type, java.util.function.Consumer<Boolean> onComplete) {
        // Resolve files
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            log.error("App not found for pipeline execution: {}", appId);
            onComplete.accept(false);
            return;
        }

        // Initialize start time for UI duration calculations
        app.setParsingStartTime(LocalDateTime.now());
        applicationService.updateById(app);
        
        // Find files again (as paths might have changed or we just need them)
        // Ideally we should use the paths from metadata, but scanning dir is safer for now
        String logPath = properties.getEventLogPath();
        File directory = new File(logPath);
        List<File> allFiles = new ArrayList<>();
        collectFiles(directory, allFiles);
        List<File> appFiles = allFiles.stream()
                .filter(f -> Objects.equals(inferAppId(f.getName()), appId))
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());

        if (appFiles.isEmpty()) {
            log.error("No files found for app: {}", appId);
            handleFailure(appId, new RuntimeException("No files found"));
            onComplete.accept(false);
            return;
        }

        if ("FULL".equals(type)) {
            executeFullPipeline(appId, appFiles, onComplete);
        } else if ("BRONZE_TO_GOLD".equals(type)) {
            executeBronzeToGold(appId, appFiles, onComplete);
        } else if ("SILVER_TO_GOLD".equals(type)) {
            executeSilverToGold(appId, appFiles, onComplete);
        } else {
            log.warn("Unknown pipeline type: {}", type);
            executeFullPipeline(appId, appFiles, onComplete);
        }
    }

    private void executeFullPipeline(String appId, List<File> files, java.util.function.Consumer<Boolean> onComplete) {
        logService.logEvent(appId, "IMPORT", "Bronze Start", "Starting Medallion pipeline (FULL)");
        
        pipelineExecutor.submit(() -> {
            try {
                // Bronze (0-100%)
                LocalDateTime bronzeStart = LocalDateTime.now();
                updateStatus(appId, "INGESTING_BRONZE", 0.0, "Starting ingestion...", bronzeStart);
                long t0 = System.currentTimeMillis();
                bronzeIngestionService.ingest(appId, files, (p, msg) -> {
                    updateStatus(appId, "INGESTING_BRONZE", p, msg, null);
                });
                logService.logEvent(appId, "IMPORT", "Bronze Finished", String.format("Duration: %.2fs", (System.currentTimeMillis() - t0) / 1000.0));

                // Silver (0-100%)
                LocalDateTime silverStart = LocalDateTime.now();
                updateStatus(appId, "TRANSFORMING_SILVER", 0.0, "Structuring data...", silverStart);
                long t1 = System.currentTimeMillis();
                logService.logEvent(appId, "TRANSFORM", "Silver Start", "Structuring data");
                silverTransformationService.transform(appId, (p, msg) -> {
                    updateStatus(appId, "TRANSFORMING_SILVER", p, msg, null);
                });
                logService.logEvent(appId, "TRANSFORM", "Silver Finished", String.format("Duration: %.2fs", (System.currentTimeMillis() - t1) / 1000.0));

                // Gold (0-100%)
                LocalDateTime goldStart = LocalDateTime.now();
                updateStatus(appId, "AGGREGATING_GOLD", 0.0, "Aggregating metrics...", goldStart);
                long t2 = System.currentTimeMillis();
                logService.logEvent(appId, "AGGREGATE", "Gold Start", "Aggregating metrics");
                goldAggregationService.aggregate(appId, (p, msg) -> {
                    updateStatus(appId, "AGGREGATING_GOLD", p, msg, null);
                });
                logService.logEvent(appId, "AGGREGATE", "Gold Finished", String.format("Duration: %.2fs", (System.currentTimeMillis() - t2) / 1000.0));
                
                // Success
                finalizeSuccess(appId, files);
                onComplete.accept(true);
                
            } catch (Exception e) {
                handleFailure(appId, e);
                onComplete.accept(false);
            }
        });
    }

    private void executeBronzeToGold(String appId, List<File> files, java.util.function.Consumer<Boolean> onComplete) {
        logService.logEvent(appId, "TRANSFORM", "Silver Start", "Re-running pipeline from Silver (using existing Bronze)");
        
        pipelineExecutor.submit(() -> {
            try {
                // Silver (0-100%)
                LocalDateTime silverStart = LocalDateTime.now();
                updateStatus(appId, "TRANSFORMING_SILVER", 0.0, "Structuring data...", silverStart);
                long t1 = System.currentTimeMillis();
                silverTransformationService.transform(appId, (p, msg) -> {
                    updateStatus(appId, "TRANSFORMING_SILVER", p, msg, null);
                });
                logService.logEvent(appId, "TRANSFORM", "Silver Finished", String.format("Duration: %.2fs", (System.currentTimeMillis() - t1) / 1000.0));

                // Gold (0-100%)
                LocalDateTime goldStart = LocalDateTime.now();
                updateStatus(appId, "AGGREGATING_GOLD", 0.0, "Aggregating metrics...", goldStart);
                long t2 = System.currentTimeMillis();
                logService.logEvent(appId, "AGGREGATE", "Gold Start", "Aggregating metrics");
                goldAggregationService.aggregate(appId, (p, msg) -> {
                    updateStatus(appId, "AGGREGATING_GOLD", p, msg, null);
                });
                logService.logEvent(appId, "AGGREGATE", "Gold Finished", String.format("Duration: %.2fs", (System.currentTimeMillis() - t2) / 1000.0));
                
                finalizeSuccess(appId, files);
                onComplete.accept(true);
            } catch (Exception e) {
                handleFailure(appId, e);
                onComplete.accept(false);
            }
        });
    }

    private void executeSilverToGold(String appId, List<File> files, java.util.function.Consumer<Boolean> onComplete) {
        logService.logEvent(appId, "AGGREGATE", "Gold Start", "Re-running pipeline from Gold (using existing Silver)");
        
        pipelineExecutor.submit(() -> {
            try {
                // Gold
                LocalDateTime goldStart = LocalDateTime.now();
                updateStatus(appId, "AGGREGATING_GOLD", 0.0, "Aggregating metrics...", goldStart);
                long t2 = System.currentTimeMillis();
                goldAggregationService.aggregate(appId, (p, msg) -> {
                    updateStatus(appId, "AGGREGATING_GOLD", p, msg, null);
                });
                logService.logEvent(appId, "AGGREGATE", "Gold Finished", String.format("Duration: %.2fs", (System.currentTimeMillis() - t2) / 1000.0));
                
                finalizeSuccess(appId, files);
                onComplete.accept(true);
            } catch (Exception e) {
                handleFailure(appId, e);
                onComplete.accept(false);
            }
        });
    }

    private void resetParsingStartTime(String appId) {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app != null) {
            app.setParsingStartTime(LocalDateTime.now());
            applicationService.updateById(app);
        }
    }

    private void finalizeSuccess(String appId, List<File> files) throws Exception {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) return;

        // Update metadata to reflect the files we just successfully processed
        List<FileMetadata> metadata = generateMetadata(files);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        
        // 1. Update the local object fields to match final state
        app.setParsingStatus("SUCCESS");
        app.setParsingProgressValue(100.0);
        app.setParsingProgress("Pipeline completed successfully.");
        app.setSourceFileMetadata(metadataJson);
        
        // 2. Use updateById to save everything (including metadata and final status) in one go
        applicationService.updateById(app);
        
        // 3. Broadcast final status via WebSocket
        // Use the latest app name which might have been updated during pipeline
        broadcaster.broadcastStatus(appId, "SUCCESS", 100.0, "Pipeline completed successfully.", app.getAppName(), app.getParsingStartTime());
        logService.logEvent(appId, "SUCCESS", "Pipeline Finished", "App data fully processed.");
    }

    private void handleFailure(String appId, Exception e) {
        log.error("Medallion pipeline failed for app: {}", appId, e);
        String errorMsg = e.getMessage();
        if (e.getCause() != null) {
            errorMsg += " (Cause: " + e.getCause().getMessage() + ")";
        }
        updateStatus(appId, "FAILED", 0.0, "Error: " + errorMsg, null);
        logService.logEvent(appId, "FAILED", "Pipeline Error", errorMsg);
    }

    private void updateStatus(String appId, String status, double progress, String msg, LocalDateTime startTime) {
        String progressText = String.format("%.0f%% %s", progress, msg);
        
        // Persist to DB immediately in a new transaction
        applicationService.updateStatusAtomic(appId, status, progress, progressText, startTime);
        
        // Broadcast via WebSocket
        GoldApplicationModel app = applicationService.getById(appId);
        broadcaster.broadcastStatus(appId, status, progress, msg, app != null ? app.getAppName() : null, app != null ? app.getParsingStartTime() : null);
    }

    private List<FileMetadata> generateMetadata(List<File> files) {
        return files.stream()
                .sorted(Comparator.comparing(File::getName))
                .map(f -> new FileMetadata(f.getName(), calculateMD5(f), f.length()))
                .collect(Collectors.toList());
    }

    private boolean isMetadataEqual(List<FileMetadata> list1, List<FileMetadata> list2) {
        if (list1.size() != list2.size()) return false;
        for (int i = 0; i < list1.size(); i++) {
            FileMetadata m1 = list1.get(i);
            FileMetadata m2 = list2.get(i);
            if (!Objects.equals(m1.getName(), m2.getName()) || 
                !Objects.equals(m1.getMd5(), m2.getMd5()) || 
                m1.getSize() != m2.getSize()) {
                return false;
            }
        }
        return true;
    }

    private String calculateMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MD5 for " + file.getName(), e);
            return "";
        }
    }

    private void collectFiles(File file, List<File> result) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File childFile : files) {
                    collectFiles(childFile, result);
                }
            }
        } else if (file.isFile() && !file.getName().startsWith(".") && isValidLogFile(file)) {
            result.add(file);
        }
    }

    private String inferAppId(String filename) {
        if (filename.startsWith("event")) {
            String[] parts = filename.split("_", 3);
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        java.util.regex.Matcher matcher = APP_ID_PATTERN.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isValidLogFile(File file) {
        String name = file.getName();
        return name.startsWith("event");
    }
}
