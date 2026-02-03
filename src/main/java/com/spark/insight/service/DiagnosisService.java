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
        if (app == null) {
            return "Application not found for ID: " + appId;
        }
        
        List<StageModel> stages = stageService.lambdaQuery().eq(StageModel::getAppId, appId).list();

        StringBuilder sb = new StringBuilder();
        sb.append("# Spark Performance Diagnosis Report\n\n");
        sb.append("## 1. Basic Information\n");
        sb.append("- **App Name:** ").append(app.getAppName()).append("\n");
        sb.append("- **App ID:** ").append(app.getAppId()).append("\n");
        
        // Null-safe duration
        Long duration = app.getDuration();
        if (duration != null) {
            sb.append("- **Duration:** ").append(duration / 1000).append("s\n");
        } else {
            sb.append("- **Duration:** <span style=\"color:red\">N/A (Missing Data)</span> ⚠️\n");
        }
        
        sb.append("- **Spark Version:** ").append(app.getSparkVersion() != null ? app.getSparkVersion() : "Unknown").append("\n\n");

        sb.append("## 2. Performance Issues (Anomalies)\n\n");

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
            if (Boolean.TRUE.equals(stage.getIsSkewed())) {
                stageIssues.append("  - **Data Skew Detected:** Max Task duration is significantly higher than median.\n");
            }

            // Rule 2: GC Pressure
            // Skip precise time calculation if data is incomplete, fall back to rough estimate if possible or skip
            if (stage.getGcTimeSum() != null && stage.getGcTimeSum() > 0 && stage.getNumTasks() != null && stage.getNumTasks() > 0) {
                 // Use p50 duration safely
                 Long p50 = stage.getDurationP50();
                 if (p50 != null && p50 > 0) {
                     long totalExecutionTime = p50 * stage.getNumTasks();
                     if (totalExecutionTime > 0 && (double) stage.getGcTimeSum() / totalExecutionTime > 0.1) {
                         stageIssues.append("  - **High GC Pressure:** JVM GC time accounts for more than 10% of estimated total task time.\n");
                     }
                 }
            }

            // Rule 3: Memory Spill
            List<TaskModel> spillTasks = taskService.lambdaQuery()
                    .eq(TaskModel::getAppId, appId)
                    .eq(TaskModel::getStageId, stage.getStageId())
                    .gt(TaskModel::getDiskBytesSpilled, 0)
                    .list();
            if (!spillTasks.isEmpty()) {
                long totalSpill = spillTasks.stream().mapToLong(TaskModel::getDiskBytesSpilled).sum();
                stageIssues.append("  - **Disk Spill Detected:** Total ").append(totalSpill / 1024 / 1024).append(" MB data spilled to disk.\n");
            }

            // Report issues
            if (stageIssues.length() > 0) {
                hasIssues = true;
                sb.append("### Stage ").append(stage.getStageId()).append(": ").append(stage.getStageName()).append("\n");
                if (isDataIncomplete) {
                    sb.append("  - ⚠️ <span style=\"color:orange\">Warning: Incomplete timing data for this stage. Analysis may be inaccurate.</span>\n");
                }
                sb.append(stageIssues.toString()).append("\n");
            }
        }

        if (!hasIssues) {
            sb.append("No significant performance issues detected in this application.\n");
        }
        
        // Data Quality Section
        if (incompleteStages > 0 || app.getDuration() == null) {
            sb.append("\n## ⚠️ Data Quality Warning\n");
            sb.append("Some metrics could not be calculated due to missing event logs:\n");
            if (app.getDuration() == null) {
                sb.append("- **App Duration:** Missing application end event.\n");
            }
            if (incompleteStages > 0) {
                sb.append("- **Incomplete Stages:** ").append(incompleteStages)
                  .append(" stages are missing submission or completion times. (Marked as Bad Data Points)\n");
            }
        }

        sb.append("\n## 3. Optimization Suggestions (For LLM)\n");
        sb.append("Based on the detected issues, consider the following actions:\n");
        sb.append("- For **Data Skew**: Check the join/grouping keys and consider using `salting` or increasing `spark.sql.shuffle.partitions`.\n");
        sb.append("- For **High GC**: Increase executor memory or tune `spark.memory.fraction`.\n");
        sb.append("- For **Spill**: Increase `spark.executor.memory` or reduce `spark.memory.storageFraction` to allow more execution memory.\n");

        return sb.toString();
    }
}
