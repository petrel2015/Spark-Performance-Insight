package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.config.DiagnosisProperties;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final JobService jobService;

    /**
     * 为指定的 Application 生成 Markdown 格式的规则引擎诊断报告
     */
    public String generateMarkdownReport(String appId) {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            return "Application not found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">analytics</span> 规则引擎诊断报告 (Rule-Based Diagnostic Report)\n\n");

        // 1. Executive Summary
        List<GoldJobModel> jobs = jobService.lambdaQuery()
                .eq(GoldJobModel::getAppId, appId)
                .list();
        
        double avgScore = jobs.stream()
                .filter(job -> {
                    return job.getDuration() != null && job.getDuration() > 0;
                })
                .mapToDouble(job -> {
                    return job.getPerformanceScore() != null ? job.getPerformanceScore() : 100.0;
                })
                .average().orElse(100.0);

        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">dashboard</span> 应用健康概览\n");
        sb.append(String.format("- **整体健康得分**: %s\n", getHealthLabel(avgScore)));
        sb.append(String.format("- **应用名称**: `%s`\n", app.getAppName()));
        sb.append(String.format("- **运行耗时**: `%s`\n", 
                app.getDuration() != null ? formatDuration(app.getDuration()) : "N/A"));
        sb.append("\n---\n\n");

        // 2. Detailed Stage Analysis
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">troubleshoot</span> 性能异常阶段分析\n");
        List<GoldStageModel> criticalStages = stageService.lambdaQuery()
                .eq(GoldStageModel::getAppId, appId)
                .lt(GoldStageModel::getPerformanceScore, 90) // 健康分低于 90 视为有优化空间
                .orderByAsc(GoldStageModel::getPerformanceScore)
                .last("LIMIT 5")
                .list();

        if (criticalStages.isEmpty()) {
            sb.append("> **结论**: <span style=\"color: #27ae60;\">未发现严重性能异常的阶段。应用当前运行状态良好。</span>\n\n");
        } else {
            for (GoldStageModel stage : criticalStages) {
                sb.append(String.format("### Stage %d: %s\n", 
                        stage.getStageId(), stage.getStageName()));
                sb.append(String.format("- **健康得分**: <span style=\"color: %s; font-weight: bold;\">%d</span> | **运行耗时**: `%s` | **任务总数**: `%d`\n", 
                        getHealthColor(stage.getPerformanceScore()),
                        Math.round(stage.getPerformanceScore()),
                        formatDuration(stage.getDuration() != null ? stage.getDuration() : 0),
                        stage.getNumTasks()));
                
                appendStageSpecificAdvice(sb, stage);
                sb.append("\n");
            }
        }

        // 3. High Impact Jobs
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">assignment_late</span> 高风险作业排名\n");
        List<GoldJobModel> topImpactJobs = jobs.stream()
                .filter(job -> {
                    return job.getPerformanceScore() != null && job.getPerformanceScore() < 90;
                })
                .sorted(java.util.Comparator.comparingDouble(GoldJobModel::getPerformanceScore))
                .limit(3)
                .toList();

        if (topImpactJobs.isEmpty()) {
            sb.append("> **结论**: 未发现高风险作业。\n\n");
        } else {
            for (GoldJobModel job : topImpactJobs) {
                sb.append(String.format("### Job %d: %s\n", job.getJobId(), 
                        job.getDescription() != null ? job.getDescription() : "Job Execution"));
                sb.append(String.format("- **健康评分**: <span style=\"color: %s; font-weight: bold;\">%d</span> | **运行耗时**: `%s`\n", 
                        getHealthColor(job.getPerformanceScore()),
                        Math.round(job.getPerformanceScore()), 
                        formatDuration(job.getDuration() != null ? job.getDuration() : 0)));
                
                // 只有当有关联阶段被列出时，才引导查看下方
                if (!criticalStages.isEmpty()) {
                    sb.append("- **状态分析**: 该作业性能受阻，具体瓶颈可参考下方“性能异常阶段分析”中的详细诊断。\n\n");
                } else {
                    sb.append("- **状态分析**: 该作业存在轻微延迟，建议通过详情页的 Event Timeline 进行微调。\n\n");
                }
            }
        }

        // 4. Data Quality Section
        if (app.getDuration() == null) {
            sb.append("\n## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle; color: #f39c12;\">warning</span> 数据质量提示\n");
            sb.append("缺失 `ApplicationEnd` 事件，诊断结果基于部分日志生成，可能存在偏差。\n");
        }

        return sb.toString();
    }

    private void appendStageSpecificAdvice(StringBuilder sb, GoldStageModel stage) {
        if (Boolean.TRUE.equals(stage.getIsSkewed())) {
            sb.append("- **数据倾斜**: 检测到最大任务耗时显著高于中位数。建议检查 `Join/GroupBy` 的 Key 分布，考虑引入加盐 (Salting) 策略。\n");
        }
        if (stage.getDiskBytesSpilledSum() != null && stage.getDiskBytesSpilledSum() > 0) {
            sb.append("- **磁盘溢写**: 内存资源不足导致中间数据溢出。建议增加 `executor-memory` 或调优 `spark.memory.fraction`。\n");
        }
        if (stage.getGcTimeSum() != null && stage.getTasksDurationSum() != null && stage.getTasksDurationSum() > 0) {
            if ((double) stage.getGcTimeSum() / stage.getTasksDurationSum() > 0.1) {
                sb.append("- **内存压力**: GC 时间占比超过 10%。请检查是否存在大对象缓存或调整 JVM 堆空间配置。\n");
            }
        }
        sb.append("- **排查路径**: 请访问该 Stage 详情页查看 “Event Timeline” 以获取任务分布的视觉化证据。\n");
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

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = (ms / (1000 * 60 * 60));

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        return sb.toString();
    }
}
