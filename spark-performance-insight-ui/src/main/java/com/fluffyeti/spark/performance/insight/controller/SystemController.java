package com.fluffyeti.spark.performance.insight.controller;

import com.fluffyeti.spark.performance.insight.model.*;
import com.fluffyeti.spark.performance.insight.model.dto.ComparisonResult;
import com.fluffyeti.spark.performance.insight.model.dto.PageResponse;
import com.fluffyeti.spark.performance.insight.service.*;
import com.fluffyeti.spark.performance.insight.exception.AppParsingException;
import com.fluffyeti.spark.performance.insight.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class SystemController {

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
    private final StorageBlockService storageBlockService;
    private final LLMDiagnosisService llmDiagnosisService;
    private final ParsingQueueService parsingQueueService;
    private final ApplicationLogService applicationLogService;

    private void checkAppReady(String appId) {
        GoldApplicationModel app = applicationService.getById(appId);
        if (app == null) {
            throw new ResourceNotFoundException("Application " + appId + " not found");
        }
        
        String status = app.getParsingStatus();
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
                                           @RequestParam(required = false) Long jobId,
                                           @RequestParam(required = false) String jobGroup,
                                           @RequestParam(required = false) Long sqlExecutionId) {
        checkAppReady(appId);
        var query = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId);
        if (jobId != null) query.eq(GoldJobModel::getJobId, jobId);
        if (sqlExecutionId != null) query.eq(GoldJobModel::getSqlExecutionId, sqlExecutionId);
        if (jobGroup != null && !jobGroup.isBlank()) query.like(GoldJobModel::getJobGroup, jobGroup);

        long total = query.count();

        var listQuery = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId);
        if (jobId != null) listQuery.eq(GoldJobModel::getJobId, jobId);
        if (sqlExecutionId != null) listQuery.eq(GoldJobModel::getSqlExecutionId, sqlExecutionId);
        if (jobGroup != null && !jobGroup.isBlank()) listQuery.like(GoldJobModel::getJobGroup, jobGroup);

        listQuery.last(buildSqlSuffix(sort, page, size, "job_id ASC"));

        List<GoldJobModel> items = listQuery.list();
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

    /**
     * 获取应用对比结果
     */
    @GetMapping("/compare")
    public ComparisonResult compare(@RequestParam String type, @RequestParam String baseAppId, @RequestParam String baseId, @RequestParam String targetAppId, @RequestParam String targetId) {
        checkAppReady(baseAppId);
        checkAppReady(targetAppId);
        return comparisonService.compare(type, baseAppId, baseId, targetAppId, targetId);
    }

    /**
     * 获取性能诊断报告
     */
    @GetMapping({"/apps/{appId}/diagnosis", "/apps/{appId}/report"})
    public String getDiagnosis(@PathVariable String appId) {
        checkAppReady(appId);
        return diagnosisService.generateMarkdownReport(appId);
    }

    /**
     * 获取 LLM 性能诊断报告
     */
    @GetMapping({"/apps/{appId}/diagnosis/llm", "/apps/{appId}/llm-report"})
    public String getLLMDiagnosis(@PathVariable String appId, @RequestParam(defaultValue = "false") boolean force) {
        checkAppReady(appId);
        return llmDiagnosisService.generateReport(appId, force);
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
    public PageResponse<GoldStorageRddModel> listStorage(@PathVariable String appId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) String sort,
                                                     @RequestParam(required = false) String search) {
        checkAppReady(appId);
        var query = storageService.lambdaQuery().eq(GoldStorageRddModel::getAppId, appId);
        if (search != null && !search.isBlank()) {
            query.and(q -> q.like(GoldStorageRddModel::getName, search)
                    .or().eq(GoldStorageRddModel::getRddId, search.replaceAll("[^0-9]", "")));
        }
        long total = query.count();

        var listQuery = storageService.lambdaQuery().eq(GoldStorageRddModel::getAppId, appId);
        if (search != null && !search.isBlank()) {
            listQuery.and(q -> q.like(GoldStorageRddModel::getName, search)
                    .or().eq(GoldStorageRddModel::getRddId, search.replaceAll("[^0-9]", "")));
        }
        listQuery.last(buildSqlSuffix(sort, page, size, "rdd_id ASC"));
        
        List<GoldStorageRddModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取单个 RDD 的元数据
     */
    @GetMapping("/apps/{appId}/storage/rdd/{rddId}")
    public GoldStorageRddModel getRdd(@PathVariable String appId, @PathVariable Long rddId) {
        checkAppReady(appId);
        return storageService.lambdaQuery()
                .eq(GoldStorageRddModel::getAppId, appId)
                .eq(GoldStorageRddModel::getRddId, rddId)
                .one();
    }

    /**
     * 获取特定 RDD 的分片明细
     */
    @GetMapping("/apps/{appId}/storage/{rddId}")
    public PageResponse<GoldStorageBlockModel> getStorageDetails(@PathVariable String appId, 
                                                             @PathVariable Long rddId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(required = false) String sort) {
        checkAppReady(appId);
        var query = storageBlockService.lambdaQuery()
                .eq(GoldStorageBlockModel::getAppId, appId)
                .eq(GoldStorageBlockModel::getRddId, rddId);
        long total = query.count();

        var listQuery = storageBlockService.lambdaQuery()
                .eq(GoldStorageBlockModel::getAppId, appId)
                .eq(GoldStorageBlockModel::getRddId, rddId);
        listQuery.last(buildSqlSuffix(sort, page, size, "block_name ASC"));

        List<GoldStorageBlockModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
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
                                                             @RequestParam(required = false) Long jobId) {
        checkAppReady(appId);
        var query = sqlExecutionService.lambdaQuery().eq(GoldSqlExecutionModel::getAppId, appId);
        if (jobId != null) {
            GoldJobModel job = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId).eq(GoldJobModel::getJobId, jobId).one();
            if (job != null && job.getSqlExecutionId() != null) query.eq(GoldSqlExecutionModel::getExecutionId, job.getSqlExecutionId());
            else return new PageResponse<>(List.of(), 0, page, size, 0);
        }

        long total = query.count();

        var listQuery = sqlExecutionService.lambdaQuery().eq(GoldSqlExecutionModel::getAppId, appId);
        if (jobId != null) {
            GoldJobModel job = jobService.lambdaQuery().eq(GoldJobModel::getAppId, appId).eq(GoldJobModel::getJobId, jobId).one();
            if (job != null && job.getSqlExecutionId() != null) listQuery.eq(GoldSqlExecutionModel::getExecutionId, job.getSqlExecutionId());
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "execution_id DESC"));

        List<GoldSqlExecutionModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取特定 SQL 执行详情
     */
    @GetMapping("/apps/{appId}/sql/{executionId}")
    public GoldSqlExecutionModel getSqlExecution(@PathVariable String appId, @PathVariable Long executionId) {
        checkAppReady(appId);
        return sqlExecutionService.lambdaQuery()
                .eq(GoldSqlExecutionModel::getAppId, appId)
                .eq(GoldSqlExecutionModel::getExecutionId, executionId)
                .one();
    }

    /**
     * 获取 Task 列表
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/tasks")
    public PageResponse<GoldTaskModel> listTasks(@PathVariable String appId,
                                             @PathVariable Long stageId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) Long attemptId) {
        checkAppReady(appId);
        var query = taskService.lambdaQuery()
                .eq(GoldTaskModel::getAppId, appId)
                .eq(GoldTaskModel::getStageId, stageId);
        if (attemptId != null) query.eq(GoldTaskModel::getAttemptId, attemptId);

        long total = query.count();

        var listQuery = taskService.lambdaQuery()
                .eq(GoldTaskModel::getAppId, appId)
                .eq(GoldTaskModel::getStageId, stageId);
        if (attemptId != null) listQuery.eq(GoldTaskModel::getAttemptId, attemptId);

        listQuery.last(buildSqlSuffix(sort, page, size, "task_id ASC"));

        List<GoldTaskModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取应用列表
     */
    @GetMapping("/apps")
    public PageResponse<GoldApplicationModel> listApps(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(required = false) String search) {
        var query = applicationService.lambdaQuery();
        if (search != null && !search.isBlank()) {
            query.and(q -> q.like(GoldApplicationModel::getAppName, search)
                    .or().like(GoldApplicationModel::getAppId, search));
        }

        long total = query.count();

        var listQuery = applicationService.lambdaQuery();
        if (search != null && !search.isBlank()) {
            listQuery.and(q -> q.like(GoldApplicationModel::getAppName, search)
                    .or().like(GoldApplicationModel::getAppId, search));
        }

        listQuery.last(buildSqlSuffix(sort, page, size, "start_time DESC"));

        List<GoldApplicationModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    /**
     * 获取单个应用详情
     */
    @GetMapping("/apps/{appId}")
    public GoldApplicationModel getApp(@PathVariable String appId) {
        return applicationService.getById(appId);
    }

    /**
     * 获取单个 Stage 详情
     */
    @GetMapping("/apps/{appId}/stages/{stageId}")
    public GoldStageModel getStage(@PathVariable String appId, 
                                 @PathVariable Long stageId,
                                 @RequestParam(required = false) Long attemptId) {
        checkAppReady(appId);
        return stageService.getStage(appId, stageId, attemptId);
    }

    /**
     * 获取特定 Stage 的统计信息分布
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/statistics")
    public List<GoldStageStatisticsModel> getStageStatistics(@PathVariable String appId, 
                                                           @PathVariable Long stageId,
                                                           @RequestParam(required = false) Long attemptId) {
        checkAppReady(appId);
        if (attemptId == null) {
            GoldStageModel stage = getStage(appId, stageId, null);
            if (stage != null) attemptId = stage.getAttemptId();
        }
        return stageService.getStageStats(appId, stageId, attemptId);
    }

    /**
     * 获取特定 Stage 的 Executor 汇总
     */
    @GetMapping("/apps/{appId}/stages/{stageId}/executors")
    public List<Map<String, Object>> getStageExecutorSummary(@PathVariable String appId, 
                                              @PathVariable Long stageId,
                                              @RequestParam(required = false) Long attemptId) {
        checkAppReady(appId);
        return stageService.getExecutorSummary(appId, stageId, attemptId);
    }

    /**
     * 获取特定 Job 的 Executor 汇总
     */
    @GetMapping("/apps/{appId}/jobs/{jobId}/executors")
    public List<Map<String, Object>> getJobExecutorSummary(@PathVariable String appId, @PathVariable Long jobId) {
        checkAppReady(appId);
        return stageService.getJobExecutorSummary(appId, jobId);
    }

    /**
     * 获取 Stage 列表
     */
    @GetMapping("/apps/{appId}/stages")
    public PageResponse<GoldStageModel> listStages(@PathVariable String appId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) Long jobId,
                                               @RequestParam(required = false) Long stageId) {
        checkAppReady(appId);
        var query = stageService.lambdaQuery().eq(GoldStageModel::getAppId, appId);
        if (jobId != null) query.eq(GoldStageModel::getJobId, jobId);
        if (stageId != null) query.eq(GoldStageModel::getStageId, stageId);

        long total = query.count();

        var listQuery = stageService.lambdaQuery().eq(GoldStageModel::getAppId, appId);
        if (jobId != null) listQuery.eq(GoldStageModel::getJobId, jobId);
        if (stageId != null) listQuery.eq(GoldStageModel::getStageId, stageId);

        listQuery.last(buildSqlSuffix(sort, page, size, "stage_id ASC"));

        List<GoldStageModel> items = listQuery.list();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }

    private String buildSqlSuffix(String sort, int page, int size, String defaultSort) {
        String orderClause = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length == 2) {
                String field = camelToSnake(parts[0]);
                String direction = parts[1].toUpperCase();
                if ("ASC".equals(direction) || "DESC".equals(direction)) {
                    orderClause = field + " " + direction;
                }
            }
        }
        return "ORDER BY " + orderClause + " LIMIT " + size + " OFFSET " + (long) (page - 1) * size;
    }

    private String camelToSnake(String str) {
        if (str == null) return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) result.append('_').append(Character.toLowerCase(c));
            else result.append(c);
        }
        return result.toString();
    }
}
