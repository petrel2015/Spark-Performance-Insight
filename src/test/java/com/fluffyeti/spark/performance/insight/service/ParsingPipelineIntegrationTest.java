package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ParsingPipelineIntegrationTest {

    @Autowired
    private EventLogWatcherService eventLogWatcherService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private JobService jobService;

    private void runPipelineAndWait(String appId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        eventLogWatcherService.executePipeline(appId, "FULL", (success) -> {
            latch.countDown();
        });
        // Wait up to 60 seconds for larger logs
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertThat(completed).as("Parsing pipeline timed out for app: " + appId).isTrue();
    }

    @Test
    @DisplayName("Should successfully parse a ZSTD compressed single log file")
    void testParseZstdSingleFile() throws InterruptedException {
        String appId = "application_1771335863289_0002";
        runPipelineAndWait(appId);

        // Verify final state
        GoldApplicationModel app = applicationService.getById(appId);
        assertThat(app).isNotNull();
        assertThat(app.getParsingStatus()).isEqualTo("SUCCESS");
        assertThat(app.getAppName()).isNotBlank();

        // Check if jobs are populated in gold layer
        List<GoldJobModel> jobs = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId).list();
        assertThat(jobs).isNotEmpty();
    }

    @Test
    @DisplayName("Should successfully parse an uncompressed single log file")
    void testParseUncompressedSingleFile() throws InterruptedException {
        String appId = "application_1771337832781_0001";
        runPipelineAndWait(appId);

        GoldApplicationModel app = applicationService.getById(appId);
        assertThat(app).isNotNull();
        assertThat(app.getAppName()).isNotBlank();
        assertThat(app.getParsingStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should successfully parse a V2 directory-based log")
    void testParseV2LogDirectory() throws InterruptedException {
        String appId = "application_1771341615593_0001";
        runPipelineAndWait(appId);

        GoldApplicationModel app = applicationService.getById(appId);
        assertThat(app).isNotNull();
        assertThat(app.getAppName()).isNotBlank();
        assertThat(app.getParsingStatus()).isEqualTo("SUCCESS");
    }
}
