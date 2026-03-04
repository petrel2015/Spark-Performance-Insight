package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("gold_jobs")
public class GoldJobModel {
    @TableId
    private String id;
    private String appId;
    private Long jobId;
    private Long submissionTime;
    private Long completionTime;
    private Long duration;
    private String status;
    private Long numStages;
    private Long numTasks;
    private String stageIds;
    private String description;
    private String jobGroup;
    private Long numCompletedStages;
    private Long numFailedStages;
    private Long numSkippedStages;
    private Long numCompletedTasks;
    private Long numFailedTasks;
    private Long numActiveTasks;
    private Long numSkippedTasks;
    private Long sqlExecutionId;
    private Double performanceScore;
    private Long tasksDurationSum;

    @TableField(exist = false)
    private List<GoldStageModel> stageList;
}
