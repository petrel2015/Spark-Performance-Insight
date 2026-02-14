package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data

@TableName("gold_applications")

public class ApplicationModel {

    @TableId

    private String appId;

    private String appName;

    private String userName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long duration;

    private String sparkVersion;

    private String status;

    private String dataQualityStatus;

    private String dataQualityNote;

    private String parsingStatus;

    private String parsingProgress;

    private String sourceFileMetadata;

    private Integer performanceScore;

    private String diagnosisInfo;

    private String llmReport;

    private Long llmStartTime;

    private Long llmEndTime;

    private Long totalLogSize;

    private Integer totalTasks = 0;

    private Integer failedTasks = 0;

    private Long totalInputBytes = 0L;

    private Long totalShuffleReadBytes = 0L;

    private LocalDateTime createdAt;



    @com.baomidou.mybatisplus.annotation.TableField(exist = false)

    private java.util.List<String> completedStages;

}
