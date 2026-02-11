package com.spark.insight.service;

import com.spark.insight.model.JobModel;
import com.spark.insight.model.StageModel;
import com.spark.insight.model.dto.ComparisonResult;
import com.spark.insight.model.dto.ComparisonResult.*;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final JobService jobService;
    private final EnvironmentConfigService envService;
    private final TaskService taskService;

    @Nullable
    public ComparisonResult compare(String type, String appId1, String id1, String appId2, String id2) {
        if ("stage".equalsIgnoreCase(type)) {
            return compareStages(appId1, Integer.parseInt(id1), appId2, Integer.parseInt(id2));
        } else if ("job".equalsIgnoreCase(type)) {
            return compareJobs(appId1, Integer.parseInt(id1), appId2, Integer.parseInt(id2));
        } else if ("app".equalsIgnoreCase(type)) {
            return compareApplications(appId1, appId2);
        }
        throw new IllegalArgumentException("Unknown comparison type: " + type);
    }

    private ComparisonResult compareApplications(String appId1, String appId2) {
        com.spark.insight.model.ApplicationModel app1 = applicationService.getById(appId1);
        com.spark.insight.model.ApplicationModel app2 = applicationService.getById(appId2);

        if (app1 == null || app2 == null) {
            throw new RuntimeException("One or both applications not found.");
        }

        List<MetricDiff> metrics = new ArrayList<>();
        addMetric(metrics, "Total Duration", "duration", "ms", (double) app1.getDuration(), (double) app2.getDuration(), true);

        double durationDiffPercent = getPctChange((double) app1.getDuration(), (double) app2.getDuration());
        String conclusionType = durationDiffPercent > 10 ? "REGRESSED" : (durationDiffPercent < -10 ? "IMPROVED" : "SIMILAR");

        return ComparisonResult.builder()
                .type("APPLICATION")
                .source(buildMeta(appId1, "APP", appId1, app1.getAppName(), app1.getDuration(), null, null))
                .target(buildMeta(appId2, "APP", appId2, app2.getAppName(), app2.getDuration(), null, null))
                .keyMetrics(metrics)
                .configDiffs(fetchResourceConfigs(appId1, appId2))
                .conclusion(String.format("Application duration change: %.1f%%", durationDiffPercent))
                .conclusionType(conclusionType)
                .build();
    }

    private ComparisonResult compareStages(String appId1, int stageId1, String appId2, int stageId2) {
        StageModel stage1 = stageService.getStage(appId1, stageId1, 0);
        StageModel stage2 = stageService.getStage(appId2, stageId2, 0);

        if (stage1 == null || stage2 == null) {
            throw new RuntimeException("One or both stages not found.");
        }

        List<MetricDiff> metrics = new ArrayList<>();
        
        // 1. Core Performance
        addMetric(metrics, "Duration", "duration", "ms", (double) stage1.getDuration(), (double) stage2.getDuration(), true);
        addMetric(metrics, "GC Time", "gc_time", "ms", (double) stage1.getGcTimeSum(), (double) stage2.getGcTimeSum(), true);
        
        // 2. Resource Overhead (Spill)
        addMetric(metrics, "Disk Spill", "disk_spill", "bytes", (double) stage1.getDiskBytesSpilledSum(), (double) stage2.getDiskBytesSpilledSum(), true);
        addMetric(metrics, "Memory Spill", "mem_spill", "bytes", (double) stage1.getMemoryBytesSpilledSum(), (double) stage2.getMemoryBytesSpilledSum(), true);

        // 3. I/O & Shuffle
        addMetric(metrics, "Input Size", "input", "bytes", (double) stage1.getInputBytes(), (double) stage2.getInputBytes(), false);
        // Treat shuffle increase as generally negative for performance stability
        addMetric(metrics, "Shuffle Read", "shuffle_read", "bytes", (double) stage1.getShuffleReadBytes(), (double) stage2.getShuffleReadBytes(), true);
        addMetric(metrics, "Shuffle Write", "shuffle_write", "bytes", (double) stage1.getShuffleWriteBytes(), (double) stage2.getShuffleWriteBytes(), true);
        
        // 4. Concurrency (Executors)
        long executors1 = taskService.getExecutorCountForStage(appId1, stageId1);
        long executors2 = taskService.getExecutorCountForStage(appId2, stageId2);
        addMetric(metrics, "Executors Involved", "exec_count", "count", (double) executors1, (double) executors2, false);

        // Analyze Conclusion
        double durationDiffPercent = getPctChange((double) stage1.getDuration(), (double) stage2.getDuration());
        String conclusionType = "SIMILAR";
        String conclusion = "Performance is stable.";
        
        if (durationDiffPercent > 20) {
            conclusionType = "REGRESSED";
            conclusion = String.format("Performance degraded by %.1f%%. ", durationDiffPercent);
            if (stage2.getDiskBytesSpilledSum() > stage1.getDiskBytesSpilledSum() * 1.5) {
                conclusion += "Significant Disk Spill increase detected.";
            } else if (executors2 < executors1) {
                conclusion += "Fewer executors were involved in the target run.";
            }
        } else if (durationDiffPercent < -20) {
            conclusionType = "IMPROVED";
            conclusion = String.format("Performance improved by %.1f%%.", Math.abs(durationDiffPercent));
        }

        return ComparisonResult.builder()
                .type("STAGE")
                .source(buildMeta(appId1, "STAGE", String.valueOf(stageId1), stage1.getStageName(), stage1.getDuration(), null, stage1.getNumTasks()))
                .target(buildMeta(appId2, "STAGE", String.valueOf(stageId2), stage2.getStageName(), stage2.getDuration(), null, stage2.getNumTasks()))
                .keyMetrics(metrics)
                .configDiffs(fetchResourceConfigs(appId1, appId2))
                .conclusion(conclusion)
                .conclusionType(conclusionType)
                .build();
    }

    private ComparisonResult compareJobs(String appId1, int jobId1, String appId2, int jobId2) {
        JobModel job1 = jobService.getJob(appId1, jobId1);
        JobModel job2 = jobService.getJob(appId2, jobId2);

        if (job1 == null || job2 == null) {
            throw new RuntimeException("One or both jobs not found.");
        }

        List<MetricDiff> metrics = new ArrayList<>();
        addMetric(metrics, "Duration", "duration", "ms", (double) job1.getDuration(), (double) job2.getDuration(), true);
        
        long executors1 = taskService.getExecutorCountForJob(appId1, jobId1);
        long executors2 = taskService.getExecutorCountForJob(appId2, jobId2);
        addMetric(metrics, "Executors Involved", "exec_count", "count", (double) executors1, (double) executors2, false);
        
        addMetric(metrics, "Stages Count", "stages", "count", (double) job1.getNumStages(), (double) job2.getNumStages(), false);
        addMetric(metrics, "Tasks Count", "tasks", "count", (double) job1.getNumTasks(), (double) job2.getNumTasks(), false);

        double durationDiffPercent = getPctChange((double) job1.getDuration(), (double) job2.getDuration());
        String conclusionType = durationDiffPercent > 10 ? "REGRESSED" : (durationDiffPercent < -10 ? "IMPROVED" : "SIMILAR");
        
        return ComparisonResult.builder()
                .type("JOB")
                .source(buildMeta(appId1, "JOB", String.valueOf(jobId1), job1.getDescription(), job1.getDuration(), job1.getNumStages(), job1.getNumTasks()))
                .target(buildMeta(appId2, "JOB", String.valueOf(jobId2), job2.getDescription(), job2.getDuration(), job2.getNumStages(), job2.getNumTasks()))
                .keyMetrics(metrics)
                .configDiffs(fetchResourceConfigs(appId1, appId2))
                .conclusion(String.format("Job duration change: %.1f%%", durationDiffPercent))
                .conclusionType(conclusionType)
                .build();
    }

    private ItemMeta buildMeta(String appId, String type, String id, String name, Long duration, Integer stages, Integer tasks) {
        return ItemMeta.builder()
                .id(id)
                .name(name)
                .appId(appId)
                .duration(duration)
                .stageCount(stages)
                .taskCount(tasks)
                .build();
    }

    private List<ConfigDiff> fetchResourceConfigs(String appId1, String appId2) {
        List<ConfigDiff> diffs = new ArrayList<>();
        // Fetch all configs
        List<com.spark.insight.model.EnvironmentConfigModel> list1 = envService.lambdaQuery().eq(com.spark.insight.model.EnvironmentConfigModel::getAppId, appId1).list();
        List<com.spark.insight.model.EnvironmentConfigModel> list2 = envService.lambdaQuery().eq(com.spark.insight.model.EnvironmentConfigModel::getAppId, appId2).list();
        
        // Use category + key as the map key to avoid collisions
        java.util.Map<String, com.spark.insight.model.EnvironmentConfigModel> map1 = list1.stream().collect(java.util.stream.Collectors.toMap(
            config -> config.getCategory() + "||" + config.getParamKey(), 
            config -> config, 
            (configA, configB) -> configA));
        java.util.Map<String, com.spark.insight.model.EnvironmentConfigModel> map2 = list2.stream().collect(java.util.stream.Collectors.toMap(
            config -> config.getCategory() + "||" + config.getParamKey(), 
            config -> config, 
            (configA, configB) -> configA));
        
        java.util.Set<String> allCompositeKeys = new java.util.HashSet<>();
        allCompositeKeys.addAll(map1.keySet());
        allCompositeKeys.addAll(map2.keySet());
        
        for (String compositeKey : allCompositeKeys) {
            com.spark.insight.model.EnvironmentConfigModel envConfig1 = map1.get(compositeKey);
            com.spark.insight.model.EnvironmentConfigModel envConfig2 = map2.get(compositeKey);
            
            String value1 = envConfig1 != null ? envConfig1.getParamValue() : null;
            String value2 = envConfig2 != null ? envConfig2.getParamValue() : null;
            
            // Only add if they are different (one null, one not, or values unequal)
            boolean different = (value1 == null && value2 != null) || (value1 != null && value2 == null) || (value1 != null && !value1.equals(value2));
            if (different) {
                String category = envConfig1 != null ? envConfig1.getCategory() : envConfig2.getCategory();
                String key = envConfig1 != null ? envConfig1.getParamKey() : envConfig2.getParamKey();
                
                diffs.add(ConfigDiff.builder()
                        .category(category)
                        .key(key)
                        .sourceValue(value1 != null ? value1 : "N/A")
                        .targetValue(value2 != null ? value2 : "N/A")
                        .build());
            }
        }
        
        // Sort by category then key
        diffs.sort(java.util.Comparator.comparing(ConfigDiff::getCategory).thenComparing(ConfigDiff::getKey));
        
        return diffs;
    }

    private void addMetric(List<MetricDiff> metricsList, String label, String name, String unit, Double value1, Double value2, boolean lowerIsBetter) {
        if (value1 == null) {
            value1 = 0.0;
        }
        if (value2 == null) {
            value2 = 0.0;
        }
        
        double delta = value2 - value1;
        double percentChange = getPctChange(value1, value2);
        
        String severity = "NEUTRAL";
        if (lowerIsBetter) {
            if (percentChange > 50) {
                severity = "CRITICAL";
            } else if (percentChange > 20) {
                severity = "WARNING";
            } else if (percentChange < -10) {
                severity = "GOOD";
            }
        } else {
            // For neutral metrics, still highlight huge changes
            if (Math.abs(percentChange) > 50) {
                severity = "WARNING"; // Use WARNING color for big changes
            }
        }

        metricsList.add(MetricDiff.builder()
                .label(label)
                .name(name)
                .unit(unit)
                .sourceValue(value1)
                .targetValue(value2)
                .delta(delta)
                .pctChange(percentChange)
                .severity(severity)
                .build());
    }

    private double getPctChange(Double value1, Double value2) {
        if (value1 == null || value1 == 0) {
            return value2 > 0 ? 100.0 : 0.0;
        }
        return (value2 - value1) / value1 * 100.0;
    }
}
