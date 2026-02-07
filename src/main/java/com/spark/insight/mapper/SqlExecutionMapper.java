package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.SqlExecutionModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SqlExecutionMapper extends BaseMapper<SqlExecutionModel> {
    void calculateSqlMetrics(String appId);
}
