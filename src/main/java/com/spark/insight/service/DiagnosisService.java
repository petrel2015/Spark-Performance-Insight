package com.spark.insight.service;

import com.spark.insight.config.DiagnosisProperties;
import com.spark.insight.model.ApplicationModel;
import com.spark.insight.model.JobModel;
import com.spark.insight.model.StageModel;
import com.spark.insight.model.TaskModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final TaskService taskService;
    private final JobService jobService;
    private final DiagnosisProperties properties;

    /**
     * 为指定的 Application 生成 Markdown 格式的规则引擎诊断报告
     */
    public String generateMarkdownReport(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app == null) return "Application not found.";

        StringBuilder sb = new StringBuilder();
        sb.append("# <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">analytics</span> 规则引擎诊断报告 (Rule-Based Diagnostic Report)\n\n");

        // 1. Executive Summary
        List<JobModel> jobs = jobService.lambdaQuery()
                .eq(JobModel::getAppId, appId)
                .list();
        
        double avgScore = jobs.stream()
                .filter(j -> j.getDuration() != null && j.getDuration() > 0)
                .mapToDouble(j -> j.getPerformanceScore() != null ? j.getPerformanceScore() : 100.0)
                .average().orElse(100.0);

        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">dashboard</span> 应用健康概览\n");
        sb.append(String.format("- **整体健康得分**: `%d / 100` (%s)\n", 
                Math.round(avgScore), getHealthLabel(avgScore)));
        sb.append(String.format("- **应用名称**: `%s`\n", app.getAppName()));
        sb.append(String.format("- **运行耗时**: `%s` 秒\n", 
                app.getDuration() != null ? app.getDuration() / 1000 : "N/A"));
        sb.append("\n---\n\n");

        // 2. Detailed Stage Analysis
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">troubleshoot</span> 性能异常阶段分析\n");
        List<StageModel> criticalStages = stageService.lambdaQuery()
                .eq(StageModel::getAppId, appId)
                .lt(StageModel::getPerformanceScore, 80) // 健康分低于 80 视为异常
                .orderByAsc(StageModel::getPerformanceScore)
                .last("LIMIT 5")
                .list();

        if (criticalStages.isEmpty()) {
            sb.append("> **结论**: <span style=\"color: #27ae60;\">未发现严重性能异常的阶段。应用当前运行状态良好。</span>\n\n");
        } else {
            for (StageModel stage : criticalStages) {
                sb.append(String.format("### Stage %d: %s\n", 
                        stage.getStageId(), stage.getStageName()));
                sb.append(String.format("- **健康得分**: `%d` | **任务总数**: `%d`\n", 
                        Math.round(stage.getPerformanceScore()), stage.getNumTasks()));
                
                appendStageSpecificAdvice(sb, stage);
                sb.append("\n");
            }
        }

        // 3. High Impact Jobs
        sb.append("## <span class=\"material-symbols-outlined\" style=\"vertical-align: middle;\">assignment_late</span> 高风险作业排名\n");
        List<JobModel> topImpactJobs = jobs.stream()
                .filter(j -> j.getPerformanceScore() != null && j.getPerformanceScore() < 90)
                .sorted(java.util.Comparator.comparingDouble(JobModel::getPerformanceScore))
                .limit(3)
                .toList();

        if (topImpactJobs.isEmpty()) {
            sb.append("> **结论**: 未发现高风险作业。\n\n");
        } else {
            for (JobModel job : topImpactJobs) {
                sb.append(String.format("### Job %d: %s\n", job.getJobId(), 
                        job.getDescription() != null ? job.getDescription() : "Job Execution"));
                sb.append(String.format("- **健康评分**: `%d` | **运行耗时**: `%d ms`\n", 
                        Math.round(job.getPerformanceScore()), job.getDuration()));
                
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

    private void appendStageSpecificAdvice(StringBuilder sb, StageModel stage) {
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
        if (score < 40) return "<span style=\"color: #e74c3c;\">极差 (Critical)</span>";
        if (score < 80) return "<span style=\"color: #f39c12;\">一般 (Warning)</span>";
        return "<span style=\"color: #27ae60;\">良好 (Healthy)</span>";
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        long s = (ms / 1000) % 60;
        long m = (ms / (1000 * 60)) % 60;
        long h = (ms / (1000 * 60 * 60));

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString();
    }
}