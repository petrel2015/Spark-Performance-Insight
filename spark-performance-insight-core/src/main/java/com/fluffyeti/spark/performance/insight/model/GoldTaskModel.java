package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_tasks")
public class GoldTaskModel {
    @TableId
    private String id;
    private String appId;
    private Long stageId;
    private Long attemptId;
    private Long taskId;
    private Long taskIndex;
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
    private Long outputBytes;
    private Long outputRecords;
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
    private String locality;
}
