package com.spark.insight.service;

import com.spark.insight.config.InsightProperties;
import com.spark.insight.mapper.ParsedEventLogMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogWatcherService {

    private final InsightProperties properties;
    private final EventParser eventParser;
    private final ParsedEventLogMapper parsedLogMapper;

    // Create a pool for parsing to avoid blocking the scheduler thread
    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(10);

    // Track files currently in processing to avoid concurrent parsing of the same file
    private final Set<String> processingFiles = ConcurrentHashMap.newKeySet();

    private static final Pattern APP_ID_PATTERN = Pattern.compile("(spark-[a-zA-Z0-9\\-]+)");
    private static final Pattern INDEX_PATTERN = Pattern.compile("[-_](\\d+)[-_]");

    @Scheduled(fixedDelayString = "${insight.scheduler.scan-interval-seconds:10}000")
    public void scan() {
        if (!properties.getScheduler().isEnabled()) return;

        String logPath = properties.getEventLogPath();
        File dir = new File(logPath);
        if (!dir.exists() || !dir.isDirectory()) return;

        List<File> allFiles = new ArrayList<>();
        collectFiles(dir, allFiles);

        // Group by App ID inferred from filename
        Map<String, List<File>> appGroups = new HashMap<>();
        List<File> standaloneFiles = new ArrayList<>();

        for (File f : allFiles) {
            String appId = inferAppId(f.getName());
            if (appId != null) {
                appGroups.computeIfAbsent(appId, k -> new ArrayList<>()).add(f);
            } else {
                standaloneFiles.add(f);
            }
        }

        // Process App Groups (Sorted)
        appGroups.forEach((appId, files) -> {
            files.sort(Comparator.comparingInt(this::getFileIndex).thenComparing(File::getName));
            // Submit as a single task per app to ensure sequential processing of rolling logs
            parseExecutor.submit(() -> {
                int total = files.size();
                for (int i = 0; i < total; i++) {
                    processFile(files.get(i), i + 1, total);
                }
            });
        });

        // Process Standalone
        standaloneFiles.forEach(f -> parseExecutor.submit(() -> processFile(f, 1, 1)));
    }

    private void collectFiles(File file, List<File> result) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) collectFiles(f, result);
            }
        } else if (file.isFile() && !file.getName().startsWith(".") && isValidLogFile(file)) {
            result.add(file);
        }
    }

    private String inferAppId(String filename) {
        // 首先尝试按照 event_index_appId 格式解析 (从左往右第二个下划线右边全都是 app id)
        if (filename.startsWith("event")) {
            String[] parts = filename.split("_", 3);
            if (parts.length >= 3) {
                return parts[2];
            }
        }

        // 兜底方案：使用正则匹配 spark-xxx
        Matcher m = APP_ID_PATTERN.matcher(filename);
        return m.find() ? m.group(1) : null;
    }

    private int getFileIndex(File file) {
        Matcher m = INDEX_PATTERN.matcher(file.getName());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private boolean isValidLogFile(File file) {
        String name = file.getName();
        // 只处理以 event 开头的文件
        return name.startsWith("event");
    }

    private void processFile(File file, int currentIdx, int totalFiles) {
        String absolutePath = file.getAbsolutePath();
        
        // Avoid concurrent processing of the same file (in case of scheduler overlaps)
        if (processingFiles.contains(absolutePath)) {
            return;
        }

        long lastModified = file.lastModified();
        long fileSize = file.length();

        ParsedEventLogModel record = parsedLogMapper.selectById(absolutePath);
        boolean needsParse = false;

        if (record == null) {
            log.info("New log file detected: {}", file.getName());
            needsParse = true;
        } else {
            // Check if file has been modified since last parse
            if (record.getLastModified() != lastModified || record.getFileSize() != fileSize || "PROCESSING".equals(record.getStatus())) {
                log.info("Log file change or retry needed: {} (Status: {}, Size: {} -> {}, Mod: {} -> {})",
                        file.getName(), record.getStatus(), record.getFileSize(), fileSize, record.getLastModified(), lastModified);
                needsParse = true;
            }
        }

        if (needsParse) {
            processingFiles.add(absolutePath);
            try {
                // Mark as PROCESSING in DB immediately
                ParsedEventLogModel startRecord = new ParsedEventLogModel();
                startRecord.setFilePath(absolutePath);
                startRecord.setLastModified(lastModified);
                startRecord.setFileSize(fileSize);
                startRecord.setStatus("PROCESSING");
                startRecord.setParsedAt(LocalDateTime.now());
                if (record == null) {
                    parsedLogMapper.insert(startRecord);
                } else {
                    parsedLogMapper.updateById(startRecord);
                }

                eventParser.parse(file, currentIdx, totalFiles);

                // Update record in DB
                ParsedEventLogModel newRecord = new ParsedEventLogModel();
                newRecord.setFilePath(absolutePath);
                newRecord.setLastModified(lastModified);
                newRecord.setFileSize(fileSize);
                newRecord.setParsedAt(LocalDateTime.now());
                newRecord.setStatus("SUCCESS");
                parsedLogMapper.updateById(newRecord);
            } catch (Exception e) {
                log.error("Failed to parse " + file.getName(), e);
                // Record failure state
                ParsedEventLogModel failedRecord = new ParsedEventLogModel();
                failedRecord.setFilePath(absolutePath);
                failedRecord.setLastModified(lastModified);
                failedRecord.setFileSize(fileSize);
                failedRecord.setParsedAt(LocalDateTime.now());
                failedRecord.setStatus("FAILED");
                parsedLogMapper.updateById(failedRecord);
            } finally {
                processingFiles.remove(absolutePath);
            }
        }
    }
}
