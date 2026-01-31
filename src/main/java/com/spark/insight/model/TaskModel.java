package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tasks")
public class TaskModel {
    @com.baomidou.mybatisplus.annotation.TableId
    private String id;
    private String appId;
    private Integer stageId;
    private Long taskId;
    private String executorId;
    private String host;
    private Long duration;
    private Long gcTime;
    private Long schedulerDelay;
    private Long gettingResultTime;
    private Long peakExecutionMemory;
    private Long inputBytes;
    private Long memoryBytesSpilled;
    private Long diskBytesSpilled;
    private Long shuffleReadBytes;
    private Long shuffleRemoteRead;
    private Boolean speculative;
    private String status;
}
