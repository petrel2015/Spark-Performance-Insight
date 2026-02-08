package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.TaskMapper;
import com.spark.insight.model.TaskModel;
import org.springframework.stereotype.Service;

@Service
public class TaskService extends ServiceImpl<TaskMapper, TaskModel> {

    public long getExecutorCountForStage(String appId, int stageId) {
        return baseMapper.getExecutorCountForStage(appId, stageId);
    }

    public long getExecutorCountForJob(String appId, int jobId) {
        return baseMapper.getExecutorCountForJob(appId, jobId);
    }
}
