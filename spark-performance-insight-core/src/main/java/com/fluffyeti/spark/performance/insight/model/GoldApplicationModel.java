package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("gold_applications")
public class GoldApplicationModel {
    @TableId
    private String appId;
    private String appName;
    private String userName;
    private Long startTime;
    private Long endTime;
    private Long duration;
    private String sparkVersion;
    private String status;
    private String dataQualityStatus;
    private String dataQualityNote;
    private String parsingStatus;
    private String parsingProgress;
    private Double parsingProgressValue;
    private Long parsingStartTime;
    private Long parsingEndTime;
    private String sourceFileMetadata;
    private Integer performanceScore;
    private String diagnosisInfo;
    private String llmReport;
    private Long llmStartTime;
    private Long llmEndTime;
    private Long totalLogSize;
    private Long totalTasks;
    private Long failedTasks;
    private Long totalInputBytes;
    private Long totalShuffleReadBytes;
    private String compressionFormat;
    private String notes;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> completedStages;
}
