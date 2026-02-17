package com.spark.insight.service;

import com.spark.insight.config.DuckDBConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuckDBManagerService {

    private final JdbcTemplate jdbcTemplate;
    private final DuckDBConfig duckDBConfig;

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
