package com.spark.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spark.insight.mapper.*;
import com.spark.insight.model.*;
import com.spark.insight.config.InsightProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationOverwriteService {

    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final StageMapper stageMapper;
    private final TaskMapper taskMapper;
    private final ExecutorMapper executorMapper;
    private final SqlExecutionMapper sqlExecutionMapper;
    private final EnvironmentConfigMapper envMapper;
    private final ParsedEventLogMapper parsedLogMapper;
    private final EventLogScanMapper scanMapper;
    private final EventLogWatcherService watcherService;
    private final StatusBroadcaster broadcaster;
    private final StageStatisticsMapper stageStatisticsMapper;
    private final StorageRddMapper storageRddMapper;
    private final StorageBlockMapper storageBlockMapper;
    private final ApplicationLogMapper applicationLogMapper;
    private final InsightProperties properties;

    @Transactional
    public void confirmOverwrite(String appId) {
        log.info("Confirming overwrite for App: {}", appId);
        
        List<EventLogScanModel> scans = scanMapper.selectList(
                new LambdaQueryWrapper<EventLogScanModel>()
                        .eq(EventLogScanModel::getAppId, appId)
                        .orderByDesc(EventLogScanModel::getDetectedTime)
        );
        
        if (scans.isEmpty()) {
            throw new RuntimeException("No scan details found for appId: " + appId);
        }
        
        EventLogScanModel scan = scans.get(0);

        clearAppData(appId);
        
        parsedLogMapper.delete(new LambdaQueryWrapper<ParsedEventLogModel>().eq(ParsedEventLogModel::getAppId, appId));

        ApplicationModel app = applicationMapper.selectById(appId);
        app.setParsingStatus("PENDING_TO_LOADING");
        app.setTotalLogSize(scan.getTotalSize());
        app.setParsingProgress("Restarting import...");
        applicationMapper.updateById(app);

        try {
            List<String> paths = new com.fasterxml.jackson.databind.ObjectMapper().readValue(scan.getFilePaths(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            List<File> files = paths.stream().map(File::new).toList();
            
            scanMapper.deleteBatchIds(scans.stream().map(EventLogScanModel::getId).toList());
            
            broadcaster.broadcastStatus(appId, "PENDING_TO_LOADING", 0.0, "Ready to re-import.");
            
            watcherService.triggerProcessing(appId, files);
        } catch (Exception e) {
            log.error("Failed to parse file paths during overwrite confirm", e);
            throw new RuntimeException("Overwrite failed due to path error", e);
        }
    }

    @Transactional
    public void cancelOverwrite(String appId) {
        log.info("Cancelling overwrite for App: {}", appId);
        
        List<EventLogScanModel> scans = scanMapper.selectList(
                new LambdaQueryWrapper<EventLogScanModel>()
                        .eq(EventLogScanModel::getAppId, appId)
                        .orderByDesc(EventLogScanModel::getDetectedTime)
        );
        
        if (!scans.isEmpty()) {
            EventLogScanModel lastScan = scans.get(0);
            ApplicationModel app = applicationMapper.selectById(appId);
            if (app != null) {
                app.setParsingStatus(lastScan.getPreviousStatus());
                applicationMapper.updateById(app);
                broadcaster.broadcastStatus(appId, app.getParsingStatus(), 100.0, "Overwrite cancelled.");
            }
            scanMapper.deleteBatchIds(scans.stream().map(EventLogScanModel::getId).toList());
        }
    }

    @Transactional
    public void deleteApp(String appId) {
        log.info("Deleting App: {}", appId);
        clearAppData(appId);
        parsedLogMapper.delete(new LambdaQueryWrapper<ParsedEventLogModel>().eq(ParsedEventLogModel::getAppId, appId));
        applicationMapper.deleteById(appId);
        broadcaster.broadcastStatus(appId, "DELETED", 0.0, "Application data cleared.");
    }

    @Transactional
    public void reimportApp(String appId) {
        log.info("Re-importing App: {}", appId);
        ApplicationModel app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new RuntimeException("Application not found: " + appId);
        }

        clearAppData(appId);
        
        parsedLogMapper.delete(new LambdaQueryWrapper<ParsedEventLogModel>().eq(ParsedEventLogModel::getAppId, appId));

        String logPath = properties.getEventLogPath();
        File directory = new File(logPath);
        if (!directory.exists() || !directory.isDirectory()) {
             throw new RuntimeException("Event log directory not found: " + logPath);
        }

        List<File> appFiles = findAppFiles(directory, appId);
        if (appFiles.isEmpty()) {
            throw new RuntimeException("No log files found for re-importing appId: " + appId);
        }

        long totalSize = appFiles.stream().mapToLong(File::length).sum();

        app.setParsingStatus("PENDING_TO_LOADING");
        app.setTotalLogSize(totalSize);
        app.setParsingProgress("Re-importing triggered manually...");
        applicationMapper.updateById(app);

        broadcaster.broadcastStatus(appId, "PENDING_TO_LOADING", 0.0, "Starting re-import...");
        watcherService.triggerProcessing(appId, appFiles);
    }

    private void clearAppData(String appId) {
        jobMapper.delete(new LambdaQueryWrapper<JobModel>().eq(JobModel::getAppId, appId));
        stageMapper.delete(new LambdaQueryWrapper<StageModel>().eq(StageModel::getAppId, appId));
        taskMapper.delete(new LambdaQueryWrapper<TaskModel>().eq(TaskModel::getAppId, appId));
        executorMapper.delete(new LambdaQueryWrapper<ExecutorModel>().eq(ExecutorModel::getAppId, appId));
        sqlExecutionMapper.delete(new LambdaQueryWrapper<SqlExecutionModel>().eq(SqlExecutionModel::getAppId, appId));
        envMapper.delete(new LambdaQueryWrapper<EnvironmentConfigModel>().eq(EnvironmentConfigModel::getAppId, appId));
        stageStatisticsMapper.delete(new LambdaQueryWrapper<StageStatisticsModel>().eq(StageStatisticsModel::getAppId, appId));
        storageRddMapper.delete(new LambdaQueryWrapper<StorageRddModel>().eq(StorageRddModel::getAppId, appId));
        storageBlockMapper.delete(new LambdaQueryWrapper<StorageBlockModel>().eq(StorageBlockModel::getAppId, appId));
        applicationLogMapper.delete(new LambdaQueryWrapper<ApplicationLogModel>().eq(ApplicationLogModel::getAppId, appId));
        scanMapper.delete(new LambdaQueryWrapper<EventLogScanModel>().eq(EventLogScanModel::getAppId, appId));
    }

    private List<File> findAppFiles(File dir, String appId) {
        List<File> result = new java.util.ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    result.addAll(findAppFiles(file, appId));
                } else if (file.getName().contains(appId) && file.getName().startsWith("event")) {
                    result.add(file);
                }
            }
        }
        return result;
    }
}
