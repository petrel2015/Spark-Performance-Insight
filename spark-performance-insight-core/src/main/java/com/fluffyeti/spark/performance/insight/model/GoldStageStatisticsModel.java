package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_stage_statistics")
public class GoldStageStatisticsModel {
    @TableId
    private String id;
    private String appId;
    private Long stageId;
    private Long attemptId;
    private String metricName;
    private Long minValue;
    private Long p25;
    private Long p50;
    private Long p75;
    private Long p95;
    private Long maxValue;
}
