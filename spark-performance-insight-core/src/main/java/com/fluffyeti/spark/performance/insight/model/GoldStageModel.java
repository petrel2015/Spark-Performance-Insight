package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gold_stages")
public class GoldStageModel {
    @TableId
    private String id;
    private String appId;
    private Long stageId;
    private Long jobId;
    private Long attemptId;
    private String stageName;
    private Long numTasks;
    private Long numCompletedTasks;
    private Long numFailedTasks;
    private LocalDateTime submissionTime;
    private LocalDateTime completionTime;
    private Long duration;
    private Long inputBytes;
    private Long inputRecords;
    private Long outputBytes;
    private Long outputRecords;
    private Long shuffleReadBytes;
    private Long shuffleReadRecords;
    private Long shuffleWriteBytes;
    private Long shuffleWriteRecords;
    private Long gcTimeSum;
    private Long tasksDurationSum;
    private Long executorDeserializeTimeSum;
    private Long resultSerializationTimeSum;
    private Long gettingResultTimeSum;
    private Long schedulerDelaySum;
    private Long peakExecutionMemoryMax;
    private Long peakExecutionMemorySum;
    private Long memoryBytesSpilledSum;
    private Long diskBytesSpilledSum;
    private Long shuffleWriteTimeSum;
    private Long durationP50;
    private Long durationP75;
    private Long durationP95;
    private Long durationP99;
    private Long maxTaskDuration;
    private String status;
    private Boolean isSkewed;
    private Double skewRatio;
    private Double gcRatio;
    private String parentStageIds;
    private String rddInfo;
    private String localitySummary;
    private String diagnosisInfo;
    private Double performanceScore;
    private Double scoreSkew;
    private Double scoreGc;
    private Double scoreLocality;
    private Double scoreShuffleWrite;
    private Double scoreShuffleRead;
    private Double scoreIo;
    private Double scoreSer;
    private Double scoreResult;
    private Double scoreDelay;
    private Double scoreSpill;
}
