package com.spark.insight.controller;

import com.spark.insight.config.InsightProperties;
import com.spark.insight.service.BronzeIngestionService;
import com.spark.insight.service.SilverTransformationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/bronze")
@RequiredArgsConstructor
public class BronzeController {

    private final BronzeIngestionService bronzeIngestionService;
    private final SilverTransformationService silverTransformationService;
    private final InsightProperties properties;

    @PostMapping("/import/{appId}")
    public void importToBronze(@PathVariable String appId) {
        log.info("Triggering Bronze import and Silver transformation for appId: {}", appId);
        
        String logPath = properties.getEventLogPath();
        File directory = new File(logPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Event log directory not found: " + logPath);
        }

        List<File> appFiles = findAppFiles(directory, appId);
        if (appFiles.isEmpty()) {
            throw new RuntimeException("No log files found for appId: " + appId);
        }

        // 1. Bronze Ingestion
        bronzeIngestionService.ingest(appId, appFiles);
        
        // 2. Silver Transformation
        silverTransformationService.transform(appId);
        
        log.info("Successfully completed Bronze and Silver pipeline for appId: {}", appId);
    }

    private List<File> findAppFiles(File dir, String appId) {
        List<File> result = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    result.addAll(findAppFiles(file, appId));
                } else if (file.getName().contains(appId) && file.getName().startsWith("event")) {
                    result.add(file);
                }
            }
        }
        return result;
    }
}
