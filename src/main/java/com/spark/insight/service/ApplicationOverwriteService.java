package com.spark.insight.service;

import com.spark.insight.mapper.*;
import com.spark.insight.model.ApplicationModel;
import com.spark.insight.model.EventLogScanModel;
import com.spark.insight.model.ParsedEventLogModel;
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
    private final ParsedEventLogMapper parsedLogMapper;
    private final EventLogScanMapper scanMapper;
    private final EventLogWatcherService watcherService;
    private final StatusBroadcaster broadcaster;

    @Transactional
    public void confirmOverwrite(String appId) {
        log.info("Confirming overwrite for App: {}", appId);
        
        // 1. Fetch scan details
        List<EventLogScanModel> scans = scanMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EventLogScanModel>()
                        .eq(EventLogScanModel::getAppId, appId)
                        .orderByDesc(EventLogScanModel::getDetectedTime)
        );
        
        if (scans.isEmpty()) {
            throw new RuntimeException("No scan details found for appId: " + appId);
        }
        
        EventLogScanModel scan = scans.get(0);

        // 2. Clear old business data
        jobMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.spark.insight.model.JobModel>().eq(com.spark.insight.model.JobModel::getAppId, appId));
        stageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.spark.insight.model.StageModel>().eq(com.spark.insight.model.StageModel::getAppId, appId));
        taskMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.spark.insight.model.TaskModel>().eq(com.spark.insight.model.TaskModel::getAppId, appId));
        executorMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.spark.insight.model.ExecutorModel>().eq(com.spark.insight.model.ExecutorModel::getAppId, appId));
        sqlExecutionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.spark.insight.model.SqlExecutionModel>().eq(com.spark.insight.model.SqlExecutionModel::getAppId, appId));
        envMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.spark.insight.model.EnvironmentConfigModel>().eq(com.spark.insight.model.EnvironmentConfigModel::getAppId, appId));
        
        // Clear parsed logs status for this app to allow re-parsing
        parsedLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParsedEventLogModel>().eq(ParsedEventLogModel::getAppId, appId));

        // 3. Update Application status
        ApplicationModel app = applicationMapper.selectById(appId);
        app.setParsingStatus("PENDING_TO_LOADING");
        app.setTotalLogSize(scan.getTotalSize());
        app.setParsingProgress("Restarting import...");
        applicationMapper.updateById(app);

        // 4. Trigger processing
        try {
            List<String> paths = new com.fasterxml.jackson.databind.ObjectMapper().readValue(scan.getFilePaths(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            List<File> files = paths.stream().map(File::new).toList();
            
            // Clean up scan records
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
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EventLogScanModel>()
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
}
