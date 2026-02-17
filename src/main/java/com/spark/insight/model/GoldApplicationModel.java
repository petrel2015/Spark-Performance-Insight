package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data

@TableName("gold_applications")

public class GoldApplicationModel {

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

    @com.baomidou.mybatisplus.annotation.TableField("parsing_status")
    private String parsingStatus;

    @com.baomidou.mybatisplus.annotation.TableField("parsing_progress")
    private String parsingProgress;

    @com.baomidou.mybatisplus.annotation.TableField("parsing_progress_value")
    private Double parsingProgressValue;

    @com.baomidou.mybatisplus.annotation.TableField("parsing_start_time")
    private LocalDateTime parsingStartTime;

    @com.baomidou.mybatisplus.annotation.TableField("parsing_end_time")
    private LocalDateTime parsingEndTime;

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

    private String notes;

    private LocalDateTime createdAt;



    @com.baomidou.mybatisplus.annotation.TableField(exist = false)

    private java.util.List<String> completedStages;

}
