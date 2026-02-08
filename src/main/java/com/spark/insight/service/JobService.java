package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.JobMapper;
import com.spark.insight.model.JobModel;
import org.springframework.stereotype.Service;

@Service
public class JobService extends ServiceImpl<JobMapper, JobModel> {

    public void calculateJobMetrics(String appId) {
        baseMapper.calculateJobMetrics(appId);
    }

    public JobModel getJob(String appId, Integer jobId) {
        return lambdaQuery()
                .eq(JobModel::getAppId, appId)
                .eq(JobModel::getJobId, jobId)
                .one();
    }
}
