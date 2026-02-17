package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.GoldExecutorModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExecutorMapper extends BaseMapper<GoldExecutorModel> {
    void updateExecutorMetrics(@Param("appId") String appId);
}
