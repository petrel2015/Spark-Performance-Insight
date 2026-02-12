package com.spark.insight.service;

import com.spark.insight.config.InsightProperties;
import com.spark.insight.mapper.ParsedEventLogMapper;
import com.spark.insight.model.ApplicationModel;
import com.spark.insight.model.EventLogStatus;
import com.spark.insight.model.ParsedEventLogModel;
import com.spark.insight.parser.EventParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
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

        // Check if MD5 of any file has changed compared to recorded ones? 
        // For simplicity, if we detect new/changed files in an existing app, we prompt for overwrite
        // Let's refine: calculate current set MD5 and compare.
        
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
        List<File> modifiableFiles = new ArrayList<>(files);
        modifiableFiles.sort(Comparator.comparingInt(this::getFileIndex).thenComparing(File::getName));
        parseExecutor.submit(() -> {
            for (File file : modifiableFiles) {
                processFile(file, appId);
            }
        });
    }

    private boolean checkAndMarkForProcessing(File file, String md5, @Nullable String appId) {
        String fileName = file.getName();
        ParsedEventLogModel record = parsedLogMapper.selectById(fileName);
        LocalDateTime updateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());

        if (record == null) {
            log.info("New log file detected (List 1): {}", fileName);
            // Insert initial record as IMPORTING
            ParsedEventLogModel startRecord = new ParsedEventLogModel();
            startRecord.setFileName(fileName);
            startRecord.setAppId(inferAppId(fileName));
            startRecord.setUpdateTime(updateTime);
            startRecord.setFileSize(file.length());
            startRecord.setFileHash(md5);
            startRecord.setStatus(EventLogStatus.IMPORTING);
            startRecord.setCreateTime(LocalDateTime.now());
            parsedLogMapper.insert(startRecord);
            return true;
        }

        // Check if MD5 changed and status is not IMPORTING (List 2)
        boolean md5Changed = !Objects.equals(record.getFileHash(), md5);
        if (record.getStatus() != EventLogStatus.IMPORTING && md5Changed) {
            log.info("Log file change detected (List 2): {} (MD5 changed)", fileName);
            return true;
        }

        // Retry processing if it was stuck in PROCESSING or failed
        return record.getStatus() == EventLogStatus.PROCESSING || record.getStatus() == EventLogStatus.FAILED;
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

    @Nullable
    private String inferAppId(String filename) {
        // 首先尝试按照 event_index_appId 格式解析 (从左往右第二个下划线右边全都是 app id)
        if (filename.startsWith("event")) {
            String[] parts = filename.split("_", 3);
            if (parts.length >= 3) {
                return parts[2];
            }
        }

        // 兜底方案：使用正则匹配 spark-xxx
        Matcher matcher = APP_ID_PATTERN.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private int getFileIndex(File file) {
        Matcher matcher = INDEX_PATTERN.matcher(file.getName());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private boolean isValidLogFile(File file) {
        String name = file.getName();
        // 只处理以 event 开头的文件
        return name.startsWith("event");
    }

    private void processFile(File file, @Nullable String appId) {
        String absolutePath = file.getAbsolutePath();
        String fileName = file.getName();

        // Avoid concurrent processing of the same file (in case of scheduler overlaps)
        if (processingFiles.contains(absolutePath)) {
            return;
        }

        LocalDateTime updateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
        long fileSize = file.length();
        String md5 = calculateMD5(file);

        processingFiles.add(absolutePath);
        try {
            // Mark as PROCESSING in DB immediately
            ParsedEventLogModel startRecord = new ParsedEventLogModel();
            startRecord.setFileName(fileName);
            startRecord.setAppId(appId);
            startRecord.setUpdateTime(updateTime);
            startRecord.setFileSize(fileSize);
            startRecord.setFileHash(md5);
            startRecord.setStatus(EventLogStatus.PROCESSING);
            startRecord.setCreateTime(LocalDateTime.now());
            parsedLogMapper.updateById(startRecord);

            eventParser.parse(file, appId);

            // Update record in DB
            ParsedEventLogModel newRecord = new ParsedEventLogModel();
            newRecord.setFileName(fileName);
            newRecord.setAppId(appId);
            newRecord.setUpdateTime(updateTime);
            newRecord.setFileSize(fileSize);
            newRecord.setFileHash(md5);
            newRecord.setCreateTime(LocalDateTime.now());
            newRecord.setStatus(EventLogStatus.SUCCESS);
            parsedLogMapper.updateById(newRecord);
        } catch (Exception exception) {
            log.error("Failed to parse " + fileName, exception);
            // Record failure state
            ParsedEventLogModel failedRecord = new ParsedEventLogModel();
            failedRecord.setFileName(fileName);
            failedRecord.setAppId(appId);
            failedRecord.setUpdateTime(updateTime);
            failedRecord.setFileSize(fileSize);
            failedRecord.setFileHash(md5);
            failedRecord.setCreateTime(LocalDateTime.now());
            failedRecord.setStatus(EventLogStatus.FAILED);
            parsedLogMapper.updateById(failedRecord);
        } finally {
            processingFiles.remove(absolutePath);
        }
    }
}
