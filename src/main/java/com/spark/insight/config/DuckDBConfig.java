package com.spark.insight.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DuckDBConfig {

    private final DataSource dataSource;
    private final InsightProperties insightProperties;

    @PostConstruct
    public void initDuckDB() {
        InsightProperties.DuckDB duckdb = insightProperties.getDuckdb();
        if (duckdb.getThreads() == null && duckdb.getMemoryLimit() == null && duckdb.getTempDirectory() == null) {
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            if (duckdb.getThreads() != null) {
                log.info("Setting DuckDB threads to {}", duckdb.getThreads());
                stmt.execute("SET threads TO " + duckdb.getThreads());
            }
            
            if (duckdb.getMemoryLimit() != null) {
                log.info("Setting DuckDB memory_limit to {}", duckdb.getMemoryLimit());
                stmt.execute("SET memory_limit TO '" + duckdb.getMemoryLimit() + "'");
            }

            if (duckdb.getTempDirectory() != null) {
                log.info("Setting DuckDB temp_directory to {}", duckdb.getTempDirectory());
                java.io.File tempDir = new java.io.File(duckdb.getTempDirectory());
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                stmt.execute("SET temp_directory = '" + duckdb.getTempDirectory() + "'");
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize DuckDB settings", e);
        }
    }
}
