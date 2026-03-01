package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StageMapper extends BaseMapper<GoldStageModel> {
    void updateStageMetrics(@Param("appId") String appId);
    void updateSpecificStageMetrics(@Param("appId") String appId, @Param("stageId") Long stageId, @Param("attemptId") Long attemptId);
    void updateBatchStageMetrics(@Param("appId") String appId, @Param("stageIds") java.util.List<Long> stageIds);

    void deleteStageStats(@Param("appId") String appId);
    void deleteSpecificStageStats(@Param("appId") String appId, @Param("stageId") Long stageId, @Param("attemptId") Long attemptId);
    void deleteBatchStageStats(@Param("appId") String appId, @Param("stageIds") java.util.List<Long> stageIds);

    void insertTaskStats(@Param("appId") String appId);
    void insertSpecificTaskStats(@Param("appId") String appId, @Param("stageId") Long stageId, @Param("attemptId") Long attemptId);
    void insertBatchTaskStats(@Param("appId") String appId, @Param("stageIds") java.util.List<Long> stageIds);

    List<java.util.Map<String, Object>> getExecutorSummary(@Param("appId") String appId,
                                                           @Param("stageId") Long stageId,
                                                           @Param("attemptId") Long attemptId);

    List<java.util.Map<String, Object>> getJobExecutorSummary(@Param("appId") String appId, @Param("jobId") Long jobId);
}
