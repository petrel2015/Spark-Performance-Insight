package com.fluffyeti.spark.performance.insight.mcp;

import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.dto.ApplicationInsight;
import com.fluffyeti.spark.performance.insight.service.ApplicationService;
import com.fluffyeti.spark.performance.insight.service.DiagnosisService;
import com.fluffyeti.spark.performance.insight.service.EventLogWatcherService;
import com.fluffyeti.spark.performance.insight.service.ParsingQueueService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@RequiredArgsConstructor
@Slf4j
public class SparkMcpTools {

    private final EventLogWatcherService eventLogWatcherService;
    private final ParsingQueueService parsingQueueService;
    private final DiagnosisService diagnosisService;
    private final ApplicationService applicationService;

    @Data
    @Builder
    public static class SubmissionResponse {
        private String appId;
        private String status;
        private String message;
    }

    @Data
    @Builder
    public static class StatusResponse {
        private String appId;
        private String status;
        private Double progress;
        private String progressText;
        private ApplicationInsight insight; // Only present if status is SUCCESS or READY
    }

    /**
     * MCP Tool to submit a Spark EventLog for analysis.
     * 
     * @param path Full path to the Spark EventLog file or directory.
     * @return Submission details including the appId.
     */
    @Tool(description = "Submit a Spark EventLog for background analysis. Returns an appId to track progress.")
    public SubmissionResponse submit_spark_analysis(String path) {
        log.info("MCP Request: submit_spark_analysis with path: {}", path);
        
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Path does not exist: " + path);
        }

        String appId = eventLogWatcherService.inferAppId(file.getName());
        if (appId == null) {
            throw new IllegalArgumentException("Could not infer Spark App ID from filename: " + file.getName());
        }

        // Trigger asynchronous parsing via the existing queue system
        parsingQueueService.submit(appId, "FULL");
        
        return SubmissionResponse.builder()
                .appId(appId)
                .status("SUBMITTED")
                .message("Analysis job has been queued. Use get_analysis_status to check progress.")
                .build();
    }

    /**
     * MCP Tool to check the status or get results of a Spark analysis.
     * 
     * @param appId The application ID returned by submit_spark_analysis.
     * @return Current status, progress, and performance insights (if ready).
     */
    @Tool(description = "Check the status of a Spark analysis job and get performance insights once completed.")
    public StatusResponse get_analysis_status(String appId) {
        log.info("MCP Request: get_analysis_status for appId: {}", appId);
        
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            return StatusResponse.builder()
                    .appId(appId)
                    .status("NOT_FOUND")
                    .progressText("Application ID not found in system.")
                    .build();
        }

        StatusResponse.StatusResponseBuilder builder = StatusResponse.builder()
                .appId(appId)
                .status(app.getParsingStatus())
                .progress(app.getParsingProgressValue())
                .progressText(app.getParsingProgress());

        // If the analysis is finished (SUCCESS) or already available (READY), provide the insight
        if ("SUCCESS".equals(app.getParsingStatus()) || "READY".equals(app.getParsingStatus()) || "GOLD".equals(app.getParsingStatus())) {
            ApplicationInsight insight = diagnosisService.computeApplicationInsight(appId);
            builder.insight(insight);
        }

        return builder.build();
    }
}
