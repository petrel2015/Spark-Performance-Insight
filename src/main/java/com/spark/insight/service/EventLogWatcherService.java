package com.spark.insight.service;

import com.spark.insight.config.InsightProperties;
import com.spark.insight.mapper.ParsedEventLogMapper;
import com.spark.insight.model.ApplicationModel;
import com.spark.insight.model.EventLogStatus;
import com.spark.insight.model.ParsedEventLogModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogWatcherService {

    private final InsightProperties properties;
    private final ParsedEventLogMapper parsedLogMapper;
    private final ApplicationService applicationService;
    private final com.spark.insight.mapper.EventLogScanMapper scanMapper;
    private final StatusBroadcaster broadcaster;
    private final ApplicationLogService logService;
    
    private final BronzeIngestionService bronzeIngestionService;
    private final SilverTransformationService silverTransformationService;
    private final GoldAggregationService goldAggregationService;

    private final ExecutorService pipelineExecutor = Executors.newFixedThreadPool(4);

    // Using [0-9] instead of \d to avoid escaping hell
    private static final Pattern APP_ID_PATTERN = Pattern.compile("(spark-[a-zA-Z0-9-]+)");
    private static final Pattern INDEX_PATTERN = Pattern.compile("[-_]([0-9]+)[-_]");

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
            ApplicationModel existingApp = applicationService.getById(appId);

            if (existingApp != null) {
                handlePendingOverwrite(appId, files, totalSize, existingApp.getParsingStatus());
            } else {
                handleNewApp(appId, files, totalSize);
            }
        });
    }

    private void handleNewApp(String appId, List<File> files, long totalSize) {
        logService.logEvent(appId, "SCAN", "New Application Detected", 
                String.format("Found %d files, total size: %.2f MB", files.size(), totalSize / (1024.0 * 1024.0)));
        
        ApplicationModel app = new ApplicationModel();
        app.setAppId(appId);
        app.setAppName("Initializing...");
        app.setParsingStatus("PENDING_TO_LOADING");
        app.setTotalLogSize(totalSize);
        applicationService.save(app);

        broadcaster.broadcastStatus(appId, "PENDING_TO_LOADING", 0.0, "Waiting to process...");
        triggerProcessing(appId, files);
    }

    private void handlePendingOverwrite(String appId, List<File> files, long totalSize, String currentStatus) {
        if ("PENDING_OVERWRITE".equals(currentStatus) || "LOADING".equals(currentStatus)) {
            return;
        }

        boolean hasChanges = false;
        for (File file : files) {
            String md5 = calculateMD5(file);
            ParsedEventLogModel record = parsedLogMapper.selectById(file.getName());
            if (record == null || !Objects.equals(record.getFileHash(), md5) || record.getStatus() == EventLogStatus.FAILED) {
                hasChanges = true;
                break;
            }
        }

        if (!hasChanges) {
            return;
        }

        logService.logEvent(appId, "SCAN", "Changes Detected", 
                String.format("New/Modified log files found. Total size: %.2f MB", totalSize / (1024.0 * 1024.0)));

        com.spark.insight.model.EventLogScanModel scan = new com.spark.insight.model.EventLogScanModel();
        scan.setId(UUID.randomUUID().toString());
        scan.setAppId(appId);
        scan.setTotalSize(totalSize);
        scan.setPreviousStatus(currentStatus);
        scan.setDetectedTime(LocalDateTime.now());

        List<String> filePaths = files.stream().map(File::getAbsolutePath).toList();
        try {
            scan.setFilePaths(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(filePaths));
        } catch (Exception e) {
            log.error("Failed to serialize file paths", e);
        }

        scanMapper.insert(scan);

        ApplicationModel app = applicationService.getById(appId);
        app.setParsingStatus("PENDING_OVERWRITE");
        applicationService.updateById(app);

        broadcaster.broadcastStatus(appId, "PENDING_OVERWRITE", 0.0, "New logs detected. Overwrite needed.");
    }

    public void triggerProcessing(String appId, List<File> files) {
        logService.logEvent(appId, "IMPORT", "Triggering Pipeline", "Starting Medallion pipeline for " + files.size() + " files");
        
        pipelineExecutor.submit(() -> {
            try {
                ApplicationModel app = applicationService.getById(appId);
                app.setParsingStatus("LOADING");
                applicationService.updateById(app);
                broadcaster.broadcastStatus(appId, "LOADING", 10.0, "Bronze: Ingesting raw logs...");

                bronzeIngestionService.ingest(appId, files);
                broadcaster.broadcastStatus(appId, "LOADING", 40.0, "Silver: Structuring data...");

                silverTransformationService.transform(appId);
                broadcaster.broadcastStatus(appId, "LOADING", 70.0, "Gold: Aggregating metrics...");

                goldAggregationService.aggregate(appId);
                
                app.setParsingStatus("SUCCESS");
                applicationService.updateById(app);
                
                for (File file : files) {
                    markFileAsSuccess(file, appId);
                }
                
                broadcaster.broadcastStatus(appId, "SUCCESS", 100.0, "Pipeline completed successfully.");
                logService.logEvent(appId, "SUCCESS", "Pipeline Finished", "App data fully processed and aggregated.");
                
            } catch (Exception e) {
                log.error("Medallion pipeline failed for app: {}", appId, e);
                ApplicationModel app = applicationService.getById(appId);
                if (app != null) {
                    app.setParsingStatus("FAILED");
                    applicationService.updateById(app);
                }
                broadcaster.broadcastStatus(appId, "FAILED", 0.0, "Error: " + e.getMessage());
                logService.logEvent(appId, "FAILED", "Pipeline Error", e.getMessage());
            }
        });
    }

    private void markFileAsSuccess(File file, String appId) {
        String fileName = file.getName();
        ParsedEventLogModel record = parsedLogMapper.selectById(fileName);
        if (record == null) {
            record = new ParsedEventLogModel();
            record.setFileName(fileName);
            record.setAppId(appId);
            record.setCreateTime(LocalDateTime.now());
        }
        record.setFileHash(calculateMD5(file));
        record.setFileSize(file.length());
        record.setStatus(EventLogStatus.SUCCESS);
        record.setUpdateTime(LocalDateTime.now());
        if (parsedLogMapper.selectById(fileName) == null) {
            parsedLogMapper.insert(record);
        } else {
            parsedLogMapper.updateById(record);
        }
    }

    private String calculateMD5(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
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
