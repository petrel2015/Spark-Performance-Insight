package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.TaskMapper;
import com.fluffyeti.spark.performance.insight.model.GoldTaskModel;
import org.springframework.stereotype.Service;

@Service
public class TaskService extends ServiceImpl<TaskMapper, GoldTaskModel> {

    public long getExecutorCountForStage(String appId, int stageId) {
        return baseMapper.getExecutorCountForStage(appId, stageId);
    }

    public long getExecutorCountForJob(String appId, int jobId) {
        return baseMapper.getExecutorCountForJob(appId, jobId);
    }
}
