package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.SqlExecutionMapper;
import com.spark.insight.model.GoldSqlExecutionModel;
import org.springframework.stereotype.Service;

@Service
public class SqlExecutionService extends ServiceImpl<SqlExecutionMapper, GoldSqlExecutionModel> {
    public void calculateSqlMetrics(String appId) {
        baseMapper.calculateSqlMetrics(appId);
    }
}
