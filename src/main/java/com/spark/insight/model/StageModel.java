package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stages")
public class StageModel {
    @com.baomidou.mybatisplus.annotation.TableId
    private String id;
    private String appId;
    private Integer stageId;
    private Integer jobId;
    private Integer attemptId;
    private String stageName;
    private Integer numTasks;
    private Integer numCompletedTasks = 0;
    private Integer numFailedTasks = 0;
    private LocalDateTime submissionTime;
    private LocalDateTime completionTime;
    private Long duration;
    private Long inputBytes = 0L;
    private Long inputRecords = 0L;
    private Long outputBytes = 0L;
    private Long outputRecords = 0L;
    private Long shuffleReadBytes = 0L;
    private Long shuffleReadRecords = 0L;
    private Long shuffleWriteBytes = 0L;
    private Long shuffleWriteRecords = 0L;
    private Long gcTimeSum = 0L;
    private Long tasksDurationSum = 0L;
    private Long executorDeserializeTimeSum = 0L;
    private Long resultSerializationTimeSum = 0L;
    private Long gettingResultTimeSum = 0L;
    private Long schedulerDelaySum = 0L;
    private Long peakExecutionMemoryMax = 0L;
    private Long peakExecutionMemorySum = 0L;
    private Long memoryBytesSpilledSum = 0L;
    private Long diskBytesSpilledSum = 0L;
    private Long shuffleWriteTimeSum = 0L;
    // 预计算分位数
    private Long durationP50 = 0L;
    private Long durationP75 = 0L;
    private Long durationP95 = 0L;
    private Long durationP99 = 0L;
    private Long maxTaskDuration = 0L;
    private String status;
    private Boolean isSkewed = false;
    private String parentStageIds;
    private String rddInfo; // JSON string of RDD Lineage
    private String localitySummary;
    private String diagnosisInfo; // JSON string of performance diagnosis
    private Double performanceScore; // Weighted score (0-100)
}
