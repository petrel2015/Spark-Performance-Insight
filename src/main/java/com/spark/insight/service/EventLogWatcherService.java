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
    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(2);

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

        // Process grouped logs in order (for rolling logs)
        appGroups.forEach((appId, files) -> {
            files.sort(Comparator.comparingInt(this::getFileIndex).thenComparing(File::getName));
            for (File f : files) {
                checkAndParse(f);
            }
        });

        // Process files that don't match standard appId naming
        for (File f : standaloneFiles) {
            checkAndParse(f);
        }
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
        Matcher m = APP_ID_PATTERN.matcher(filename);
        return m.find() ? m.group(1) : null;
    }

    private int getFileIndex(File file) {
        Matcher m = INDEX_PATTERN.matcher(file.getName());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private boolean isValidLogFile(File file) {
        String name = file.getName();
        // Support Spark History Server format, JSON, and ZSTD compressed logs
        return name.startsWith("app-") || name.contains("events_") || 
               name.endsWith(".json") || name.endsWith(".zstd") || name.endsWith(".zst");
    }

    private void checkAndParse(File file) {
        String absolutePath = file.getAbsolutePath();
        long lastModified = file.lastModified();
        long fileSize = file.length();

        ParsedEventLogModel record = parsedLogMapper.selectById(absolutePath);
        boolean needsParse = false;

        if (record == null) {
            log.info("New log file detected: {}", file.getName());
            needsParse = true;
        } else {
            // Check if file has been modified since last parse
            if (record.getLastModified() != lastModified || record.getFileSize() != fileSize) {
                log.info("Log file changed: {} (Size: {} -> {}, Mod: {} -> {})", 
                        file.getName(), record.getFileSize(), fileSize, record.getLastModified(), lastModified);
                needsParse = true;
            }
        }

        if (needsParse) {
            // Submit to executor to process asynchronously
            parseExecutor.submit(() -> {
                try {
                    // TODO: Ideally we should calculate hash here if required
                    
                    eventParser.parse(file);
                    
                    // Update record in DB
                    ParsedEventLogModel newRecord = new ParsedEventLogModel();
                    newRecord.setFilePath(absolutePath);
                    newRecord.setLastModified(lastModified);
                    newRecord.setFileSize(fileSize);
                    newRecord.setParsedAt(LocalDateTime.now());
                    newRecord.setStatus("SUCCESS");
                    
                    if (record == null) {
                        parsedLogMapper.insert(newRecord);
                    } else {
                        parsedLogMapper.updateById(newRecord);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse " + file.getName(), e);
                    // Record failure state
                    ParsedEventLogModel failedRecord = new ParsedEventLogModel();
                    failedRecord.setFilePath(absolutePath);
                    failedRecord.setLastModified(lastModified);
                    failedRecord.setFileSize(fileSize);
                    failedRecord.setParsedAt(LocalDateTime.now());
                    failedRecord.setStatus("FAILED");
                    
                    if (record == null) {
                        parsedLogMapper.insert(failedRecord);
                    } else {
                        parsedLogMapper.updateById(failedRecord);
                    }
                }
            });
        }
    }
}
