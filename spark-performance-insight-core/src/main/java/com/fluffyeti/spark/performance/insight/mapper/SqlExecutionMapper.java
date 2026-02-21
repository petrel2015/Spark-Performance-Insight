package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldSqlExecutionModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SqlExecutionMapper extends BaseMapper<GoldSqlExecutionModel> {
    void calculateSqlMetrics(String appId);
}
