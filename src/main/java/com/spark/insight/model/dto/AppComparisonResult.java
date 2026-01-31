package com.spark.insight.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class AppComparisonResult {
    private String appId1;
    private String appId2;
    private List<ConfigDiff> configDiffs;
    private List<StageDiff> stageDiffs;
}
