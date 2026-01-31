package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("stage_statistics")
public class StageStatisticsModel {
    @TableId
    private String id;
    private String appId;
    private Integer stageId;
    private Integer attemptId;
    private String metricName;
    private Long minValue;
    private Long p25;
    private Long p50;
    private Long p75;
    private Long p95;
    private Long maxValue;
}
