package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("jobs")
public class JobModel {
    @TableId
    private String id;
    private String appId;
    private Integer jobId;
    private LocalDateTime submissionTime;
    private LocalDateTime completionTime;
    private Long duration;
    private String status;
    private Integer numStages;
    private Integer numTasks;
    private String stageIds;
    private String description;
    private String jobGroup;
    private Integer numCompletedStages;
    private Integer numFailedStages;
    private Integer numSkippedStages;
    private Integer numCompletedTasks;
    private Integer numFailedTasks;
    private Integer numActiveTasks;
    private Integer numSkippedTasks;

    @TableField(exist = false)
    private List<StageModel> stageList;
}
