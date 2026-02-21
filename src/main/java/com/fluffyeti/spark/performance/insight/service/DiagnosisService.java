package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import com.fluffyeti.spark.performance.insight.model.dto.ApplicationInsight;
import com.fluffyeti.spark.performance.insight.utils.FormatUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final JobService jobService;

    /**
     * Compute a structured insight DTO for an application.
     * This is the single source of truth for both UI and MCP.
     */
    public ApplicationInsight computeApplicationInsight(String appId) {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            return null;
        }

        List<GoldJobModel> jobs = jobService.lambdaQuery()
                .eq(GoldJobModel::getAppId, appId)
                .list();

        double avgScore = jobs.stream()
                .filter(job -> job.getDuration() != null && job.getDuration() > 0)
                .mapToDouble(job -> job.getPerformanceScore() != null ? job.getPerformanceScore() : 100.0)
                .average().orElse(100.0);

        List<GoldStageModel> criticalStages = stageService.lambdaQuery()
                .eq(GoldStageModel::getAppId, appId)
                .lt(GoldStageModel::getPerformanceScore, 90)
                .orderByAsc(GoldStageModel::getPerformanceScore)
                .last("LIMIT 5")
                .list();

        List<ApplicationInsight.Bottleneck> bottlenecks = new ArrayList<>();
        for (GoldStageModel stage : criticalStages) {
            bottlenecks.add(buildBottleneck(stage));
        }

        List<ApplicationInsight.JobScore> jobScores = jobs.stream()
                .filter(job -> job.getPerformanceScore() != null && job.getPerformanceScore() < 90)
                .sorted(java.util.Comparator.comparingDouble(GoldJobModel::getPerformanceScore))
                .limit(3)
                .map(j -> ApplicationInsight.JobScore.builder()
                        .jobId(j.getJobId())
                        .description(j.getDescription() != null ? j.getDescription() : "Job Execution")
                        .score(j.getPerformanceScore())
                        .durationMs(j.getDuration())
                        .build())
                .toList();

        return ApplicationInsight.builder()
                .appMetadata(ApplicationInsight.AppMetadata.builder()
                        .appId(app.getAppId())
                        .name(app.getAppName())
                        .durationMs(app.getDuration())
                        .healthScore(avgScore)
                        .build())
                .criticalBottlenecks(bottlenecks)
                .topImpactJobs(jobScores)
                .efficiencyMetrics(ApplicationInsight.EfficiencyMetrics.builder()
                        .totalInputBytes(app.getTotalInputBytes())
                        .totalShuffleReadBytes(app.getTotalShuffleReadBytes())
                        .build())
                .dataQuality(ApplicationInsight.DataQualityHint.builder()
                        .hasEndEvent(app.getDuration() != null)
                        .note(app.getDuration() == null ? "Missing ApplicationEnd event." : null)
                        .build())
                .build();
    }

    private ApplicationInsight.Bottleneck buildBottleneck(GoldStageModel stage) {
        ApplicationInsight.Bottleneck.BottleneckBuilder builder = ApplicationInsight.Bottleneck.builder()
                .stageId(stage.getStageId())
                .stageName(stage.getStageName())
                .severity(stage.getPerformanceScore() < 40 ? "HIGH" : (stage.getPerformanceScore() < 70 ? "MEDIUM" : "LOW"));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("score", stage.getPerformanceScore());
        metrics.put("duration_ms", stage.getDuration());

        if (Boolean.TRUE.equals(stage.getIsSkewed())) {
            builder.type(ApplicationInsight.Bottleneck.Type.DATA_SKEW);
            builder.description("Data Skew: Max task duration is significantly higher than median.");
            metrics.put("is_skewed", true);
        } else if (stage.getDiskBytesSpilledSum() != null && stage.getDiskBytesSpilledSum() > 0) {
            builder.type(ApplicationInsight.Bottleneck.Type.DISK_SPILL);
            builder.description("Disk Spill: Memory pressure caused data to spill to disk.");
            metrics.put("disk_spill_bytes", stage.getDiskBytesSpilledSum());
        } else if (stage.getGcTimeSum() != null && stage.getTasksDurationSum() != null && stage.getTasksDurationSum() > 0 
                   && (double) stage.getGcTimeSum() / stage.getTasksDurationSum() > 0.1) {
            builder.type(ApplicationInsight.Bottleneck.Type.MEMORY_PRESSURE);
            builder.description("Memory Pressure: GC time accounts for more than 10% of task duration.");
            metrics.put("gc_time_ms", stage.getGcTimeSum());
        } else {
            builder.type(ApplicationInsight.Bottleneck.Type.LOCALITY_ISSUE); // Fallback
            builder.description("General performance degradation.");
        }

        return builder.metrics(metrics).build();
    }

    /**
     * 为指定的 Application 生成 Markdown 格式的规则引擎诊断报告
     */
    public String generateMarkdownReport(String appId) {
        ApplicationInsight insight = computeApplicationInsight(appId);
        if (insight == null) {
            return "Application not found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">analytics</span> 规则引擎诊断报告 (Rule-Based Diagnostic Report)\n\n");

        // 1. Executive Summary
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">dashboard</span> 应用健康概览\n");
        sb.append(String.format("- **整体健康得分**: %s\n", getHealthLabel(insight.getAppMetadata().getHealthScore())));
        sb.append(String.format("- **应用名称**: `%s`\n", insight.getAppMetadata().getName()));
        sb.append(String.format("- **运行耗时**: `%s`\n", 
                insight.getAppMetadata().getDurationMs() != null ? FormatUtils.formatDuration(insight.getAppMetadata().getDurationMs()) : "N/A"));
        sb.append("\n---\n\n");

        // 2. Detailed Stage Analysis
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">troubleshoot</span> 性能异常阶段分析\n");
        if (insight.getCriticalBottlenecks().isEmpty()) {
            sb.append("> **结论**: <span style=\"color: #27ae60;\">未发现严重性能异常的阶段。应用当前运行状态良好。</span>\n\n");
        } else {
            for (ApplicationInsight.Bottleneck b : insight.getCriticalBottlenecks()) {
                sb.append(String.format("### Stage %d: %s\n", b.getStageId(), b.getStageName()));
                sb.append(String.format("- **健康得分**: <span style=\"color: %s; font-weight: bold;\">%d</span> | **运行耗时**: `%s`\n", 
                        getHealthColor((Double) b.getMetrics().get("score")),
                        Math.round((Double) b.getMetrics().get("score")),
                        FormatUtils.formatDuration((Long) b.getMetrics().get("duration_ms"))));
                sb.append("- **诊断**: ").append(b.getDescription()).append("\n");
                sb.append("- **排查路径**: 请访问该 Stage 详情页查看 “Event Timeline” 以获取任务分布的视觉化证据。\n\n");
            }
        }

        // 3. High Impact Jobs
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">assignment_late</span> 高风险作业排名\n");
        if (insight.getTopImpactJobs().isEmpty()) {
            sb.append("> **结论**: 未发现高风险作业。\n\n");
        } else {
            for (ApplicationInsight.JobScore job : insight.getTopImpactJobs()) {
                sb.append(String.format("### Job %d: %s\n", job.getJobId(), job.getDescription()));
                sb.append(String.format("- **健康评分**: <span style=\"color: %s; font-weight: bold;\">%d</span> | **运行耗时**: `%s`\n", 
                        getHealthColor(job.getScore()),
                        Math.round(job.getScore()), 
                        FormatUtils.formatDuration(job.getDurationMs() != null ? job.getDurationMs() : 0)));
                sb.append("- **状态分析**: 该作业性能受阻，具体瓶颈可参考上方“性能异常阶段分析”中的详细诊断。\n\n");
            }
        }

        // 4. Data Quality Section
        if (!insight.getDataQuality().isHasEndEvent()) {
            sb.append("\n## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle; color: #f39c12;\">warning</span> 数据质量提示\n");
            sb.append("缺失 `ApplicationEnd` 事件，诊断结果基于部分日志生成，可能存在偏差。\n");
        }

        return sb.toString();
    }

    private String getHealthLabel(double score) {
        if (score < 40) {
            return String.format("<span style=\"color: #e74c3c; font-weight: bold;\">极差 (Critical: %d)</span>", Math.round(score));
        }
        if (score < 70) {
            return String.format("<span style=\"color: #f39c12; font-weight: bold;\">一般 (Warning: %d)</span>", Math.round(score));
        }
        if (score < 90) {
            return String.format("<span style=\"color: #27ae60; font-weight: bold;\">良好 (Good: %d)</span>", Math.round(score));
        }
        return String.format("<span style=\"color: #27ae60; font-weight: bold;\">健康 (Healthy: %d)</span>", Math.round(score));
    }

    private String getHealthColor(double score) {
        if (score < 40) {
            return "#e74c3c";
        }
        if (score < 70) {
            return "#f39c12";
        }
        return "#27ae60";
    }
}
