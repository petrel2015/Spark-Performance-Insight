package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.config.DuckDBConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuckDBManagerService {

    private final JdbcTemplate jdbcTemplate;
    private final DuckDBConfig duckDBConfig;
    private final DataSource dataSource;

    /**
     * Executes a database operation with automatic OOM detection and retry.
     */
    public <T> T executeWithRetry(Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            if (isOOMError(e)) {
                log.warn("Detected DuckDB Out of Memory Error. Attempting to release memory and retry once...");
                duckDBConfig.forceMemoryRelease();
                try {
                    return action.get();
                } catch (Exception e2) {
                    log.error("Retry failed after DuckDB memory release", e2);
                    throw e2;
                }
            }
            throw e;
        }
    }

    /**
     * Specialized for void actions (like jdbcTemplate.update).
     */
    public void runWithRetry(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            if (isOOMError(e)) {
                log.warn("Detected DuckDB Out of Memory Error. Attempting to release memory and retry once...");
                duckDBConfig.forceMemoryRelease();
                try {
                    action.run();
                } catch (Exception e2) {
                    log.error("Retry failed after DuckDB memory release", e2);
                    throw e2;
                }
            } else {
                throw e;
            }
        }
    }

    /**
     * Executes a functional interface with a raw DuckDBConnection.
     * Useful for using native features like DuckDBAppender.
     */
    public <T> T executeNative(Function<org.duckdb.DuckDBConnection, T> action) {
        return executeWithRetry(() -> {
            try (Connection conn = dataSource.getConnection()) {
                org.duckdb.DuckDBConnection nativeConn = conn.unwrap(org.duckdb.DuckDBConnection.class);
                return action.apply(nativeConn);
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute native DuckDB action", e);
            }
        });
    }

    private boolean isOOMError(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        
        boolean isOom = msg.contains("Out of Memory") || 
                       msg.contains("could not allocate block") || 
                       msg.contains("Memory limit reached");
        
        if (!isOom && e.getCause() != null) {
            return isOOMError(e.getCause());
        }
        return isOom;
    }
}
