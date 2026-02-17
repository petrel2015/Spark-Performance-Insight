package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import com.fluffyeti.spark.performance.insight.model.GoldEnvironmentConfigModel;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import com.fluffyeti.spark.performance.insight.model.dto.ComparisonResult;
import com.fluffyeti.spark.performance.insight.model.dto.ComparisonResult.ConfigDiff;
import com.fluffyeti.spark.performance.insight.model.dto.ComparisonResult.ItemMeta;
import com.fluffyeti.spark.performance.insight.model.dto.ComparisonResult.MetricDiff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final JobService jobService;
    private final EnvironmentConfigService envService;
    private final TaskService taskService;
    private final SystemProperties systemProperties;

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
        com.fluffyeti.spark.performance.insight.model.GoldApplicationModel app1 = applicationService.getById(appId1);
        com.fluffyeti.spark.performance.insight.model.GoldApplicationModel app2 = applicationService.getById(appId2);

        if (app1 == null) throw new RuntimeException("Application not found: " + appId1);
        if (app2 == null) throw new RuntimeException("Application not found: " + appId2);

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
        GoldStageModel stage1 = stageService.getStage(appId1, stageId1, 0);
        GoldStageModel stage2 = stageService.getStage(appId2, stageId2, 0);

        if (stage1 == null) throw new RuntimeException("Stage " + stageId1 + " not found in application: " + appId1);
        if (stage2 == null) throw new RuntimeException("Stage " + stageId2 + " not found in application: " + appId2);

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
        GoldJobModel job1 = jobService.getJob(appId1, jobId1);
        GoldJobModel job2 = jobService.getJob(appId2, jobId2);

        if (job1 == null) throw new RuntimeException("Job " + jobId1 + " not found in application: " + appId1);
        if (job2 == null) throw new RuntimeException("Job " + jobId2 + " not found in application: " + appId2);

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
        List<GoldEnvironmentConfigModel> list1 = envService.lambdaQuery().eq(GoldEnvironmentConfigModel::getAppId, appId1).list();
        List<GoldEnvironmentConfigModel> list2 = envService.lambdaQuery().eq(GoldEnvironmentConfigModel::getAppId, appId2).list();

        // Use category + key as the map key to avoid collisions
        Map<String, GoldEnvironmentConfigModel> map1 = list1.stream().collect(java.util.stream.Collectors.toMap(
                config -> config.getCategory() + "||" + config.getParamKey(),
                config -> config,
                (configA, configB) -> configA));
        Map<String, GoldEnvironmentConfigModel> map2 = list2.stream().collect(java.util.stream.Collectors.toMap(
                config -> config.getCategory() + "||" + config.getParamKey(),
                config -> config,
                (configA, configB) -> configA));

        Set<String> allCompositeKeys = new java.util.HashSet<>();
        allCompositeKeys.addAll(map1.keySet());
        allCompositeKeys.addAll(map2.keySet());

        for (String compositeKey : allCompositeKeys) {
            GoldEnvironmentConfigModel envConfig1 = map1.get(compositeKey);
            GoldEnvironmentConfigModel envConfig2 = map2.get(compositeKey);

            if (isConfigDifferent(envConfig1, envConfig2)) {
                Optional<GoldEnvironmentConfigModel> optConfig = Optional.ofNullable(envConfig1).or(() -> Optional.ofNullable(envConfig2));
                
                String category = optConfig.map(GoldEnvironmentConfigModel::getCategory).orElse("Unknown");
                if (systemProperties.getComparison().getIgnoreCategories().stream()
                        .anyMatch(ignore -> ignore.equalsIgnoreCase(category))) {
                    continue;
                }

                diffs.add(ConfigDiff.builder()
                        .category(category)
                        .key(optConfig.map(GoldEnvironmentConfigModel::getParamKey).orElse("Unknown"))
                        .sourceValue(Optional.ofNullable(envConfig1).map(GoldEnvironmentConfigModel::getParamValue).orElse("N/A"))
                        .targetValue(Optional.ofNullable(envConfig2).map(GoldEnvironmentConfigModel::getParamValue).orElse("N/A"))
                        .build());
            }
        }

        // Sort by category then key
        diffs.sort(java.util.Comparator.comparing(ConfigDiff::getCategory).thenComparing(ConfigDiff::getKey));

        return diffs;
    }

    private boolean isConfigDifferent(@Nullable GoldEnvironmentConfigModel config1,
                                     @Nullable GoldEnvironmentConfigModel config2) {
        String value1 = config1 != null ? config1.getParamValue() : null;
        String value2 = config2 != null ? config2.getParamValue() : null;

        if (value1 == null && value2 == null) {
            return false;
        }
        if (value1 == null || value2 == null) {
            return true;
        }

        String normalized1 = config1.getAppId() != null ? value1.replaceAll(config1.getAppId(), "") : value1;
        String normalized2 = config2.getAppId() != null ? value2.replaceAll(config2.getAppId(), "") : value2;

        return !StringUtils.equals(normalized1, normalized2);
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
