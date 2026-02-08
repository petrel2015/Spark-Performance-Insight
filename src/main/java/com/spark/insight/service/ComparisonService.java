package com.spark.insight.service;

import com.spark.insight.model.JobModel;
import com.spark.insight.model.StageModel;
import com.spark.insight.model.dto.ComparisonResult;
import com.spark.insight.model.dto.ComparisonResult.*;
import lombok.RequiredArgsConstructor;
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

    public ComparisonResult compare(String type, String app1, String id1, String app2, String id2) {
        if ("stage".equalsIgnoreCase(type)) {
            return compareStages(app1, Integer.parseInt(id1), app2, Integer.parseInt(id2));
        } else if ("job".equalsIgnoreCase(type)) {
            return compareJobs(app1, Integer.parseInt(id1), app2, Integer.parseInt(id2));
        } else if ("app".equalsIgnoreCase(type)) {
            return compareApplications(app1, app2);
        }
        throw new IllegalArgumentException("Unknown comparison type: " + type);
    }

    private ComparisonResult compareApplications(String app1, String app2) {
        com.spark.insight.model.ApplicationModel a1 = applicationService.getById(app1);
        com.spark.insight.model.ApplicationModel a2 = applicationService.getById(app2);

        if (a1 == null || a2 == null) {
            throw new RuntimeException("One or both applications not found.");
        }

        List<MetricDiff> metrics = new ArrayList<>();
        addMetric(metrics, "Total Duration", "duration", "ms", (double) a1.getDuration(), (double) a2.getDuration(), true);

        double durDiff = getPctChange((double) a1.getDuration(), (double) a2.getDuration());
        String conclusionType = durDiff > 10 ? "REGRESSED" : (durDiff < -10 ? "IMPROVED" : "SIMILAR");

        return ComparisonResult.builder()
                .type("APPLICATION")
                .source(buildMeta(app1, "APP", app1, a1.getAppName(), a1.getDuration(), null, null))
                .target(buildMeta(app2, "APP", app2, a2.getAppName(), a2.getDuration(), null, null))
                .keyMetrics(metrics)
                .configDiffs(fetchResourceConfigs(app1, app2))
                .conclusion(String.format("Application duration change: %.1f%%", durDiff))
                .conclusionType(conclusionType)
                .build();
    }

    private ComparisonResult compareStages(String app1, int stageId1, String app2, int stageId2) {
        StageModel s1 = stageService.getStage(app1, stageId1, 0);
        StageModel s2 = stageService.getStage(app2, stageId2, 0);

        if (s1 == null || s2 == null) {
            throw new RuntimeException("One or both stages not found.");
        }

        List<MetricDiff> metrics = new ArrayList<>();
        
        // 1. Core Performance
        addMetric(metrics, "Duration", "duration", "ms", (double) s1.getDuration(), (double) s2.getDuration(), true);
        addMetric(metrics, "GC Time", "gc_time", "ms", (double) s1.getGcTimeSum(), (double) s2.getGcTimeSum(), true);
        
        // 2. Resource Overhead (Spill)
        addMetric(metrics, "Disk Spill", "disk_spill", "bytes", (double) s1.getDiskBytesSpilledSum(), (double) s2.getDiskBytesSpilledSum(), true);
        addMetric(metrics, "Memory Spill", "mem_spill", "bytes", (double) s1.getMemoryBytesSpilledSum(), (double) s2.getMemoryBytesSpilledSum(), true);

        // 3. I/O & Shuffle
        addMetric(metrics, "Input Size", "input", "bytes", (double) s1.getInputBytes(), (double) s2.getInputBytes(), false);
        // Treat shuffle increase as generally negative for performance stability
        addMetric(metrics, "Shuffle Read", "shuffle_read", "bytes", (double) s1.getShuffleReadBytes(), (double) s2.getShuffleReadBytes(), true);
        addMetric(metrics, "Shuffle Write", "shuffle_write", "bytes", (double) s1.getShuffleWriteBytes(), (double) s2.getShuffleWriteBytes(), true);
        
        // 4. Concurrency (Executors)
        long execs1 = taskService.getExecutorCountForStage(app1, stageId1);
        long execs2 = taskService.getExecutorCountForStage(app2, stageId2);
        addMetric(metrics, "Executors Involved", "exec_count", "count", (double) execs1, (double) execs2, false);

        // Analyze Conclusion
        double durDiff = getPctChange((double) s1.getDuration(), (double) s2.getDuration());
        String conclusionType = "SIMILAR";
        String conclusion = "Performance is stable.";
        
        if (durDiff > 20) {
            conclusionType = "REGRESSED";
            conclusion = String.format("Performance degraded by %.1f%%. ", durDiff);
            if (s2.getDiskBytesSpilledSum() > s1.getDiskBytesSpilledSum() * 1.5) {
                conclusion += "Significant Disk Spill increase detected.";
            } else if (execs2 < execs1) {
                conclusion += "Fewer executors were involved in the target run.";
            }
        } else if (durDiff < -20) {
            conclusionType = "IMPROVED";
            conclusion = String.format("Performance improved by %.1f%%.", Math.abs(durDiff));
        }

        return ComparisonResult.builder()
                .type("STAGE")
                .source(buildMeta(app1, "STAGE", String.valueOf(stageId1), s1.getStageName(), s1.getDuration(), null, s1.getNumTasks()))
                .target(buildMeta(app2, "STAGE", String.valueOf(stageId2), s2.getStageName(), s2.getDuration(), null, s2.getNumTasks()))
                .keyMetrics(metrics)
                .configDiffs(fetchResourceConfigs(app1, app2))
                .conclusion(conclusion)
                .conclusionType(conclusionType)
                .build();
    }

    private ComparisonResult compareJobs(String app1, int jobId1, String app2, int jobId2) {
        JobModel j1 = jobService.getJob(app1, jobId1);
        JobModel j2 = jobService.getJob(app2, jobId2);

        if (j1 == null || j2 == null) {
            throw new RuntimeException("One or both jobs not found.");
        }

        List<MetricDiff> metrics = new ArrayList<>();
        addMetric(metrics, "Duration", "duration", "ms", (double) j1.getDuration(), (double) j2.getDuration(), true);
        
        long execs1 = taskService.getExecutorCountForJob(app1, jobId1);
        long execs2 = taskService.getExecutorCountForJob(app2, jobId2);
        addMetric(metrics, "Executors Involved", "exec_count", "count", (double) execs1, (double) execs2, false);
        
        addMetric(metrics, "Stages Count", "stages", "count", (double) j1.getNumStages(), (double) j2.getNumStages(), false);
        addMetric(metrics, "Tasks Count", "tasks", "count", (double) j1.getNumTasks(), (double) j2.getNumTasks(), false);

        double durDiff = getPctChange((double) j1.getDuration(), (double) j2.getDuration());
        String conclusionType = durDiff > 10 ? "REGRESSED" : (durDiff < -10 ? "IMPROVED" : "SIMILAR");
        
        return ComparisonResult.builder()
                .type("JOB")
                .source(buildMeta(app1, "JOB", String.valueOf(jobId1), j1.getDescription(), j1.getDuration(), j1.getNumStages(), j1.getNumTasks()))
                .target(buildMeta(app2, "JOB", String.valueOf(jobId2), j2.getDescription(), j2.getDuration(), j2.getNumStages(), j2.getNumTasks()))
                .keyMetrics(metrics)
                .configDiffs(fetchResourceConfigs(app1, app2))
                .conclusion(String.format("Job duration change: %.1f%%", durDiff))
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

    private List<ConfigDiff> fetchResourceConfigs(String app1, String app2) {
        List<ConfigDiff> diffs = new ArrayList<>();
        // Fetch all configs
        List<com.spark.insight.model.EnvironmentConfigModel> list1 = envService.lambdaQuery().eq(com.spark.insight.model.EnvironmentConfigModel::getAppId, app1).list();
        List<com.spark.insight.model.EnvironmentConfigModel> list2 = envService.lambdaQuery().eq(com.spark.insight.model.EnvironmentConfigModel::getAppId, app2).list();
        
        // Use category + key as the map key to avoid collisions
        java.util.Map<String, com.spark.insight.model.EnvironmentConfigModel> map1 = list1.stream().collect(java.util.stream.Collectors.toMap(
            e -> e.getCategory() + "||" + e.getParamKey(), 
            e -> e, 
            (a, b) -> a));
        java.util.Map<String, com.spark.insight.model.EnvironmentConfigModel> map2 = list2.stream().collect(java.util.stream.Collectors.toMap(
            e -> e.getCategory() + "||" + e.getParamKey(), 
            e -> e, 
            (a, b) -> a));
        
        java.util.Set<String> allCompositeKeys = new java.util.HashSet<>();
        allCompositeKeys.addAll(map1.keySet());
        allCompositeKeys.addAll(map2.keySet());
        
        for (String compositeKey : allCompositeKeys) {
            com.spark.insight.model.EnvironmentConfigModel e1 = map1.get(compositeKey);
            com.spark.insight.model.EnvironmentConfigModel e2 = map2.get(compositeKey);
            
            String v1 = e1 != null ? e1.getParamValue() : null;
            String v2 = e2 != null ? e2.getParamValue() : null;
            
            // Only add if they are different (one null, one not, or values unequal)
            boolean different = (v1 == null && v2 != null) || (v1 != null && v2 == null) || (v1 != null && !v1.equals(v2));
            if (different) {
                String category = e1 != null ? e1.getCategory() : e2.getCategory();
                String key = e1 != null ? e1.getParamKey() : e2.getParamKey();
                
                diffs.add(ConfigDiff.builder()
                        .category(category)
                        .key(key)
                        .sourceValue(v1 != null ? v1 : "N/A")
                        .targetValue(v2 != null ? v2 : "N/A")
                        .build());
            }
        }
        
        // Sort by category then key
        diffs.sort(java.util.Comparator.comparing(ConfigDiff::getCategory).thenComparing(ConfigDiff::getKey));
        
        return diffs;
    }

    private void addMetric(List<MetricDiff> list, String label, String name, String unit, Double v1, Double v2, boolean lowerIsBetter) {
        if (v1 == null) v1 = 0.0;
        if (v2 == null) v2 = 0.0;
        
        double delta = v2 - v1;
        double pct = getPctChange(v1, v2);
        
        String severity = "NEUTRAL";
        if (lowerIsBetter) {
            if (pct > 50) severity = "CRITICAL";
            else if (pct > 20) severity = "WARNING";
            else if (pct < -10) severity = "GOOD";
        } else {
            // For neutral metrics, still highlight huge changes
            if (Math.abs(pct) > 50) severity = "WARNING"; // Use WARNING color for big changes
        }

        list.add(MetricDiff.builder()
                .label(label)
                .name(name)
                .unit(unit)
                .sourceValue(v1)
                .targetValue(v2)
                .delta(delta)
                .pctChange(pct)
                .severity(severity)
                .build());
    }

    private double getPctChange(Double v1, Double v2) {
        if (v1 == null || v1 == 0) return v2 > 0 ? 100.0 : 0.0;
        return (v2 - v1) / v1 * 100.0;
    }
}