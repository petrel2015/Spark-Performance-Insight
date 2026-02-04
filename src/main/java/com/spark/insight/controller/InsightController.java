package com.spark.insight.controller;

import com.spark.insight.model.*;
import com.spark.insight.model.dto.AppComparisonResult;
import com.spark.insight.model.dto.PageResponse;
import com.spark.insight.service.*;
import com.spark.insight.exception.AppParsingException;
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

    private void checkAppReady(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null && "PARSING".equals(app.getParsingStatus())) {
            throw new AppParsingException("The application " + appId + " is currently being parsed. Please try again later.");
        }
    }

    /**
     * 获取 Job 列表
     */
    @GetMapping("/apps/{appId}/jobs")
    public PageResponse<JobModel> listJobs(@PathVariable String appId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String sort) {
        checkAppReady(appId);
        long total = jobService.lambdaQuery().eq(JobModel::getAppId, appId).count();
        var listQuery = jobService.lambdaQuery().eq(JobModel::getAppId, appId);

        listQuery.last(buildSqlSuffix(sort, page, size, "job_id ASC"));

        List<JobModel> items = listQuery.list();
        // Populate stageList for each job to track stage statuses
        for (JobModel job : items) {
            List<StageModel> jobStages = stageService.lambdaQuery()
                    .eq(StageModel::getAppId, appId)
                    .eq(StageModel::getJobId, job.getJobId())
                    .list();
            job.setStageList(jobStages);
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    @GetMapping("/apps/{appId}/jobs/{jobId}")
    public JobModel getJob(@PathVariable String appId, @PathVariable Integer jobId) {
        checkAppReady(appId);
        return jobService.lambdaQuery()
                .eq(JobModel::getAppId, appId)
                .eq(JobModel::getJobId, jobId)
                .one();
    }

    /**
     * 获取 Environment Config 列表
     */
    @GetMapping("/apps/{appId}/environment")
    public List<EnvironmentConfigModel> listEnvironment(@PathVariable String appId) {
        checkAppReady(appId);
        return envService.lambdaQuery().eq(EnvironmentConfigModel::getAppId, appId).orderByAsc(EnvironmentConfigModel::getParamKey).list();
    }

    /**
     * 获取 Executor 列表
     */
    @GetMapping("/apps/{appId}/executors")
    public List<ExecutorModel> listExecutors(@PathVariable String appId) {
        checkAppReady(appId);
        return executorService.lambdaQuery().eq(ExecutorModel::getAppId, appId).list();
    }

    @GetMapping("/apps/{appId}/stages/{stageId}/tasks")
    public PageResponse<TaskModel> listTasks(@PathVariable String appId,
                                             @PathVariable Integer stageId,
                                             @RequestParam(required = false) Integer attemptId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        checkAppReady(appId);
        // 1. 获取总数 (使用独立的 QueryWrapper)
        var countQuery = taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId);
        if (attemptId != null) countQuery.eq(TaskModel::getAttemptId, attemptId);
        long total = countQuery.count();

        // 2. 获取列表 (使用新的 QueryWrapper)
        var listQuery = taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId);
        if (attemptId != null) listQuery.eq(TaskModel::getAttemptId, attemptId);

        listQuery.last(buildSqlSuffix(sort, page, size, "task_index ASC"));

        List<TaskModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取所有已导入的 Application 列表
     */
    @GetMapping("/apps")
    public PageResponse<ApplicationModel> listApps(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(required = false) String search) {
        // listApps needs to return apps even if parsing, so status can be seen
        var query = applicationService.lambdaQuery();
        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search + "%";
            query.and(q -> q.apply("app_name ILIKE {0}", searchPattern)
                    .or().apply("app_id ILIKE {0}", searchPattern)
                    .or().apply("user_name ILIKE {0}", searchPattern));
        }

        long total = query.count();

        query.last(buildSqlSuffix(sort, page, size, "start_time DESC"));

        List<ApplicationModel> items = query.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取指定 App 的 Markdown 诊断报告
     */
    @GetMapping("/apps/{appId}/report")
    public String getReport(@PathVariable String appId) {
        checkAppReady(appId);
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
        checkAppReady(appId);
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
    public StageModel getStage(@PathVariable String appId, 
                               @PathVariable Integer stageId,
                               @RequestParam(required = false) Integer attemptId) {
        checkAppReady(appId);
        var query = stageService.lambdaQuery()
                .eq(StageModel::getAppId, appId)
                .eq(StageModel::getStageId, stageId);
        
        if (attemptId != null) {
            query.eq(StageModel::getAttemptId, attemptId);
        } else {
            query.orderByDesc(StageModel::getAttemptId);
            query.last("LIMIT 1");
        }
        
        return query.one();
    }

    /**
     * 对比两个 Application
     */
    @GetMapping("/compare")
    public AppComparisonResult compare(@RequestParam String appId1, @RequestParam String appId2) {
        checkAppReady(appId1);
        checkAppReady(appId2);
        return comparisonService.compareApps(appId1, appId2);
    }

    /**
     * 获取 Stage 详细统计指标 (Summary Metrics)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/{attemptId}/stats")
    public List<StageStatisticsModel> getStageStats(@PathVariable String appId,
                                                    @PathVariable Integer stageId,
                                                    @PathVariable Integer attemptId) {
        checkAppReady(appId);
        return stageService.getStageStats(appId, stageId, attemptId);
    }

    /**
     * 获取 Stage 的所有 Task 数据 (用于 Timeline 可视化)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/timeline")
    public List<TaskModel> getStageTimeline(@PathVariable String appId, 
                                            @PathVariable Integer stageId,
                                            @RequestParam(required = false) Integer attemptId) {
        checkAppReady(appId);
        var query = taskService.lambdaQuery()
                .eq(TaskModel::getAppId, appId)
                .eq(TaskModel::getStageId, stageId);
        if (attemptId != null) query.eq(TaskModel::getAttemptId, attemptId);
        
        return query.orderByAsc(TaskModel::getLaunchTime).list();
    }

    /**
     * 获取 Stage 按 Executor 聚合的统计数据
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/executor-summary")
    public List<java.util.Map<String, Object>> getExecutorSummary(@PathVariable String appId,
                                                                  @PathVariable Integer stageId,
                                                                  @RequestParam(required = false) Integer attemptId) {
        checkAppReady(appId);
        return stageService.getExecutorSummary(appId, stageId, attemptId);
    }

    @GetMapping("/apps/{appId}/jobs/{jobId}/executor-summary")
    public List<java.util.Map<String, Object>> getJobExecutorSummary(@PathVariable String appId,
                                                                     @PathVariable Integer jobId) {
        checkAppReady(appId);
        return stageService.getJobExecutorSummary(appId, jobId);
    }

    @GetMapping("/apps/{appId}/jobs/{jobId}/stages")
    public List<StageModel> getJobStages(@PathVariable String appId, @PathVariable Integer jobId) {
        checkAppReady(appId);
        return stageService.lambdaQuery()
                .eq(StageModel::getAppId, appId)
                .eq(StageModel::getJobId, jobId)
                .orderByAsc(StageModel::getStageId)
                .list();
    }

    /**
     * 获取单个 Application 的元数据
     */
    @GetMapping("/apps/{appId}")
    public ApplicationModel getApp(@PathVariable String appId) {
        // Do NOT checkAppReady here, we need this to check status
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
                    orderBy.append("\"").append(column).append("\"").append(" ").append(dir);
                }
            }
        }

        String orderClause = orderBy.length() > 0 ? orderBy.toString() : defaultSort;
        return "ORDER BY " + orderClause + " LIMIT " + size + " OFFSET " + (page - 1) * size;
    }
}
