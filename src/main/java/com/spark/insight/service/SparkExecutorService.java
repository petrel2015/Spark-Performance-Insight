package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.ExecutorMapper;
import com.spark.insight.model.GoldExecutorModel;
import org.springframework.stereotype.Service;

@Service
public class SparkExecutorService extends ServiceImpl<ExecutorMapper, GoldExecutorModel> {
    public void calculateExecutorMetrics(String appId) {
        baseMapper.updateExecutorMetrics(appId);
    }
}
