package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldExecutorModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ExecutorMapper extends BaseMapper<GoldExecutorModel> {
    void updateExecutorMetrics(@Param("appId") String appId);

    List<Map<String, Object>> getExecutorTimeSeries(@Param("appId") String appId);

    List<Map<String, Object>> getExecutorMetricTimeSeries(@Param("appId") String appId, @Param("metricColumn") String metricColumn);

    List<Map<String, Object>> getExecutorEfficiencyMetrics(@Param("appId") String appId);
}
