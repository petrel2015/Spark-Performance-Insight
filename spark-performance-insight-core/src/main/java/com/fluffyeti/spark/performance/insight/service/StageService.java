package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.StageMapper;
import com.fluffyeti.spark.performance.insight.mapper.StageStatisticsMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageStatisticsModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageService extends ServiceImpl<StageMapper, GoldStageModel> {

    private final StageStatisticsMapper stageStatisticsMapper;

    public GoldStageModel getById(String id) {
        return super.getById(id);
    }

    /**
     * 利用 DuckDB 的强大分析能力进行后期预计算
     */
    public void calculateStageMetrics(String appId) {
        log.info("Calculating advanced metrics for App: {}", appId);
        StageMapper mapper = (StageMapper) getBaseMapper();
        // 1. 更新 Stage 表的基础聚合字段
        mapper.updateStageMetrics(appId);
        // 2. 清理旧统计并插入新统计
        mapper.deleteStageStats(appId);
        mapper.insertTaskStats(appId);
    }

    public void calculateStageMetrics(String appId, Long stageId, Long attemptId) {
        log.debug("Calculating advanced metrics for Stage: {}-{}", stageId, attemptId);
        StageMapper mapper = (StageMapper) getBaseMapper();
        mapper.updateSpecificStageMetrics(appId, stageId, attemptId);
        mapper.deleteSpecificStageStats(appId, stageId, attemptId);
        mapper.insertSpecificTaskStats(appId, stageId, attemptId);
    }

    public void calculateStageMetricsBatch(String appId, List<Long> stageIds) {
        log.info("Calculating advanced metrics for {} stages in App: {}", stageIds.size(), appId);
        StageMapper mapper = (StageMapper) getBaseMapper();
        mapper.updateBatchStageMetrics(appId, stageIds);
        mapper.deleteBatchStageStats(appId, stageIds);
        mapper.insertBatchTaskStats(appId, stageIds);
    }

    public void calculateOnlyStageStatsBatch(String appId, List<Long> stageIds) {
        log.info("Calculating summary statistics for {} stages in App: {}", stageIds.size(), appId);
        StageMapper mapper = (StageMapper) getBaseMapper();
        mapper.deleteBatchStageStats(appId, stageIds);
        mapper.insertBatchTaskStats(appId, stageIds);
    }

    public void calculateOnlyStageStats(String appId, Long stageId, Long attemptId) {
        log.debug("Calculating summary statistics for Stage: {}-{}", stageId, attemptId);
        StageMapper mapper = (StageMapper) getBaseMapper();
        mapper.deleteSpecificStageStats(appId, stageId, attemptId);
        mapper.insertSpecificTaskStats(appId, stageId, attemptId);
    }

    public List<GoldStageStatisticsModel> getStageStats(String appId, Long stageId, Long attemptId) {
        return stageStatisticsMapper.selectList(new QueryWrapper<GoldStageStatisticsModel>()
                .eq("app_id", appId)
                .eq("stage_id", stageId)
                .eq("attempt_id", attemptId));
    }

    public List<java.util.Map<String, Object>> getExecutorSummary(String appId, Long stageId) {
        return ((StageMapper) getBaseMapper()).getExecutorSummary(appId, stageId, null);
    }

    public List<java.util.Map<String, Object>> getExecutorSummary(String appId, Long stageId, Long attemptId) {
        return ((StageMapper) getBaseMapper()).getExecutorSummary(appId, stageId, attemptId);
    }

    public List<java.util.Map<String, Object>> getJobExecutorSummary(String appId, Long jobId) {
        return ((StageMapper) getBaseMapper()).getJobExecutorSummary(appId, jobId);
    }

    public GoldStageModel getStage(String appId, Long stageId, Long attemptId) {
        var query = lambdaQuery()
                .eq(GoldStageModel::getAppId, appId)
                .eq(GoldStageModel::getStageId, stageId);
        
        if (attemptId != null) {
            query.eq(GoldStageModel::getAttemptId, attemptId);
        } else {
            query.orderByDesc(GoldStageModel::getAttemptId);
            query.last("LIMIT 1");
        }
        
        return query.one();
    }
}
