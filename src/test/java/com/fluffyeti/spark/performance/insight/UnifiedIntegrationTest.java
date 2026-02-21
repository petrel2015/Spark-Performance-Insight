package com.fluffyeti.spark.performance.insight;

import com.fluffyeti.spark.performance.insight.mapper.*;
import com.fluffyeti.spark.performance.insight.service.*;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified Integration Test Suite.
 * Grouping all database-touching tests into one class to minimize Spring Context refreshes 
 * and prevent DuckDB Native library conflicts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Disabled("Disabled due to DuckDB native library conflicts during CI execution. Run manually if needed.")
class UnifiedIntegrationTest {

    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private StageMapper stageMapper;
    @Autowired private JobMapper jobMapper;
    @Autowired private EventLogWatcherService eventLogWatcherService;
    @Autowired private ApplicationService applicationService;

    @Test
    @DisplayName("Verify All Mappers SQL Compatibility")
    void verifyMappers() {
        // 1. Application
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId("test-app");
        applicationMapper.insert(app);
        assertThat(applicationMapper.selectById("test-app")).isNotNull();

        // 2. Stage
        stageMapper.updateStageMetrics("test-app");
        stageMapper.deleteStageStats("test-app");

        // 3. Job
        jobMapper.calculateJobMetrics("test-app");
    }

    @Test
    @DisplayName("Verify End-to-End Log Parsing")
    void testParsingPipeline() throws InterruptedException {
        String appId = "application_1771335863289_0002";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventLogWatcherService.executePipeline(appId, "FULL", (success) -> {
            latch.countDown();
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        GoldApplicationModel result = applicationService.getById(appId);
        assertThat(result.getParsingStatus()).isEqualTo("SUCCESS");
    }
}
