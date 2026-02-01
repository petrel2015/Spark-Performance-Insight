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
    private Integer taskIndex;
    private String executorId;
    private String host;
    private Long launchTime;
    private Long finishTime;
    private Long duration;
    private Long gcTime;
    private Long schedulerDelay;
    private Long gettingResultTime;
    private Long executorDeserializeTime;
    private Long executorRunTime;
    private Long resultSerializationTime;
    private Long executorCpuTime;
    private Long peakExecutionMemory;
    private Long inputBytes;
    private Long inputRecords;
    private Long memoryBytesSpilled;
    private Long diskBytesSpilled;
    private Long shuffleReadBytes;
    private Long shuffleReadRecords;
    private Long shuffleFetchWaitTime;
    private Long shuffleWriteBytes;
    private Long shuffleWriteTime;
    private Long shuffleWriteRecords;
    private Long shuffleRemoteRead;
    private Boolean speculative;
    private String status;
}
