package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.StageModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StageMapper extends BaseMapper<StageModel> {
    void updateStageMetrics(@Param("appId") String appId);

    void deleteStageStats(@Param("appId") String appId);

    void insertTaskStats(@Param("appId") String appId);
}
