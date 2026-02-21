package com.fluffyeti.spark.performance.insight.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Structured performance insight for an application.
 * Designed to be both human-readable (via Markdown) and machine-readable (via MCP/JSON).
 */
@Data
@Builder
public class ApplicationInsight {
    private AppMetadata appMetadata;
    private List<Bottleneck> criticalBottlenecks;
    private List<JobScore> topImpactJobs;
    private EfficiencyMetrics efficiencyMetrics;
    private DataQualityHint dataQuality;

    @Data
    @Builder
    public static class AppMetadata {
        private String appId;
        private String name;
        private Long durationMs;
        private Double healthScore;
    }

    @Data
    @Builder
    public static class Bottleneck {
        public enum Type { DATA_SKEW, DISK_SPILL, MEMORY_PRESSURE, LOCALITY_ISSUE, SHUFFLE_IO_LIMIT }
        
        private Type type;
        private String severity; // HIGH, MEDIUM, LOW
        private Integer stageId;
        private String stageName;
        private String description;
        private Map<String, Object> metrics; // Raw numbers for LLM reasoning
    }

    @Data
    @Builder
    public static class JobScore {
        private Integer jobId;
        private String description;
        private Double score;
        private Long durationMs;
    }

    @Data
    @Builder
    public static class EfficiencyMetrics {
        private Double cpuUtilization;
        private Double gcTimeRatio;
        private Long totalInputBytes;
        private Long totalShuffleReadBytes;
    }

    @Data
    @Builder
    public static class DataQualityHint {
        private boolean hasEndEvent;
        private String note;
    }
}
