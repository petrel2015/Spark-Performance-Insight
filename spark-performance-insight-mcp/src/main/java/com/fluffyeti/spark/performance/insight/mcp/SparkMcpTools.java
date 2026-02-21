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
        private ApplicationInsight insight;
    }

    public SubmissionResponse submit_spark_analysis(String path) {
        log.info("MCP: submit_spark_analysis path={}", path);
        File file = new File(path);
        if (!file.exists()) throw new IllegalArgumentException("Path does not exist: " + path);

        String appId = eventLogWatcherService.inferAppId(file.getName());
        if (appId == null) throw new IllegalArgumentException("Could not infer App ID.");

        parsingQueueService.submit(appId, "FULL");
        
        return SubmissionResponse.builder()
                .appId(appId)
                .status("SUBMITTED")
                .message("Analysis queued.")
                .build();
    }

    public StatusResponse get_analysis_status(String appId) {
        log.info("MCP: get_analysis_status appId={}", appId);
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            return StatusResponse.builder().appId(appId).status("NOT_FOUND").build();
        }

        StatusResponse.StatusResponseBuilder builder = StatusResponse.builder()
                .appId(appId)
                .status(app.getParsingStatus())
                .progress(app.getParsingProgressValue())
                .progressText(app.getParsingProgress());

        if ("SUCCESS".equals(app.getParsingStatus())) {
            builder.insight(diagnosisService.computeApplicationInsight(appId));
        }

        return builder.build();
    }
}
