package com.fluffyeti.spark.performance.insight.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("IntegrationTest")
class AllMappersIntegrationTest {

    @Autowired private StageMapper stageMapper;
    @Autowired private JobMapper jobMapper;
    @Autowired private SqlExecutionMapper sqlExecutionMapper;
    @Autowired private ExecutorMapper executorMapper;

    @Test
    @DisplayName("Verify StageMapper complex aggregation SQLs")
    void verifyStageMapper() {
        stageMapper.updateStageMetrics("test-app");
        stageMapper.deleteStageStats("test-app");
        stageMapper.insertTaskStats("test-app");
    }

    @Test
    @DisplayName("Verify JobMapper SQLs")
    void verifyJobMapper() {
        jobMapper.calculateJobMetrics("test-app");
    }

    @Test
    @DisplayName("Verify SqlExecutionMapper SQLs")
    void verifySqlMapper() {
        sqlExecutionMapper.calculateSqlMetrics("test-app");
    }

    @Test
    @DisplayName("Verify ExecutorMapper SQLs")
    void verifyExecutorMapper() {
        executorMapper.updateExecutorMetrics("test-app");
    }
}
