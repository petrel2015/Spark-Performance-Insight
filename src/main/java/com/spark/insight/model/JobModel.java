package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("jobs")
public class JobModel {
    @TableId
    private String id;
    private String appId;
    private Integer jobId;
    private LocalDateTime submissionTime;
    private LocalDateTime completionTime;
    private String status;
    private Integer numStages;
    private Integer numTasks;
    private String stageIds;
}
