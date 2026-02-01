package com.spark.insight.controller;

import com.spark.insight.model.*;
import com.spark.insight.model.dto.AppComparisonResult;
import com.spark.insight.model.dto.PageResponse;
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
    public PageResponse<JobModel> listJobs(@PathVariable String appId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String sort) {
        long total = jobService.lambdaQuery().eq(JobModel::getAppId, appId).count();
        var listQuery = jobService.lambdaQuery().eq(JobModel::getAppId, appId);

        listQuery.last(buildSqlSuffix(sort, page, size, "job_id ASC"));

        List<JobModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
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

    @GetMapping("/apps/{appId}/stages/{stageId}/tasks")
    public PageResponse<TaskModel> listTasks(@PathVariable String appId,
                                             @PathVariable Integer stageId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        // 1. 获取总数 (使用独立的 QueryWrapper)
        long total = taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId)
                .count();

        // 2. 获取列表 (使用新的 QueryWrapper)
        var listQuery = taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId);

        listQuery.last(buildSqlSuffix(sort, page, size, "task_index ASC"));

        List<TaskModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
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
    public PageResponse<StageModel> listStages(@PathVariable String appId,
                                               @RequestParam(required = false) Integer jobId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        var query = stageService.lambdaQuery().eq(StageModel::getAppId, appId);
        if (jobId != null) {
            query.eq(StageModel::getJobId, jobId);
        }

        long total = query.count();
        
        // 重新构建查询以应用分页和排序
        var listQuery = stageService.lambdaQuery().eq(StageModel::getAppId, appId);
        if (jobId != null) {
            listQuery.eq(StageModel::getJobId, jobId);
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "stage_id ASC"));

        List<StageModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取单个 Stage 的元数据
     */
    @GetMapping("/apps/{appId}/stages/{stageId}")
    public StageModel getStage(@PathVariable String appId, @PathVariable Integer stageId) {
        return stageService.lambdaQuery()
                .eq(StageModel::getAppId, appId)
                .eq(StageModel::getStageId, stageId)
                .one();
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
     * 获取 Stage 的所有 Task 数据 (用于 Timeline 可视化)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/timeline")
    public List<TaskModel> getStageTimeline(@PathVariable String appId, @PathVariable Integer stageId) {
        return taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId)
                .orderByAsc(TaskModel::getLaunchTime)
                .list();
    }

    /**
     * 获取 Stage 按 Executor 聚合的统计数据
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/executor-summary")
    public List<java.util.Map<String, Object>> getExecutorSummary(@PathVariable String appId,
                                                                  @PathVariable Integer stageId) {
        return stageService.getExecutorSummary(appId, stageId);
    }

    @GetMapping("/apps/{appId}/jobs/{jobId}/executor-summary")
    public List<java.util.Map<String, Object>> getJobExecutorSummary(@PathVariable String appId,
                                                                     @PathVariable Integer jobId) {
        return stageService.getJobExecutorSummary(appId, jobId);
    }

    /**
     * 获取单个 Application 的元数据
     */
    @GetMapping("/apps/{appId}")
    public ApplicationModel getApp(@PathVariable String appId) {
        return applicationService.getById(appId);
    }

    private String buildSqlSuffix(String sort, int page, int size, String defaultSort) {
        StringBuilder orderBy = new StringBuilder();
        if (sort != null && !sort.isBlank()) {
            String[] orders = sort.split(";");
            for (String order : orders) {
                String[] parts = order.split(",");
                if (parts.length == 2) {
                    String field = parts[0];
                    String dir = "asc".equalsIgnoreCase(parts[1]) ? "ASC" : "DESC";
                    // 驼峰转蛇形: taskId -> task_id, taskIndex -> task_index
                    String column = field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
                    if (orderBy.length() > 0) orderBy.append(", ");
                    orderBy.append(column).append(" ").append(dir);
                }
            }
        }

        String orderClause = orderBy.length() > 0 ? orderBy.toString() : defaultSort;
        return "ORDER BY " + orderClause + " LIMIT " + size + " OFFSET " + (page - 1) * size;
    }
}
