package com.spark.insight.service;

import com.spark.insight.config.DiagnosisProperties;
import com.spark.insight.model.ApplicationModel;
import com.spark.insight.model.StageModel;
import com.spark.insight.model.TaskModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final TaskService taskService;
    private final DiagnosisProperties properties;

    /**
     * 为指定的 Application 生成 Markdown 格式的诊断报告
     */
    public String generateMarkdownReport(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            return "未找到 ID 为 " + appId + " 的应用。";
        }
        
        List<StageModel> stages = stageService.lambdaQuery().eq(StageModel::getAppId, appId).list();

        StringBuilder sb = new StringBuilder();
        sb.append("# Spark 性能诊断报告\n\n");
        sb.append("## 1. 基本信息\n");
        sb.append("- **应用名称:** ").append(app.getAppName()).append("\n");
        sb.append("- **应用 ID:** ").append(app.getAppId()).append("\n");
        
        // Null-safe duration
        Long duration = app.getDuration();
        if (duration != null) {
            sb.append("- **运行时长:** ").append(duration / 1000).append(" 秒\n");
        } else {
            sb.append("- **运行时长:** <span style=\"color:red\">暂无数据 (日志缺失)</span> ⚠️\n");
        }
        
        sb.append("- **Spark 版本:** ").append(app.getSparkVersion() != null ? app.getSparkVersion() : "未知").append("\n\n");

        sb.append("## 2. 性能问题 (异常指标)\n\n");

        boolean hasIssues = false;
        int incompleteStages = 0;

        for (StageModel stage : stages) {
            StringBuilder stageIssues = new StringBuilder();
            boolean isDataIncomplete = false;

            // Rule 0: Data Integrity Check
            if (stage.getSubmissionTime() == null || stage.getCompletionTime() == null) {
                incompleteStages++;
                isDataIncomplete = true;
            }
            
            // Rule 1: Data Skew
            // Only report skew if absolute duration is significant (e.g. > 1s)
            if (Boolean.TRUE.equals(stage.getIsSkewed()) && 
                stage.getMaxTaskDuration() != null && 
                stage.getMaxTaskDuration() > properties.getSkewMinDurationMs()) {
                stageIssues.append("  - **检测到数据倾斜:** 最大任务 (Task) 耗时显著高于中位数，且绝对耗时超过 ")
                           .append(properties.getSkewMinDurationMs()).append("ms。\n");
            }

            // Rule 2: GC Pressure
            boolean gcIssueFound = false;
            // 2.1 Ratio Check
            if (stage.getGcTimeSum() != null && stage.getGcTimeSum() > 0 && stage.getNumTasks() != null && stage.getNumTasks() > 0) {
                 Long p50 = stage.getDurationP50();
                 if (p50 != null && p50 > 0) {
                     long totalExecutionTime = p50 * stage.getNumTasks();
                     if (totalExecutionTime > 0 && (double) stage.getGcTimeSum() / totalExecutionTime > 0.1) {
                         stageIssues.append("  - **GC 压力过大 (整体):** JVM GC 时间占比超过预估总任务时间的 10%。\n");
                         gcIssueFound = true;
                     }
                 }
            }
            // 2.2 Absolute Check (Single Task)
            long highGcTaskCount = taskService.lambdaQuery()
                    .eq(TaskModel::getAppId, appId)
                    .eq(TaskModel::getStageId, stage.getStageId())
                    .gt(TaskModel::getGcTime, properties.getGcMinDurationMs())
                    .count();
            
            if (highGcTaskCount > 0) {
                stageIssues.append("  - **GC 压力过大 (单任务):** 有 ").append(highGcTaskCount)
                           .append(" 个任务单次 GC 时间超过 ").append(properties.getGcMinDurationMs()).append("ms。\n");
                gcIssueFound = true;
            }

            // Rule 3: Memory Spill
            List<TaskModel> spillTasks = taskService.lambdaQuery()
                    .eq(TaskModel::getAppId, appId)
                    .eq(TaskModel::getStageId, stage.getStageId())
                    .gt(TaskModel::getDiskBytesSpilled, 0)
                    .list();
            if (!spillTasks.isEmpty()) {
                long totalSpill = spillTasks.stream().mapToLong(TaskModel::getDiskBytesSpilled).sum();
                stageIssues.append("  - **检测到磁盘溢写:** 共有 ").append(totalSpill / 1024 / 1024).append(" MB 数据溢写至磁盘。\n");
            }

            // Report issues
            if (stageIssues.length() > 0) {
                hasIssues = true;
                sb.append("### Stage ").append(stage.getStageId()).append(": ").append(stage.getStageName()).append("\n");
                if (isDataIncomplete) {
                    sb.append("  - ⚠️ <span style=\"color:orange\">警告：此阶段时间数据不完整，分析结果可能存在偏差。</span>\n");
                }
                sb.append(stageIssues.toString()).append("\n");
            }
        }

        if (!hasIssues) {
            sb.append("在此应用中未检测到显著的性能问题。\n");
        }
        
        // Data Quality Section
        if (incompleteStages > 0 || app.getDuration() == null) {
            sb.append("\n## ⚠️ 数据质量警告\n");
            sb.append("由于事件日志缺失，部分指标无法准确计算：\n");
            if (app.getDuration() == null) {
                sb.append("- **应用时长:** 缺失 ApplicationEnd 事件。\n");
            }
            if (incompleteStages > 0) {
                sb.append("- **不完整的阶段:** 共有 ").append(incompleteStages)
                  .append(" 个阶段缺失提交或完成时间（已标记为坏点数据）。\n");
            }
        }

        sb.append("\n## 3. 优化建议 (供 LLM 参考)\n");
        sb.append("针对检测到的问题，建议考虑以下优化措施：\n");
        sb.append("- **针对数据倾斜**: 检查 Join 或 Grouping 的 Key，考虑使用“加盐”策略或增加 `spark.sql.shuffle.partitions`。\n");
        sb.append("- **针对高 GC 压力**: 增加 Executor 内存，或调整 `spark.memory.fraction` 以优化内存管理。\n");
        sb.append("- **针对溢写**: 增加 `spark.executor.memory` 或减少 `spark.memory.storageFraction` 以留出更多执行内存。\n");

        return sb.toString();
    }
}
