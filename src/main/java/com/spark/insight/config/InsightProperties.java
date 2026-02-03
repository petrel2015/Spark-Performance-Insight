package com.spark.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "insight")
public class InsightProperties {
    /**
     * Directory containing Spark Event Logs.
     */
    private String eventLogPath = "./eventlogs";

    /**
     * Scheduler configuration for log scanning.
     */
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Scheduler {
        /**
         * Enable scheduled scanning.
         */
        private boolean enabled = true;

        /**
         * Interval in seconds between scans.
         */
        private long scanIntervalSeconds = 10;
    }
}
