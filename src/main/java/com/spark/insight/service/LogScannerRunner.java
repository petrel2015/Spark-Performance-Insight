package com.spark.insight.service;

import com.spark.insight.parser.EventParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class LogScannerRunner implements CommandLineRunner {

    @Value("${insight.event-log-path}")
    private String logPath;

    private final EventParser eventParser;

    public LogScannerRunner(EventParser eventParser) {
        this.eventParser = eventParser;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting log scan at path: {}", logPath);
        File dir = new File(logPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("EventLog path does not exist or is not a directory: {}", logPath);
            return;
        }

        // 使用标准线程池以兼容 Java 17
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            scanAndSubmit(dir, executor);
        } catch (Exception e) {
            log.error("Error during log scanning", e);
        } finally {
            executor.shutdown();
        }
        
        log.info("Log scanning and initial import submitted.");
    }

    private void scanAndSubmit(File file, ExecutorService executor) {
        if (file.isDirectory()) {
            log.debug("Scanning directory: {}", file.getPath());
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    scanAndSubmit(f, executor);
                }
            }
        } else if (file.isFile() && !file.getName().startsWith(".")) {
            // 过滤掉一些明显不是 eventlog 的文件，比如 appstatus
            // 支持 Spark Standalone (app-...), History Server (events_...), JSON, ZSTD
            if (file.getName().startsWith("app-") || file.getName().contains("events_") || file.getName().endsWith(".json") || file.getName().endsWith(".zstd") || file.getName().endsWith(".zst")) {
                log.info("Found log file: {}, submitting for parsing", file.getPath());
                executor.submit(() -> {
                    try {
                        eventParser.parse(file);
                    } catch (Exception e) {
                        log.error("Failed to parse file: " + file.getPath(), e);
                    }
                });
            }
        }
    }
}
