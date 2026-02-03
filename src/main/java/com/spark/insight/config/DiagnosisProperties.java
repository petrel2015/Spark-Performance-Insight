package com.spark.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "insight.diagnosis")
public class DiagnosisProperties {
    /**
     * Data Skew: Minimum max task duration to trigger skew warning (ms).
     * Default: 1000ms (1s)
     */
    private long skewMinDurationMs = 1000;

    /**
     * GC Pressure: Minimum GC time for a single task to trigger warning (ms).
     * Default: 800ms
     */
    private long gcMinDurationMs = 800;
}
