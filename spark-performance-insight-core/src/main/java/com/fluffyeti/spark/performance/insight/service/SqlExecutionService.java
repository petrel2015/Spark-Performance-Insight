package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.SqlExecutionMapper;
import com.fluffyeti.spark.performance.insight.model.GoldSqlExecutionModel;
import org.springframework.stereotype.Service;

@Service
public class SqlExecutionService extends ServiceImpl<SqlExecutionMapper, GoldSqlExecutionModel> {
    public void calculateSqlMetrics(String appId) {
        baseMapper.calculateSqlMetrics(appId);
    }
}
