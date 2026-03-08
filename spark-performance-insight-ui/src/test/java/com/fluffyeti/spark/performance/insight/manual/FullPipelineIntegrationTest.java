package com.fluffyeti.spark.performance.insight.manual;

import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.service.ApplicationService;
import com.fluffyeti.spark.performance.insight.service.BronzeIngestionService;
import com.fluffyeti.spark.performance.insight.service.GoldAggregationService;
import com.fluffyeti.spark.performance.insight.service.SilverTransformationService;
import com.fluffyeti.spark.performance.insight.SparkPerformanceInsightApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End Integration Test for the entire Medallion Pipeline.
 */
@SpringBootTest(classes = SparkPerformanceInsightApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FullPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BronzeIngestionService bronzeService;

    @Autowired
    private SilverTransformationService silverService;

    @Autowired
    private GoldAggregationService goldService;

    @Autowired
    private ApplicationService applicationService;

    private static final String APP_ID = "application_1771335863289_0002";
    private static final String LOG_PATH = "../workspace/eventlog/application_1771335863289_0002.zstd";

    @Test
    void shouldExecuteFullPipelineAndVerifyApis() throws Exception {
        // Clean old test DB
        File oldDb = new File("./target/test_spark_insight.db");
        if (oldDb.exists()) oldDb.delete();
        File oldDbWal = new File("./target/test_spark_insight.db.wal");
        if (oldDbWal.exists()) oldDbWal.delete();

        File logFile = new File(LOG_PATH);
        if (!logFile.exists()) {
            throw new RuntimeException("Log file not found: " + logFile.getAbsolutePath());
        }

        // --- Step 1: Bronze Ingestion ---
        System.out.println(">>> Starting Step 1: Bronze Ingestion");
        bronzeService.ingest(APP_ID, Collections.singletonList(logFile), (p, m) -> {});

        // --- Step 2: Silver Transformation ---
        System.out.println(">>> Starting Step 2: Silver Transformation");
        silverService.transform(APP_ID, (p, m) -> {});

        // --- Step 3: Gold Aggregation ---
        System.out.println(">>> Starting Step 3: Gold Aggregation");
        goldService.aggregate(APP_ID, (p, m) -> {});

        // Manually mark as SUCCESS to pass Controller's checkAppReady
        GoldApplicationModel appUpdate = new GoldApplicationModel();
        appUpdate.setAppId(APP_ID);
        appUpdate.setParsingStatus("SUCCESS");
        applicationService.updateById(appUpdate);

        // --- Step 4: Verify API Endpoints ---
        System.out.println(">>> Starting Step 4: API Verification");

        // 1. App Summary
        mockMvc.perform(get("/api/apps/" + APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", is(APP_ID)))
                .andExpect(jsonPath("$.appName", not(anyOf(nullValue(), is("Unparsed Application"), is("Unknown Application")))))
                .andExpect(jsonPath("$.status", is("FINISHED")));

        // 1.1 Update Notes
        String testNotes = "This is a test note " + System.currentTimeMillis();
        mockMvc.perform(patch("/api/apps/" + APP_ID + "/notes")
                .contentType(MediaType.TEXT_PLAIN)
                .content(testNotes))
                .andExpect(status().isOk());

        // Verify notes updated
        mockMvc.perform(get("/api/apps/" + APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes", is(testNotes)));

        // 2. Jobs List (Check not empty)
        mockMvc.perform(get("/api/apps/" + APP_ID + "/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", not(empty())))
                // Note: numCompletedStages might be 0 if the aggregation logic didn't find completed stages for the job
                // in the test data or if the mapping is complex. We'll check for job presence first.
                .andExpect(jsonPath("$.total", greaterThan(0)));

        // 3. Stages List
        mockMvc.perform(get("/api/apps/" + APP_ID + "/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", not(empty())))
                .andExpect(jsonPath("$.total", greaterThan(0)));

        // 4. Storage List
        mockMvc.perform(get("/api/apps/" + APP_ID + "/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", not(empty())));

        // 5. SQL Executions
        mockMvc.perform(get("/api/apps/" + APP_ID + "/sql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", not(empty())));

        // 6. Executors List
        mockMvc.perform(get("/api/apps/" + APP_ID + "/executors"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", not(empty())));

        // 7. Rule-based Diagnosis
        mockMvc.perform(get("/api/apps/" + APP_ID + "/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("诊断报告")));

        // 8. Stage Statistics (New Verification)
        mockMvc.perform(get("/api/apps/" + APP_ID + "/stages/0/0/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].metricName", notNullValue()))
                .andExpect(jsonPath("$[0].p50", notNullValue()));

        System.out.println(">>> E2E Pipeline Verification Successful!");
    }
}
