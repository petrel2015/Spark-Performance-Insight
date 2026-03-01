package com.fluffyeti.spark.performance.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "insight")
public class SystemProperties {
    /**
     * Directory containing Spark Event Logs.
     */
    private String eventLogPath = "./eventlogs";

    /**
     * Scheduler configuration for log scanning.
     */
    private Scheduler scheduler = new Scheduler();

    /**
     * Comparison configuration.
     */
    private Comparison comparison = new Comparison();

    /**
     * DuckDB configuration.
     */
    private DuckDB duckdb = new DuckDB();

    /**
     * Transformation configuration for thread pool.
     */
    private Transformation transformation = new Transformation();

    /**
     * Ingestion configuration.
     */
    private Ingestion ingestion = new Ingestion();

    @Data
    public static class Transformation {
        /**
         * Number of threads for gold aggregation. Silver is now single-threaded batch mode.
         */
        private int threads = 1;

        /**
         * Number of stages to process in a single SQL batch during Silver transformation.
         */
        private int stageBatchSize = 500;
    }

    @Data
    public static class Scheduler {
        /**
         * Enable scheduled scanning.
         */
        private boolean enabled = true;
    }

    @Data
    public static class Comparison {
        /**
         * List of environment config categories to ignore during comparison.
         */
        private java.util.List<String> ignoreCategories = new java.util.ArrayList<>();
    }

    @Data
    public static class DuckDB {
        /**
         * Number of threads DuckDB can use.
         */
        private Integer threads;

        /**
         * Memory limit for DuckDB (e.g., "2GB").
         */
        private String memoryLimit;

        /**
         * Temporary directory for DuckDB to avoid memory overflow.
         */
        private String tempDirectory = "out/spark_performance_insight.duckdb.db.tmp";
    }

    @Data
    public static class Ingestion {
        /**
         * Batch size for processing SQL lines during ingestion to prevent OOM.
         */
        private int batchSize = 50000;
    }
}
