package com.fluffyeti.spark.performance.insight.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutorDiagnosisDTO {
    private List<Anomaly> anomalies;
    private List<ExecutorMetrics> metrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Anomaly {
        private String executorId;
        private String metric;
        private String reason;
        private Double value;
        private Double median;
        private Double ratio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutorMetrics {
        private String executorId;
        private Double taskThroughput;
        private Double hdfsIoRate;
        private Double shuffleIoRate;
    }
}
