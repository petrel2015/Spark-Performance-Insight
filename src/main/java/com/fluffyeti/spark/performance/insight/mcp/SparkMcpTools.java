package com.fluffyeti.spark.performance.insight.mcp;

import com.fluffyeti.spark.performance.insight.model.dto.ApplicationInsight;
import com.fluffyeti.spark.performance.insight.service.DiagnosisService;
import com.fluffyeti.spark.performance.insight.service.EventLogWatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SparkMcpTools {

    private final EventLogWatcherService eventLogWatcherService;
    private final DiagnosisService diagnosisService;

    /**
     * MCP Tool to analyze a Spark EventLog and return structured performance insights.
     * 
     * @param path Full path to the Spark EventLog file (e.g. .zstd) or directory (V2 logs).
     * @return A structured ApplicationInsight JSON object.
     */
    @Tool(description = "Analyze a Spark EventLog and return structured performance insights. Supports single files (.zstd, plain) and V2 log directories.")
    public ApplicationInsight analyze_spark_application(String path) {
        log.info("MCP Request: analyze_spark_application with path: {}", path);
        
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Path does not exist: " + path);
        }

        // 1. Resolve App ID
        String appId = eventLogWatcherService.inferAppId(file.getName());
        if (appId == null) {
            throw new IllegalArgumentException("Could not infer Spark App ID from filename: " + file.getName());
        }

        // 2. Prepare files for ingestion
        List<File> filesToProcess = new ArrayList<>();
        if (file.isDirectory()) {
            // V2 directory mode
            File[] list = file.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (!f.getName().startsWith(".")) {
                        filesToProcess.add(f);
                    }
                }
            }
        } else {
            filesToProcess.add(file);
        }

        if (filesToProcess.isEmpty()) {
            throw new IllegalArgumentException("No valid log files found at path: " + path);
        }

        // 3. Trigger Pipeline Synchronously
        log.info("Triggering synchronous Medallion pipeline for App: {}", appId);
        boolean success = eventLogWatcherService.executeFullPipelineSync(appId, filesToProcess);
        
        if (!success) {
            throw new RuntimeException("Medallion pipeline failed for App: " + appId);
        }

        // 4. Generate JSON Insight
        log.info("Generating structured insight for App: {}", appId);
        ApplicationInsight insight = diagnosisService.computeApplicationInsight(appId);
        
        if (insight == null) {
            throw new RuntimeException("Diagnosis failed to produce insight for App: " + appId);
        }

        return insight;
    }
}
