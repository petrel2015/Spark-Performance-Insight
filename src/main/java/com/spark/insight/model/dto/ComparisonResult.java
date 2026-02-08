package com.spark.insight.model.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComparisonResult {
    private String type; // "JOB" or "STAGE"
    
    // 基本元数据
    private ItemMeta source;
    private ItemMeta target;
    
    // 核心结论
    private String conclusion; // e.g. "Performance Regressed by 25%"
    private String conclusionType; // "IMPROVED", "REGRESSED", "SIMILAR"
    
    // 关键指标差异 (Key Metrics Diff)
    private List<MetricDiff> keyMetrics;
    
    // 资源/配置差异 (仅当相关时)
    private List<ConfigDiff> configDiffs;
    
    // (仅 Stage 对比) 任务分布差异
    private TaskSkewDiff skewDiff;
    
    // (仅 Job 对比) 包含的 Stages 列表对比
    private List<StageRefDiff> stageListDiff;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ItemMeta {
        private String id;
        private String name;
        private String appId;
        private Long duration;
        private Long startTime;
        private Integer stageCount;
        private Integer taskCount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MetricDiff {
        private String name;
        private String label;
        private String unit; // "ms", "bytes", "count"
        private Double sourceValue;
        private Double targetValue;
        private Double delta;     // target - source
        private Double pctChange; // (target - source) / source * 100
        private String severity;  // "CRITICAL", "WARNING", "GOOD", "NEUTRAL"
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfigDiff {
        private String category;
        private String key;
        private String sourceValue;
        private String targetValue;
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskSkewDiff {
        private Double sourceP75;
        private Double targetP75;
        private Double sourceP95;
        private Double targetP95;
        private Double sourceMax;
        private Double targetMax;
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StageRefDiff {
        private Integer stageId;
        private String name;
        private Long sourceDuration;
        private Long targetDuration;
        private Double durationDelta;
    }
}
