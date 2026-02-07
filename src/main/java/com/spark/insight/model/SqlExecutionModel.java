package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sql_executions")
public class SqlExecutionModel {
    @TableId
    private String id; // appId:executionId
    private String appId;
    private Long executionId;
    private String description;
    private String details;
    private String physicalPlan;
    private String planInfo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private String status;
    private Double performanceScore;

    @TableField(exist = false)
    private List<Integer> jobIds;

    @TableField(exist = false)
    private List<JobModel> jobList;
}
