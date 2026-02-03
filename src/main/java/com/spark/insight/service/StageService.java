package com.spark.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.StageMapper;
import com.spark.insight.mapper.StageStatisticsMapper;
import com.spark.insight.model.StageModel;
import com.spark.insight.model.StageStatisticsModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageService extends ServiceImpl<StageMapper, StageModel> {
    
    private final StageStatisticsMapper stageStatisticsMapper;

    public StageModel getById(String id) {
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

    public List<StageStatisticsModel> getStageStats(String appId, Integer stageId, Integer attemptId) {
        return stageStatisticsMapper.selectList(new QueryWrapper<StageStatisticsModel>()
                .eq("app_id", appId)
                .eq("stage_id", stageId)
                .eq("attempt_id", attemptId));
    }

    public List<java.util.Map<String, Object>> getExecutorSummary(String appId, Integer stageId) {
        return baseMapper.getExecutorSummary(appId, stageId, null);
    }
    
    public List<java.util.Map<String, Object>> getExecutorSummary(String appId, Integer stageId, Integer attemptId) {
        return baseMapper.getExecutorSummary(appId, stageId, attemptId);
    }

    public List<java.util.Map<String, Object>> getJobExecutorSummary(String appId, Integer jobId) {
        return baseMapper.getJobExecutorSummary(appId, jobId);
    }
}
