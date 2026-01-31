package com.spark.insight.model.dto;

import lombok.Data;

@Data
public class StageDiff {
    private String stageName;
    private Integer stageId1;
    private Integer stageId2;
    
    // 指标对比 (Value1, Value2, DeltaPercent)
    private Long durationP95_1;
    private Long durationP95_2;
    private Double durationDelta;

    private Long gcTime1;
    private Long gcTime2;
    private Double gcDelta;

    private Long shuffleRead1;
    private Long shuffleRead2;
}
