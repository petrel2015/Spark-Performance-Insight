package com.spark.insight.controller;

import com.spark.insight.model.*;
import com.spark.insight.model.dto.AppComparisonResult;
import com.spark.insight.model.dto.ComparisonResult;
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
    private final SqlExecutionService sqlExecutionService;
    private final StorageService storageService;
    private final LLMDiagnosisService llmDiagnosisService;

    private void checkAppReady(String appId) {
        ApplicationModel app = applicationService.getById(appId);
        if (app != null && "PARSING".equals(app.getParsingStatus())) {
            String msg = app.getParsingProgress() != null ? app.getParsingProgress() : "Processing...";
            throw new AppParsingException(msg);
        }
    }

    /**
     * 获取 Job 列表
     */
    @GetMapping("/apps/{appId}/jobs")
    public PageResponse<JobModel> listJobs(@PathVariable String appId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) Integer jobId,
                                           @RequestParam(required = false) String jobGroup,
                                           @RequestParam(required = false) Long sqlExecutionId) {
        checkAppReady(appId);
        var query = jobService.lambdaQuery().eq(JobModel::getAppId, appId);
        if (jobId != null) {
            query.eq(JobModel::getJobId, jobId);
        }
        if (sqlExecutionId != null) {
            query.eq(JobModel::getSqlExecutionId, sqlExecutionId);
        }
        if (jobGroup != null && !jobGroup.isBlank()) {
            query.like(JobModel::getJobGroup, jobGroup); // Fuzzy search for convenience
        }

        long total = query.count();

        // Re-apply conditions for list
        var listQuery = jobService.lambdaQuery().eq(JobModel::getAppId, appId);
        if (jobId != null) {
            listQuery.eq(JobModel::getJobId, jobId);
        }
        if (sqlExecutionId != null) {
            listQuery.eq(JobModel::getSqlExecutionId, sqlExecutionId);
        }
        if (jobGroup != null && !jobGroup.isBlank()) {
            listQuery.like(JobModel::getJobGroup, jobGroup);
        }

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
        JobModel job = jobService.lambdaQuery()
                .eq(JobModel::getAppId, appId)
                .eq(JobModel::getJobId, jobId)
                .one();
        
        if (job != null && job.getStageIds() != null) {
            List<Integer> stageIds = java.util.Arrays.stream(job.getStageIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
            
            if (!stageIds.isEmpty()) {
                List<StageModel> jobStages = stageService.lambdaQuery()
                        .eq(StageModel::getAppId, appId)
                        .in(StageModel::getStageId, stageIds)
                        .list();
                job.setStageList(jobStages);
            }
        }
        return job;
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
     * 获取 RDD 存储列表
     */
    @GetMapping("/apps/{appId}/storage")
    public List<StorageRddModel> listStorage(@PathVariable String appId) {
        checkAppReady(appId);
        return storageService.getRdds(appId);
    }

    /**
     * 获取特定 RDD 的分片明细
     */
    @GetMapping("/apps/{appId}/storage/{rddId}")
    public List<StorageBlockModel> getStorageDetails(@PathVariable String appId, @PathVariable Integer rddId) {
        checkAppReady(appId);
        return storageService.getRddBlocks(appId, rddId);
    }

    /**
     * 获取 Executor 列表
     */
    @GetMapping("/apps/{appId}/executors")
    public List<ExecutorModel> listExecutors(@PathVariable String appId) {
        checkAppReady(appId);
        return executorService.lambdaQuery().eq(ExecutorModel::getAppId, appId).list();
    }

    /**
     * 获取 SQL 执行列表
     */
    @GetMapping("/apps/{appId}/sql")
    public PageResponse<SqlExecutionModel> listSqlExecutions(@PathVariable String appId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(required = false) String sort,
                                                             @RequestParam(required = false) Integer jobId) {
        checkAppReady(appId);
        var query = sqlExecutionService.lambdaQuery().eq(SqlExecutionModel::getAppId, appId);
        if (jobId != null) {
            // 通过子查询找到关联该 Job ID 的 SQL Execution ID
            query.apply("execution_id IN (SELECT sql_execution_id FROM jobs WHERE app_id = {0} AND job_id = {1})", appId, jobId);
        }

        long total = query.count();

        // 重新构建查询以应用分页
        var listQuery = sqlExecutionService.lambdaQuery().eq(SqlExecutionModel::getAppId, appId);
        if (jobId != null) {
            listQuery.apply("execution_id IN (SELECT sql_execution_id FROM jobs WHERE app_id = {0} AND job_id = {1})", appId, jobId);
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "execution_id DESC"));

        List<SqlExecutionModel> items = listQuery.list();
        for (SqlExecutionModel sql : items) {
            List<Integer> jobIds = jobService.lambdaQuery()
                    .eq(JobModel::getAppId, appId)
                    .eq(JobModel::getSqlExecutionId, sql.getExecutionId())
                    .select(JobModel::getJobId)
                    .list()
                    .stream()
                    .map(JobModel::getJobId)
                    .toList();
            sql.setJobIds(jobIds);
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    @GetMapping("/apps/{appId}/sql/{executionId}")
    public SqlExecutionModel getSqlExecution(@PathVariable String appId, @PathVariable Long executionId) {
        checkAppReady(appId);
        SqlExecutionModel sql = sqlExecutionService.lambdaQuery()
                .eq(SqlExecutionModel::getAppId, appId)
                .eq(SqlExecutionModel::getExecutionId, executionId)
                .one();
        
        if (sql != null) {
            List<JobModel> jobList = jobService.lambdaQuery()
                    .eq(JobModel::getAppId, appId)
                    .eq(JobModel::getSqlExecutionId, executionId)
                    .list();
            sql.setJobList(jobList);
            sql.setJobIds(jobList.stream().map(JobModel::getJobId).toList());
        }
        return sql;
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
     * 获取大模型生成的深度诊断报告
     */
    @GetMapping("/apps/{appId}/llm-report")
    public String getLLMReport(
            @PathVariable String appId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        checkAppReady(appId);
        return llmDiagnosisService.generateReport(appId, force);
    }

    /**
     * 获取对比结果
     */
    @GetMapping("/compare/result")
    public ComparisonResult getComparisonResult(
            @RequestParam String type,
            @RequestParam String app1,
            @RequestParam String id1,
            @RequestParam String app2,
            @RequestParam String id2) {
        return comparisonService.compare(type, app1, id1, app2, id2);
    }

    /**
     * 专门用于 Application 级别的对比
     */
    @GetMapping("/compare")
    public ComparisonResult compareApps(
            @RequestParam String appId1,
            @RequestParam String appId2) {
        return comparisonService.compare("app", appId1, appId1, appId2, appId2);
    }

    /**
     * 获取指定 App 的 Stage 详情（包含预计算指标）
     */
    @GetMapping("/apps/{appId}/stages")
    public PageResponse<StageModel> listStages(@PathVariable String appId,
                                               @RequestParam(required = false) Integer jobId,
                                               @RequestParam(required = false) Integer stageId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        checkAppReady(appId);
        var query = stageService.lambdaQuery().eq(StageModel::getAppId, appId);
        if (jobId != null) {
            query.eq(StageModel::getJobId, jobId);
        }
        if (stageId != null) {
            query.eq(StageModel::getStageId, stageId);
        }

        long total = query.count();

        // 重新构建查询以应用分页和排序
        var listQuery = stageService.lambdaQuery().eq(StageModel::getAppId, appId);
        if (jobId != null) {
            listQuery.eq(StageModel::getJobId, jobId);
        }
        if (stageId != null) {
            listQuery.eq(StageModel::getStageId, stageId);
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
