package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fluffyeti.spark.performance.insight.mapper.*;
import com.fluffyeti.spark.performance.insight.model.*;
import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

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
    private final EventLogScanMapper scanMapper;
    private final ParsingQueueService parsingQueueService;
    private final StatusBroadcaster broadcaster;
    private final StageStatisticsMapper stageStatisticsMapper;
    private final StorageRddMapper storageRddMapper;
    private final StorageBlockMapper storageBlockMapper;
    private final ApplicationLogMapper applicationLogMapper;
    private final SystemProperties properties;

    @Transactional
    public void confirmOverwrite(String appId) {
        log.info("Confirming overwrite for App: {}", appId);
        
        List<SysEventLogScanModel> scans = scanMapper.selectList(
                new LambdaQueryWrapper<SysEventLogScanModel>()
                        .eq(SysEventLogScanModel::getAppId, appId)
                        .orderByDesc(SysEventLogScanModel::getDetectedTime)
        );
        
        if (scans.isEmpty()) {
            throw new RuntimeException("No scan details found for appId: " + appId);
        }
        
        SysEventLogScanModel scan = scans.get(0);

        clearAppData(appId);
        
        GoldApplicationModel app = applicationMapper.selectById(appId);
        app.setParsingStatus("PENDING_TO_LOADING");
        app.setTotalLogSize(scan.getTotalSize());
        app.setParsingProgress("Restarting import...");
        applicationMapper.updateById(app);

        try {
            // We don't need to parse file paths manually if we trust the queue service to find them
            // But we keep the logic to delete scans
            scanMapper.deleteBatchIds(scans.stream().map(SysEventLogScanModel::getId).toList());
            
            broadcaster.broadcastStatus(appId, "PENDING_TO_LOADING", 0.0, "Queueing re-import.", app.getAppName(), app.getParsingStartTime());
            
            parsingQueueService.submit(appId, "FULL");
        } catch (Exception e) {
            log.error("Failed to confirm overwrite", e);
            throw new RuntimeException("Overwrite failed", e);
        }
    }

    @Transactional
    public void cancelOverwrite(String appId) {
        log.info("Cancelling overwrite for App: {}", appId);
        
        List<SysEventLogScanModel> scans = scanMapper.selectList(
                new LambdaQueryWrapper<SysEventLogScanModel>()
                        .eq(SysEventLogScanModel::getAppId, appId)
                        .orderByDesc(SysEventLogScanModel::getDetectedTime)
        );
        
        if (!scans.isEmpty()) {
            SysEventLogScanModel lastScan = scans.get(0);
            GoldApplicationModel app = applicationMapper.selectById(appId);
            if (app != null) {
                app.setParsingStatus(lastScan.getPreviousStatus());
                applicationMapper.updateById(app);
                broadcaster.broadcastStatus(appId, app.getParsingStatus(), 100.0, "Overwrite cancelled.", app.getAppName(), app.getParsingStartTime());
            }
            scanMapper.deleteBatchIds(scans.stream().map(SysEventLogScanModel::getId).toList());
        }
    }

    @Transactional
    public void deleteApp(String appId) {
        log.info("Deleting App: {}", appId);
        GoldApplicationModel app = applicationMapper.selectById(appId);
        String appName = app != null ? app.getAppName() : null;
        Long startTime = app != null ? app.getParsingStartTime() : null;
        
        clearAppData(appId);
        applicationMapper.deleteById(appId);
        broadcaster.broadcastStatus(appId, "DELETED", 0.0, "Application data cleared.", appName, startTime);
    }

    @Transactional
    public void reimportApp(String appId) {
        log.info("Re-importing App: {}", appId);
        GoldApplicationModel app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new RuntimeException("Application not found: " + appId);
        }

        clearAppData(appId);
        
        // We check files existence just to fail fast, but queue service will handle actual finding
        String logPath = properties.getEventLogPath();
        File directory = new File(logPath);
        if (!directory.exists() || !directory.isDirectory()) {
             throw new RuntimeException("Event log directory not found: " + logPath);
        }

        // We can skip detailed file finding here as ParsingQueueService/EventLogWatcherService will do it
        // But for totalSize update we might need it. 
        // Let's assume re-import keeps previous size or we just update status.
        
        app.setParsingStatus("PENDING_TO_LOADING");
        app.setParsingProgress("Queueing re-import...");
        applicationMapper.updateById(app);

        broadcaster.broadcastStatus(appId, "PENDING_TO_LOADING", 0.0, "Queueing re-import...", app.getAppName(), app.getParsingStartTime());
        parsingQueueService.submit(appId, "FULL");
    }

    private void clearAppData(String appId) {
        jobMapper.delete(new LambdaQueryWrapper<GoldJobModel>().eq(GoldJobModel::getAppId, appId));
        stageMapper.delete(new LambdaQueryWrapper<GoldStageModel>().eq(GoldStageModel::getAppId, appId));
        taskMapper.delete(new LambdaQueryWrapper<GoldTaskModel>().eq(GoldTaskModel::getAppId, appId));
        executorMapper.delete(new LambdaQueryWrapper<GoldExecutorModel>().eq(GoldExecutorModel::getAppId, appId));
        sqlExecutionMapper.delete(new LambdaQueryWrapper<GoldSqlExecutionModel>().eq(GoldSqlExecutionModel::getAppId, appId));
        envMapper.delete(new LambdaQueryWrapper<GoldEnvironmentConfigModel>().eq(GoldEnvironmentConfigModel::getAppId, appId));
        stageStatisticsMapper.delete(new LambdaQueryWrapper<GoldStageStatisticsModel>().eq(GoldStageStatisticsModel::getAppId, appId));
        storageRddMapper.delete(new LambdaQueryWrapper<GoldStorageRddModel>().eq(GoldStorageRddModel::getAppId, appId));
        storageBlockMapper.delete(new LambdaQueryWrapper<GoldStorageBlockModel>().eq(GoldStorageBlockModel::getAppId, appId));
        applicationLogMapper.delete(new LambdaQueryWrapper<SysApplicationLogModel>().eq(SysApplicationLogModel::getAppId, appId));
        scanMapper.delete(new LambdaQueryWrapper<SysEventLogScanModel>().eq(SysEventLogScanModel::getAppId, appId));
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
