package com.spark.insight.controller;

import com.spark.insight.model.*;
import com.spark.insight.model.dto.ComparisonResult;
import com.spark.insight.model.dto.PageResponse;
import com.spark.insight.service.*;
import com.spark.insight.exception.AppParsingException;
import com.spark.insight.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
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
    private final SparkExecutorService sparkExecutorService;
    private final TaskService taskService;
    private final EnvironmentConfigService envService;
    private final SqlExecutionService sqlExecutionService;
    private final StorageService storageService;
    private final LLMDiagnosisService llmDiagnosisService;
    private final ParsingQueueService parsingQueueService;
    private final ApplicationLogService applicationLogService;

    private void checkAppReady(String appId) {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            throw new ResourceNotFoundException("Application " + appId + " not found");
        }
        
        String status = app.getParsingStatus();
        // 如果状态不是 SUCCESS 或 FAILED，说明数据尚不完整，禁止进入详情
        if (!"SUCCESS".equals(status) && !"FAILED".equals(status)) {
            String msg = app.getParsingProgress() != null ? app.getParsingProgress() : "Application data is not ready (Status: " + (status != null ? status : "DETECTED") + ")";
            throw new AppParsingException(msg);
        }
    }

    /**
     * 获取 Job 列表
     */
    @GetMapping("/apps/{appId}/jobs")
    public PageResponse<GoldJobModel> listJobs(@PathVariable String appId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) Integer jobId,
                                           @RequestParam(required = false) String jobGroup,
                                           @RequestParam(required = false) Long sqlExecutionId) {
        checkAppReady(appId);
        var query = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId);
        if (jobId != null) {
            query.eq(GoldJobModel::getJobId, jobId);
        }
        if (sqlExecutionId != null) {
            query.eq(GoldJobModel::getSqlExecutionId, sqlExecutionId);
        }
        if (jobGroup != null && !jobGroup.isBlank()) {
            query.like(GoldJobModel::getJobGroup, jobGroup); // Fuzzy search for convenience
        }

        long total = query.count();

        // Re-apply conditions for list
        var listQuery = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId);
        if (jobId != null) {
            listQuery.eq(GoldJobModel::getJobId, jobId);
        }
        if (sqlExecutionId != null) {
            listQuery.eq(GoldJobModel::getSqlExecutionId, sqlExecutionId);
        }
        if (jobGroup != null && !jobGroup.isBlank()) {
            listQuery.like(GoldJobModel::getJobGroup, jobGroup);
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "job_id ASC"));

        List<GoldJobModel> items = listQuery.list();
        // Populate stageList for each job to track stage statuses
        for (GoldJobModel job : items) {
            List<GoldStageModel> jobStages = stageService.lambdaQuery()
                    .eq(GoldStageModel::getAppId, appId)
                    .eq(GoldStageModel::getJobId, job.getJobId())
                    .list();
            job.setStageList(jobStages);
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    @Nullable
    @GetMapping("/apps/{appId}/jobs/{jobId}")
    public GoldJobModel getJob(@PathVariable String appId, @PathVariable Integer jobId) {
        checkAppReady(appId);
        GoldJobModel job = jobService.lambdaQuery()
                .eq(GoldJobModel::getAppId, appId)
                .eq(GoldJobModel::getJobId, jobId)
                .one();
        
        if (job != null && job.getStageIds() != null) {
            List<Integer> stageIds = java.util.Arrays.stream(job.getStageIds().split(","))
                    .map(String::trim)
                    .filter(stageIdStr -> {
                        return !stageIdStr.isEmpty();
                    })
                    .map(Integer::parseInt)
                    .toList();
            
            if (!stageIds.isEmpty()) {
                List<GoldStageModel> jobStages = stageService.lambdaQuery()
                        .eq(GoldStageModel::getAppId, appId)
                        .in(GoldStageModel::getStageId, stageIds)
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
    public List<GoldEnvironmentConfigModel> listEnvironment(@PathVariable String appId) {
        checkAppReady(appId);
        return envService.lambdaQuery().eq(GoldEnvironmentConfigModel::getAppId, appId).orderByAsc(GoldEnvironmentConfigModel::getParamKey).list();
    }

    /**
     * 获取 RDD 存储列表
     */
    @GetMapping("/apps/{appId}/storage")
    public List<GoldStorageRddModel> listStorage(@PathVariable String appId) {
        checkAppReady(appId);
        return storageService.getRdds(appId);
    }

    /**
     * 获取特定 RDD 的分片明细
     */
    @GetMapping("/apps/{appId}/storage/{rddId}")
    public List<GoldStorageBlockModel> getStorageDetails(@PathVariable String appId, @PathVariable Integer rddId) {
        checkAppReady(appId);
        return storageService.getRddBlocks(appId, rddId);
    }

    /**
     * 获取 Executor 列表
     */
    @GetMapping("/apps/{appId}/executors")
    public List<GoldExecutorModel> listExecutors(@PathVariable String appId) {
        checkAppReady(appId);
        return sparkExecutorService.lambdaQuery().eq(GoldExecutorModel::getAppId, appId).list();
    }

    /**
     * 获取 SQL 执行列表
     */
    @GetMapping("/apps/{appId}/sql")
    public PageResponse<GoldSqlExecutionModel> listSqlExecutions(@PathVariable String appId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(required = false) String sort,
                                                             @RequestParam(required = false) Integer jobId) {
        checkAppReady(appId);
        var query = sqlExecutionService.lambdaQuery().eq(GoldSqlExecutionModel::getAppId, appId);
        if (jobId != null) {
            // 通过子查询找到关联该 Job ID 的 SQL Execution ID
            query.apply("execution_id IN (SELECT sql_execution_id FROM gold_jobs WHERE app_id = {0} AND job_id = {1})", appId, jobId);
        }

        long total = query.count();

        // 重新构建查询以应用分页
        var listQuery = sqlExecutionService.lambdaQuery().eq(GoldSqlExecutionModel::getAppId, appId);
        if (jobId != null) {
            listQuery.apply("execution_id IN (SELECT sql_execution_id FROM gold_jobs WHERE app_id = {0} AND job_id = {1})", appId, jobId);
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "execution_id DESC"));

        List<GoldSqlExecutionModel> items = listQuery.list();
        for (GoldSqlExecutionModel sql : items) {
            List<Integer> jobIds = jobService.lambdaQuery()
                    .eq(GoldJobModel::getAppId, appId)
                    .eq(GoldJobModel::getSqlExecutionId, sql.getExecutionId())
                    .select(GoldJobModel::getJobId)
                    .list()
                    .stream()
                    .map(GoldJobModel::getJobId)
                    .toList();
            sql.setJobIds(jobIds);
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    @Nullable
    @GetMapping("/apps/{appId}/sql/{executionId}")
    public GoldSqlExecutionModel getSqlExecution(@PathVariable String appId, @PathVariable Long executionId) {
        checkAppReady(appId);
        GoldSqlExecutionModel sql = sqlExecutionService.lambdaQuery()
                .eq(GoldSqlExecutionModel::getAppId, appId)
                .eq(GoldSqlExecutionModel::getExecutionId, executionId)
                .one();
        
        if (sql != null) {
            List<GoldJobModel> jobList = jobService.lambdaQuery()
                    .eq(GoldJobModel::getAppId, appId)
                    .eq(GoldJobModel::getSqlExecutionId, executionId)
                    .list();
            sql.setJobList(jobList);
            sql.setJobIds(jobList.stream().map(GoldJobModel::getJobId).toList());
        }
        return sql;
    }

    @GetMapping("/apps/{appId}/stages/{stageId}/tasks")
    public PageResponse<GoldTaskModel> listTasks(@PathVariable String appId,
                                             @PathVariable Integer stageId,
                                             @RequestParam(required = false) Integer attemptId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        checkAppReady(appId);
        // 1. 获取总数 (使用独立的 QueryWrapper)
        var countQuery = taskService.lambdaQuery()
                .eq(GoldTaskModel::getAppId, appId)
                .eq(GoldTaskModel::getStageId, stageId);
        if (attemptId != null) {
            countQuery.eq(GoldTaskModel::getAttemptId, attemptId);
        }
        long total = countQuery.count();

        // 2. 获取列表 (使用新的 QueryWrapper)
        var listQuery = taskService.lambdaQuery()
                .eq(GoldTaskModel::getAppId, appId)
                .eq(GoldTaskModel::getStageId, stageId);
        if (attemptId != null) {
            listQuery.eq(GoldTaskModel::getAttemptId, attemptId);
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "task_index ASC"));

        List<GoldTaskModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取所有已导入的 Application 列表
     */
    @GetMapping("/apps")
    public PageResponse<GoldApplicationModel> listApps(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(required = false) String search) {
        // listApps needs to return apps even if parsing, so status can be seen
        var query = applicationService.lambdaQuery();
        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search + "%";
            query.and(applicationQuery -> {
                applicationQuery.apply("app_name ILIKE {0}", searchPattern)
                        .or().apply("app_id ILIKE {0}", searchPattern)
                        .or().apply("user_name ILIKE {0}", searchPattern);
            });
        }

        long total = query.count();

        query.last(buildSqlSuffix(sort, page, size, "start_time DESC"));

        List<GoldApplicationModel> items = query.list();
        
        if (!items.isEmpty()) {
            List<String> appIds = items.stream().map(GoldApplicationModel::getAppId).toList();
            java.util.Map<String, String> queueStatus = parsingQueueService.getQueueStatuses(appIds);
            
            for (GoldApplicationModel app : items) {
                if (queueStatus.containsKey(app.getAppId())) {
                    app.setParsingStatus("QUEUED");
                    app.setParsingProgress("Waiting in queue...");
                }
                app.setCompletedStages(applicationLogService.getCompletedStages(app.getAppId()));
            }
        }
        
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
    public PageResponse<GoldStageModel> listStages(@PathVariable String appId,
                                               @RequestParam(required = false) Integer jobId,
                                               @RequestParam(required = false) Integer stageId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        checkAppReady(appId);
        var query = stageService.lambdaQuery().eq(GoldStageModel::getAppId, appId);
        if (jobId != null) {
            query.eq(GoldStageModel::getJobId, jobId);
        }
        if (stageId != null) {
            query.eq(GoldStageModel::getStageId, stageId);
        }

        long total = query.count();

        // 重新构建查询以应用分页和排序
        var listQuery = stageService.lambdaQuery().eq(GoldStageModel::getAppId, appId);
        if (jobId != null) {
            listQuery.eq(GoldStageModel::getJobId, jobId);
        }
        if (stageId != null) {
            listQuery.eq(GoldStageModel::getStageId, stageId);
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "stage_id ASC"));

        List<GoldStageModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取单个 Stage 的元数据
     */
    @Nullable
    @GetMapping("/apps/{appId}/stages/{stageId}")
    public GoldStageModel getStage(@PathVariable String appId,
                               @PathVariable Integer stageId,
                               @RequestParam(required = false) Integer attemptId) {
        checkAppReady(appId);
        return stageService.getStage(appId, stageId, attemptId);
    }

    /**
     * 获取 Stage 详细统计指标 (Summary Metrics)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/{attemptId}/stats")
    public List<GoldStageStatisticsModel> getStageStats(@PathVariable String appId,
                                                    @PathVariable Integer stageId,
                                                    @PathVariable Integer attemptId) {
        checkAppReady(appId);
        return stageService.getStageStats(appId, stageId, attemptId);
    }

    /**
     * 获取 Stage 的所有 Task 数据 (用于 Timeline 可视化)
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/timeline")
    public List<GoldTaskModel> getStageTimeline(@PathVariable String appId,
                                            @PathVariable Integer stageId,
                                            @RequestParam(required = false) Integer attemptId) {
        checkAppReady(appId);
        var query = taskService.lambdaQuery()
                .eq(GoldTaskModel::getAppId, appId)
                .eq(GoldTaskModel::getStageId, stageId);
        if (attemptId != null) {
            query.eq(GoldTaskModel::getAttemptId, attemptId);
        }

        return query.orderByAsc(GoldTaskModel::getLaunchTime).list();
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
    public List<GoldStageModel> getJobStages(@PathVariable String appId, @PathVariable Integer jobId) {
        checkAppReady(appId);
        return stageService.lambdaQuery()
                .eq(GoldStageModel::getAppId, appId)
                .eq(GoldStageModel::getJobId, jobId)
                .orderByAsc(GoldStageModel::getStageId)
                .list();
    }

    /**
     * 获取单个 Application 的元数据
     */
    @Nullable
    @GetMapping("/apps/{appId}")
    public GoldApplicationModel getApp(@PathVariable String appId) {
        // Do NOT checkAppReady here, we need this to check status
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            throw new ResourceNotFoundException("Application " + appId + " not found");
        }
        return app;
    }

    /**
     * 更新 Application 备注
     */
    @PatchMapping("/apps/{appId}/notes")
    public void updateNotes(@PathVariable String appId, @RequestBody String notes) {
        applicationService.lambdaUpdate()
                .eq(GoldApplicationModel::getAppId, appId)
                .set(GoldApplicationModel::getNotes, notes)
                .update();
    }

    /**
     * 批量校验工作区项的有效性
     */
    @PostMapping("/compare/validate")
    public java.util.Map<String, Boolean> validateItems(@RequestBody List<String> itemKeys) {
        java.util.Map<String, Boolean> results = new java.util.HashMap<>();
        for (String key : itemKeys) {
            // Key format: "appId:type:itemId"
            // We split from the right to handle appIds that might contain colons
            int lastColon = key.lastIndexOf(':');
            if (lastColon == -1) continue;
            String itemId = key.substring(lastColon + 1);
            
            String remaining = key.substring(0, lastColon);
            int secondLastColon = remaining.lastIndexOf(':');
            if (secondLastColon == -1) continue;
            
            String type = remaining.substring(secondLastColon + 1);
            String appId = remaining.substring(0, secondLastColon);
            
            boolean exists = false;
            try {
                if ("app".equalsIgnoreCase(type)) {
                    exists = applicationService.getById(appId) != null;
                } else if ("job".equalsIgnoreCase(type)) {
                    exists = jobService.getJob(appId, Integer.parseInt(itemId)) != null;
                } else if ("stage".equalsIgnoreCase(type)) {
                    exists = stageService.getStage(appId, Integer.parseInt(itemId), null) != null;
                }
            } catch (Exception e) {
                exists = false;
            }
            results.put(key, exists);
        }
        return results;
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
                    String column;
                    
                    if ("totalLogSize".equals(field)) {
                        // 按照预估解压后的大小进行排序，逻辑与前端保持一致
                        column = "(total_log_size * CASE " +
                                "WHEN UPPER(compression_format) = 'ZSTD' THEN 10 " +
                                "WHEN UPPER(compression_format) = 'GZIP' THEN 8 " +
                                "WHEN UPPER(compression_format) = 'LZ4' THEN 4 " +
                                "WHEN UPPER(compression_format) = 'SNAPPY' THEN 3 " +
                                "ELSE 1 END)";
                    } else {
                        // 驼峰转蛇形: taskId -> task_id, taskIndex -> task_index
                        column = "\"" + field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase() + "\"";
                    }

                    if (orderBy.length() > 0) {
                        orderBy.append(", ");
                    }
                    orderBy.append(column).append(" ").append(dir);
                }
            }
        }

        String orderClause = orderBy.length() > 0 ? orderBy.toString() : defaultSort;
        return "ORDER BY " + orderClause + " LIMIT " + size + " OFFSET " + (page - 1) * size;
    }
}