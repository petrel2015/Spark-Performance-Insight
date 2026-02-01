package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.ExecutorModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExecutorMapper extends BaseMapper<ExecutorModel> {
    void updateExecutorMetrics(@Param("appId") String appId);
}
