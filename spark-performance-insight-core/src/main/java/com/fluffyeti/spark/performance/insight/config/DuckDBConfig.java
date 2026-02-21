package com.fluffyeti.spark.performance.insight.config;

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
    private final SystemProperties systemProperties;

    private static boolean initialized = false;

    // @PostConstruct removed to avoid DuckDB native library conflicts during frequent Spring context refreshes in tests
    public synchronized void initDuckDB() {
        if (initialized) {
            return;
        }
        applyDuckDBSettings();
        initialized = true;
    }

    public void applyDuckDBSettings() {
        SystemProperties.DuckDB duckdb = systemProperties.getDuckdb();
        if (duckdb.getThreads() == null && duckdb.getMemoryLimit() == null && duckdb.getTempDirectory() == null) {
            return;
        }

        log.info("Initializing/Applying DuckDB settings...");
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
            
            // Forces data to disk and helps clear buffer manager
            stmt.execute("CHECKPOINT");
            
        } catch (Exception e) {
            log.error("Failed to initialize DuckDB settings", e);
        }
    }

    /**
     * Attempts to "restart" DuckDB by shrinking memory and re-applying limits.
     * In DuckDB JDBC, we can't easily kill the engine without closing all connections,
     * but we can force memory release.
     */
    public void forceMemoryRelease() {
        log.warn("FORCING DuckDB checkpoint and memory limit re-application...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT");
            applyDuckDBSettings();
        } catch (Exception e) {
            log.error("Failed to force memory release", e);
        }
    }
}
