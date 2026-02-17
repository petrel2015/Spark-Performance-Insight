package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.JobMapper;
import com.spark.insight.model.GoldJobModel;
import org.springframework.stereotype.Service;

@Service
public class JobService extends ServiceImpl<JobMapper, GoldJobModel> {

    public void calculateJobMetrics(String appId) {
        baseMapper.calculateJobMetrics(appId);
    }

    public GoldJobModel getJob(String appId, Integer jobId) {
        return lambdaQuery()
                .eq(GoldJobModel::getAppId, appId)
                .eq(GoldJobModel::getJobId, jobId)
                .one();
    }
}
