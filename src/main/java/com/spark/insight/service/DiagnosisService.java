package com.spark.insight.service;

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

    /**
     * 为指定的 Application 生成 Markdown 格式的诊断报告
     */
    public String generateMarkdownReport(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        List<StageModel> stages = stageService.lambdaQuery().eq(StageModel::getAppId, appId).list();

        StringBuilder sb = new StringBuilder();
        sb.append("# Spark Performance Diagnosis Report\n\n");
        sb.append("## 1. Basic Information\n");
        sb.append("- **App Name:** ").append(app.getAppName()).append("\n");
        sb.append("- **App ID:** ").append(app.getAppId()).append("\n");
        sb.append("- **Duration:** ").append(app.getDuration() / 1000).append("s\n");
        sb.append("- **Spark Version:** ").append(app.getSparkVersion()).append("\n\n");

        sb.append("## 2. Performance Issues (Anomalies)\n\n");

        boolean hasIssues = false;
        for (StageModel stage : stages) {
            StringBuilder stageIssues = new StringBuilder();
            
            // Rule 1: Data Skew
            if (Boolean.TRUE.equals(stage.getIsSkewed())) {
                stageIssues.append("  - **Data Skew Detected:** Max Task duration is significantly higher than median.\n");
            }

            // Rule 2: GC Pressure
            double gcRatio = (double) stage.getGcTimeSum() / (stage.getCompletionTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - stage.getSubmissionTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            // 简化判断：如果总 GC 时间超过该 Stage 运行时间的 10% (近似估算)
            if (stage.getGcTimeSum() > 0 && stage.getNumTasks() > 0) {
                 // 实际上应该用所有 task 耗时总和作为分母，这里简化为总耗时*并发度
                 long totalExecutionTime = stage.getDurationP50() * stage.getNumTasks();
                 if (totalExecutionTime > 0 && (double) stage.getGcTimeSum() / totalExecutionTime > 0.1) {
                     stageIssues.append("  - **High GC Pressure:** JVM GC time accounts for more than 10% of total task time.\n");
                 }
            }

            // Rule 3: Memory Spill (需查询 Task 表获取详情)
            List<TaskModel> spillTasks = taskService.lambdaQuery()
                    .eq(TaskModel::getAppId, appId)
                    .eq(TaskModel::getStageId, stage.getStageId())
                    .gt(TaskModel::getDiskBytesSpilled, 0)
                    .list();
            if (!spillTasks.isEmpty()) {
                long totalSpill = spillTasks.stream().mapToLong(TaskModel::getDiskBytesSpilled).sum();
                stageIssues.append("  - **Disk Spill Detected:** Total ").append(totalSpill / 1024 / 1024).append(" MB data spilled to disk.\n");
            }

            if (stageIssues.length() > 0) {
                hasIssues = true;
                sb.append("### Stage ").append(stage.getStageId()).append(": ").append(stage.getStageName()).append("\n");
                sb.append(stageIssues.toString()).append("\n");
            }
        }

        if (!hasIssues) {
            sb.append("No significant performance issues detected in this application.\n");
        }

        sb.append("## 3. Optimization Suggestions (For LLM)\n");
        sb.append("Based on the detected issues, consider the following actions:\n");
        sb.append("- For **Data Skew**: Check the join/grouping keys and consider using `salting` or increasing `spark.sql.shuffle.partitions`.\n");
        sb.append("- For **High GC**: Increase executor memory or tune `spark.memory.fraction`.\n");
        sb.append("- For **Spill**: Increase `spark.executor.memory` or reduce `spark.memory.storageFraction` to allow more execution memory.\n");

        return sb.toString();
    }
}
