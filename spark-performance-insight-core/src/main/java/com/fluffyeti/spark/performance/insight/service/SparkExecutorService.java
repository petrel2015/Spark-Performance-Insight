package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.ExecutorMapper;
import com.fluffyeti.spark.performance.insight.model.GoldExecutorModel;
import org.springframework.stereotype.Service;

@Service
public class SparkExecutorService extends ServiceImpl<ExecutorMapper, GoldExecutorModel> {
    public void calculateExecutorMetrics(String appId) {
        baseMapper.updateExecutorMetrics(appId);
    }
}
