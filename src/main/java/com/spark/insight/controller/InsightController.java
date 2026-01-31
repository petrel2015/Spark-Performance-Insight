package com.spark.insight.controller;

import com.spark.insight.model.*;
import com.spark.insight.model.dto.AppComparisonResult;
import com.spark.insight.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class InsightController {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final ComparisonService comparisonService;
    private final DiagnosisService diagnosisService;
    private final JobService jobService;
    private final ExecutorService executorService;
    private final TaskService taskService;
    private final EnvironmentConfigService envService;

    /**
     * 获取 Job 列表
     */
    @GetMapping("/apps/{appId}/jobs")
    public List<JobModel> listJobs(@PathVariable String appId) {
        return jobService.lambdaQuery().eq(JobModel::getAppId, appId).orderByAsc(JobModel::getJobId).list();
    }

    /**
     * 获取 Environment Config 列表
     */
    @GetMapping("/apps/{appId}/environment")
    public List<EnvironmentConfigModel> listEnvironment(@PathVariable String appId) {
        return envService.lambdaQuery().eq(EnvironmentConfigModel::getAppId, appId).orderByAsc(EnvironmentConfigModel::getParamKey).list();
    }

    /**
     * 获取 Executor 列表
     */
    @GetMapping("/apps/{appId}/executors")
    public List<ExecutorModel> listExecutors(@PathVariable String appId) {
        return executorService.lambdaQuery().eq(ExecutorModel::getAppId, appId).list();
    }

    /**
     * 获取 Stage 下的 Task 分页列表 (超越原生 UI 的关键)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/tasks")
    public List<TaskModel> listTasks(@PathVariable String appId,
                                     @PathVariable Integer stageId,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "100") int size) {
        return taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
                .list();
    }

    /**
     * 获取所有已导入的 Application 列表
     */
    @GetMapping("/apps")
    public List<ApplicationModel> listApps() {
        return applicationService.list();
    }

    /**
     * 获取指定 App 的 Markdown 诊断报告
     */
    @GetMapping("/apps/{appId}/report")
    public String getReport(@PathVariable String appId) {
        return diagnosisService.generateMarkdownReport(appId);
    }

    /**
     * 获取指定 App 的 Stage 详情（包含预计算指标）
     */
    @GetMapping("/apps/{appId}/stages")
    public List<StageModel> listStages(@PathVariable String appId) {
        return stageService.lambdaQuery()
                .eq(StageModel::getAppId, appId)
                .orderByAsc(StageModel::getStageId)
                .list();
    }

    /**
     * 对比两个 Application
     */
    @GetMapping("/compare")
    public AppComparisonResult compare(@RequestParam String appId1, @RequestParam String appId2) {
        return comparisonService.compareApps(appId1, appId2);
    }

    /**
     * 获取 Stage 详细统计指标 (Summary Metrics)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/{attemptId}/stats")
    public List<StageStatisticsModel> getStageStats(@PathVariable String appId,
                                                    @PathVariable Integer stageId,
                                                    @PathVariable Integer attemptId) {
        return stageService.getStageStats(appId, stageId, attemptId);
    }

    /**
     * 获取单个 Application 的元数据
     */
    @GetMapping("/apps/{appId}")
    public ApplicationModel getApp(@PathVariable String appId) {
        return applicationService.getById(appId);
    }
}
