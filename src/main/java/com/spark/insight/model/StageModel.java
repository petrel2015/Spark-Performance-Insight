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
    private LocalDateTime submissionTime;
    private LocalDateTime completionTime;
    private Long inputBytes = 0L;
    private Long inputRecords = 0L;
    private Long outputBytes = 0L;
    private Long shuffleReadBytes = 0L;
    private Long shuffleReadRecords = 0L;
    private Long shuffleWriteBytes = 0L;
    private Long shuffleWriteRecords = 0L;
    private Long gcTimeSum = 0L;
    private Long tasksDurationSum = 0L;
    // 预计算分位数
    private Long durationP50 = 0L;
    private Long durationP75 = 0L;
    private Long durationP95 = 0L;
    private Long durationP99 = 0L;
    private Boolean isSkewed = false;
}
