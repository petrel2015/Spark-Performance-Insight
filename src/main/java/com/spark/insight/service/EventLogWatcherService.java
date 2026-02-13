package com.spark.insight.service;

import com.spark.insight.config.InsightProperties;
import com.spark.insight.mapper.ParsedEventLogMapper;
import com.spark.insight.model.ApplicationModel;
import com.spark.insight.model.EventLogStatus;
import com.spark.insight.model.ParsedEventLogModel;
import com.spark.insight.parser.EventParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogWatcherService {

    private final InsightProperties properties;
    private final EventParser eventParser;
    private final ParsedEventLogMapper parsedLogMapper;
    private final ApplicationService applicationService;
    private final com.spark.insight.mapper.EventLogScanMapper scanMapper;
    private final StatusBroadcaster broadcaster;
    private final ApplicationLogService logService;

    // Create a pool for parsing to avoid blocking the scheduler thread
    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(10);

    // Track files currently in processing to avoid concurrent parsing of the same file
    private final Set<String> processingFiles = ConcurrentHashMap.newKeySet();

    private static final Pattern APP_ID_PATTERN = Pattern.compile("(spark-[a-zA-Z0-9\\-]+)");
    private static final Pattern INDEX_PATTERN = Pattern.compile("[-_](\\d+)[-_]");

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

        // 1. Record scanned files into two groups: inferred App ID and standalone
        Map<String, List<File>> appGroups = new HashMap<>();
        List<File> standaloneToProcess = new ArrayList<>();

        for (File file : allFiles) {
            String appId = inferAppId(file.getName());
            if (appId != null) {
                appGroups.computeIfAbsent(appId, k -> new ArrayList<>()).add(file);
            } else {
                // For standalone, we check MD5 here as before
                String md5 = calculateMD5(file);
                if (checkAndMarkForProcessing(file, md5, null)) {
                    standaloneToProcess.add(file);
                }
            }
        }

        // 2. Process App Groups: Check for Overwrite or New
        appGroups.forEach((appId, files) -> {
            long totalSize = files.stream().mapToLong(File::length).sum();
            ApplicationModel existingApp = applicationService.getById(appId);

            if (existingApp != null) {
                // Logic for PENDING_OVERWRITE
                handlePendingOverwrite(appId, files, totalSize, existingApp.getParsingStatus());
            } else {
                // Logic for New App
                handleNewApp(appId, files, totalSize);
            }
        });

        // 3. Submit standalone tasks
        standaloneToProcess.forEach(file -> parseExecutor.submit(() -> processFile(file, null)));
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

        // Start processing immediately for new apps
        triggerProcessing(appId, files);
    }

    private void handlePendingOverwrite(String appId, List<File> files, long totalSize, String currentStatus) {
        // Skip if already in a transient state
        if ("PENDING_OVERWRITE".equals(currentStatus) || "LOADING".equals(currentStatus) || "PRE_CALCULATING".equals(currentStatus)) {
            return;
        }

        // Check if any file is new or changed by comparing MD5
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

        // Save scan details
        com.spark.insight.model.EventLogScanModel scan = new com.spark.insight.model.EventLogScanModel();
        scan.setId(UUID.randomUUID().toString());
        scan.setAppId(appId);
        scan.setTotalSize(totalSize);
        scan.setPreviousStatus(currentStatus);
        scan.setDetectedTime(LocalDateTime.now());

        // Serialize file info (paths and names)
        List<String> filePaths = files.stream().map(File::getAbsolutePath).toList();
        try {
            scan.setFilePaths(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(filePaths));
        } catch (Exception e) {
            log.error("Failed to serialize file paths", e);
        }

        scanMapper.insert(scan);

        // Update App status
        ApplicationModel app = applicationService.getById(appId);
        app.setParsingStatus("PENDING_OVERWRITE");
        applicationService.updateById(app);

        broadcaster.broadcastStatus(appId, "PENDING_OVERWRITE", 0.0, "New logs detected. Overwrite needed.");
    }

    public void triggerProcessing(String appId, List<File> files) {
        logService.logEvent(appId, "IMPORT", "Triggering Import", "Starting parallel parsing of " + files.size() + " files");
        List<File> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(Comparator.comparingInt(this::getFileIndex).thenComparing(File::getName));

        parseExecutor.submit(() -> {
            try {
                for (File file : sortedFiles) {
                    markFileAsProcessing(file, appId);
                }
                eventParser.parseFiles(sortedFiles, appId);
            } catch (InterruptedException e) {
                log.error("Parsing interrupted for app: {}", appId);
                Thread.currentThread().interrupt();
            }
        });
    }

    private void markFileAsProcessing(File file, String appId) {
        String fileName = file.getName();
        ParsedEventLogModel record = parsedLogMapper.selectById(fileName);
        if (record == null) {
            record = new ParsedEventLogModel();
            record.setFileName(fileName);
            record.setAppId(appId);
            record.setCreateTime(LocalDateTime.now());
            record.setFileHash(calculateMD5(file));
            record.setFileSize(file.length());
            record.setStatus(EventLogStatus.PROCESSING);
            record.setUpdateTime(LocalDateTime.now());
            parsedLogMapper.insert(record);
        } else {
            record.setAppId(appId);
            record.setUpdateTime(LocalDateTime.now());
            record.setFileSize(file.length());
            record.setStatus(EventLogStatus.PROCESSING);
            parsedLogMapper.updateById(record);
        }
    }

    private boolean checkAndMarkForProcessing(File file, String md5, String appId) {
        String fileName = file.getName();
        ParsedEventLogModel record = parsedLogMapper.selectById(fileName);
        LocalDateTime now = LocalDateTime.now();

        if (record == null) {
            log.info("New log file detected: {}", fileName);
            ParsedEventLogModel startRecord = new ParsedEventLogModel();
            startRecord.setFileName(fileName);
            startRecord.setAppId(appId != null ? appId : inferAppId(fileName));
            startRecord.setUpdateTime(now);
            startRecord.setFileSize(file.length());
            startRecord.setFileHash(md5);
            startRecord.setStatus(EventLogStatus.IMPORTING);
            startRecord.setCreateTime(now);
            parsedLogMapper.insert(startRecord);
            return true;
        }

        if (record.getStatus() == EventLogStatus.SUCCESS) {
            boolean md5Changed = !Objects.equals(record.getFileHash(), md5);
            if (md5Changed) {
                log.info("Log file change detected: {} (MD5 changed)", fileName);
                return true;
            }
            return false;
        }

        return record.getStatus() == EventLogStatus.PROCESSING || record.getStatus() == EventLogStatus.FAILED || record.getStatus() == EventLogStatus.IMPORTING;
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
                if (hex.length() == 1) { hexString.append('0'); }
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

    private int getFileIndex(File file) {
        java.util.regex.Matcher matcher = INDEX_PATTERN.matcher(file.getName());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private boolean isValidLogFile(File file) {
        String name = file.getName();
        return name.startsWith("event");
    }

    private void processFile(File file, String appId) {
        parseExecutor.submit(() -> {
            try {
                markFileAsProcessing(file, appId);
                eventParser.parseFiles(Collections.singletonList(file), appId);
            } catch (Exception e) {
                log.error("Failed to process standalone file: " + file.getName(), e);
            }
        });
    }
}
