package com.fluffyeti.spark.performance.insight.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Disabled due to DuckDB native library conflicts during CI execution. Run manually if needed.")
class PipelinePerformanceWatchdogTest {

    @Autowired
    private EventLogWatcherService eventLogWatcherService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- Performance Baselines (established on baseline run) ---
    private static final long MAX_TOTAL_MS = 35000;  
    private static final long MAX_BRONZE_MS = 18000; 
    private static final long MAX_SILVER_MS = 12000; 
    private static final long MAX_GOLD_MS = 6000;    

    @Test
    @DisplayName("Performance Guard: Medallion Pipeline Layer Latency Audit")
    void performanceAudit() throws InterruptedException {
        String appId = "application_1771341615593_0001";
        
        StopWatch sw = new StopWatch("Medallion Pipeline Audit");
        CountDownLatch latch = new CountDownLatch(1);

        sw.start("Full Execution");
        eventLogWatcherService.executePipeline(appId, "FULL", (success) -> {
            sw.stop();
            latch.countDown();
        });

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Extract layer timings from lifecycle logs
        long bronzeTime = getLayerDuration(appId, "Bronze Start", "Bronze Finished");
        long silverTime = getLayerDuration(appId, "Silver Start", "Silver Finished");
        long goldTime = getLayerDuration(appId, "Gold Start", "Gold Finished");

        log.info("\n--- Medallion Performance Report ---" +
                "\nTotal Time:  {} ms (Limit: {}ms)" +
                "\nBronze (IO): {} ms (Limit: {}ms)" +
                "\nSilver (Map):{} ms (Limit: {}ms)" +
                "\nGold (Agg):  {} ms (Limit: {}ms)" +
                "\n---------------------------------",
                sw.getTotalTimeMillis(), MAX_TOTAL_MS,
                bronzeTime, MAX_BRONZE_MS,
                silverTime, MAX_SILVER_MS,
                goldTime, MAX_GOLD_MS);

        // Assertions to prevent regression
        assertThat(bronzeTime).as("Bronze layer is too slow!").isLessThan(MAX_BRONZE_MS);
        assertThat(silverTime).as("Silver layer is too slow!").isLessThan(MAX_SILVER_MS);
        assertThat(goldTime).as("Gold layer is too slow!").isLessThan(MAX_GOLD_MS);
        assertThat(sw.getTotalTimeMillis()).as("Overall pipeline is too slow!").isLessThan(MAX_TOTAL_MS);
    }

    private long getLayerDuration(String appId, String startEventName, String endEventName) {
        String sql = "SELECT created_at FROM sys_application_logs WHERE app_id = ? AND event_name = ? ORDER BY created_at DESC LIMIT 1";
        try {
            java.sql.Timestamp start = jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class, appId, startEventName);
            java.sql.Timestamp end = jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class, appId, endEventName);
            if (start != null && end != null) {
                return end.getTime() - start.getTime();
            }
        } catch (Exception e) {
            log.warn("Could not calculate duration for {} -> {}: {}", startEventName, endEventName, e.getMessage());
        }
        return 0;
    }
}
