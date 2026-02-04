package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.StageModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StageMapper extends BaseMapper<StageModel> {
    void updateStageMetrics(@Param("appId") String appId);

    void deleteStageStats(@Param("appId") String appId);

    void insertTaskStats(@Param("appId") String appId);

    List<java.util.Map<String, Object>> getExecutorSummary(@Param("appId") String appId,
                                                           @Param("stageId") Integer stageId,
                                                           @Param("attemptId") Integer attemptId);

    List<java.util.Map<String, Object>> getJobExecutorSummary(@Param("appId") String appId, @Param("jobId") Integer jobId);
}
