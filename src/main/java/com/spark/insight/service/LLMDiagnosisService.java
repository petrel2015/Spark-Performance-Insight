package com.spark.insight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.insight.llm.LLMClient;
import com.spark.insight.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMDiagnosisService {

    private final ApplicationService applicationService;
    private final StageService stageService;
    private final EnvironmentConfigService envService;
    private final ObjectMapper objectMapper;
    private final LLMClient llmClient;

    private static final String SYSTEM_PROMPT = """
            # Role
            你是一位资深的 Spark 性能调优专家，拥有处理分布式计算瓶颈 and JVM 优化的丰富经验。
            
            # Task
            请根据提供的 Spark Application 指标上下文 (JSON)，生成一份严谨、高度专业且具备实操性的技术诊断报告。
            
            # Constraints
            - 必须使用 **中文** 进行回答。
            - 所有的 **技术专有名词、Spark 参数名、代码片段** 必须保持 **英文** 原称（例如：Shuffle Blocked Time, GC, spark.sql.shuffle.partitions, Broadcast Join 等）。
            - 禁止使用任何 Emoji 符号。
            - 使用 Markdown 格式进行结构化排版。
            - 聚焦于识别 "Critical Path" (关键路径) 和资源浪费。
            - **不要引用或依赖任何未在上下文中提供的自定义评分（如 performance_score），请直接根据原始指标（Duration, GC Time, Shuffle, Spill 等）进行独立分析。**
            
            # Required Report Structure
            
            ## 1. 执行摘要 (Executive Summary)
            简要总结应用的整体健康状况。明确说明该应用是受限于 CPU、Memory 还是 I/O，并指出最耗时的阶段。
            
            ## 2. 核心瓶颈识别 (Bottleneck Identification)
            分析并列出应用中的主要技术瓶颈。
            重点分析以下维度：
            - **Shuffle Blocked Time**: 网络传输或上游 Stage 依赖问题。
            - **I/O Wait**: 外部存储吞吐量瓶颈。
            - **GC Impact**: 内存管理及对象生命周期问题。
            - **Disk Spill**: 内存不足导致的磁盘溢写。
            
            ## 3. 高风险任务深度分析 (High-Risk Task Analysis)
            针对提供的 Top Slowest Stages 进行分析。
            针对每一项，提供：
            - **症状 (Symptoms)**: (例如：严重的 Data Skew, Disk Spill, 高 GC 占比)。
            - **根因 (Root Cause)**: 最可能的架构设计或数据分布原因。
            
            ## 4. 针对性调优建议 (Specific Tuning Recommendations)
            提供具体的、可直接应用的配置变更或代码重构方案。
            分为以下两个板块：
            - **配置参数调整 (Configuration Changes)**: (例如：`spark.sql.shuffle.partitions`, `spark.memory.fraction`)。
            - **代码/SQL 优化方案 (Code/SQL Optimization)**: (例如：使用 "Broadcast Join", 为 Join Key 增加 "Salting" 等)。
            """;

    @Nullable
    public String generateReport(String appId, boolean force) {
        try {
            // 1. 检查缓存或生成状态
            GoldApplicationModel app = applicationService.getById(appId);
            if (app != null && !force) {
                String existingReport = app.getLlmReport();
                if (existingReport != null) {
                    if (existingReport.startsWith("[GENERATING]")) {
                        return "GENERATING";
                    }
                    if (!existingReport.isEmpty() && !existingReport.startsWith("Failed")) {
                        log.info("Returning cached LLM report for app: {}", appId);
                        return existingReport;
                    }
                }
            }

            log.info(">>> Starting {}LLM diagnosis for app: {}", force ? "FORCED " : "", appId);

            // 2. 标记为生成中 (记录开始时间)
            applicationService.lambdaUpdate()
                    .eq(GoldApplicationModel::getAppId, appId)
                    .set(GoldApplicationModel::getLlmReport, "[GENERATING]")
                    .set(GoldApplicationModel::getLlmStartTime, System.currentTimeMillis())
                    .set(GoldApplicationModel::getLlmEndTime, null)
                    .update();

            Map<String, Object> contextMap = buildAnalysisContextMap(appId);
            String compactJson = objectMapper.writeValueAsString(contextMap);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(contextMap);
            
            log.info(">>> Sending request to LLM for app: {}", appId);
            log.info(">>> [System Prompt]:\n{}", SYSTEM_PROMPT);
            log.info(">>> [Metrics Context JSON (Pretty Print for Log)]:\n{}", prettyJson);
            
            // 3. 调用 AI 接口
            String report = llmClient.generate(SYSTEM_PROMPT, "Spark Application Metrics Context:\n" + compactJson);

            log.info(">>> [LLM Response]:\n{}", report);

            // 4. 更新结果 (记录结束时间)
            if (report != null && !report.isEmpty()) {
                applicationService.lambdaUpdate()
                        .eq(GoldApplicationModel::getAppId, appId)
                        .set(GoldApplicationModel::getLlmReport, report)
                        .set(GoldApplicationModel::getLlmEndTime, System.currentTimeMillis())
                        .update();
            } else {
                applicationService.lambdaUpdate()
                        .eq(GoldApplicationModel::getAppId, appId)
                        .set(GoldApplicationModel::getLlmReport, null)
                        .update();
            }
            
            return report;
            
        } catch (Exception exception) {
            log.error("LLM Diagnosis failed", exception);
            // 重置状态
            applicationService.lambdaUpdate()
                    .eq(GoldApplicationModel::getAppId, appId)
                    .set(GoldApplicationModel::getLlmReport, null)
                    .update();
            return "Failed to generate deep diagnosis report: " + exception.getMessage();
        }
    }

    private Map<String, Object> buildAnalysisContextMap(String appId) {
        try {
            GoldApplicationModel app = applicationService.getById(appId);
            if (app == null) {
                return new HashMap<>();
            }

            Map<String, Object> context = new HashMap<>();
            
            // 1. App Metadata (原始信息)
            context.put("application", Map.of(
                "name", app.getAppName(),
                "duration_ms", app.getDuration() != null ? app.getDuration() : 0,
                "spark_version", app.getSparkVersion()
            ));

            // 2. Top 10 Slowest Stages (纯原始指标，不含评分)
            List<GoldStageModel> stages = stageService.lambdaQuery()
                    .eq(GoldStageModel::getAppId, appId)
                    .orderByDesc(GoldStageModel::getDuration) // 按耗时倒序
                    .last("LIMIT 10")
                    .list();
            
            context.put("top_10_slowest_stages", stages.stream().map(stage -> {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("stage_id", stage.getStageId());
                metrics.put("name", stage.getStageName());
                metrics.put("duration_ms", stage.getDuration());
                metrics.put("num_tasks", stage.getNumTasks());
                // GC Time
                metrics.put("gc_time_ms", stage.getGcTimeSum());
                // I/O & Shuffle (原始字节数/时间)
                metrics.put("input_bytes", stage.getInputBytes());
                metrics.put("output_bytes", stage.getOutputBytes());
                metrics.put("shuffle_read_bytes", stage.getShuffleReadBytes());
                metrics.put("shuffle_write_bytes", stage.getShuffleWriteBytes());
                metrics.put("shuffle_write_time_ns", stage.getShuffleWriteTimeSum());
                // Disk Spill
                metrics.put("disk_spill_bytes", stage.getDiskBytesSpilledSum());
                metrics.put("memory_spill_bytes", stage.getMemoryBytesSpilledSum());
                // Task Skew Indicators
                metrics.put("max_task_duration_ms", stage.getMaxTaskDuration());
                metrics.put("median_task_duration_ms", stage.getDurationP50());
                metrics.put("p95_task_duration_ms", stage.getDurationP95());
                return metrics;
            }).collect(Collectors.toList()));

            // 3. Environment Configs (全量关键参数)
            List<GoldEnvironmentConfigModel> allConfigs = envService.lambdaQuery()
                    .eq(GoldEnvironmentConfigModel::getAppId, appId)
                    .list();
            
            Map<String, String> relevantConfigs = allConfigs.stream()
                    .filter(config -> {
                        return isRelevantConfig(config.getParamKey());
                    })
                    .collect(Collectors.toMap(GoldEnvironmentConfigModel::getParamKey, GoldEnvironmentConfigModel::getParamValue, (value1, value2) -> {
                        return value1;
                    }));
            context.put("spark_configuration", relevantConfigs);

            return context;
        } catch (Exception exception) {
            log.error("Error building context", exception);
            return new HashMap<>();
        }
    }

    @Nullable
    private String buildAnalysisContext(String appId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(buildAnalysisContextMap(appId));
        } catch (Exception exception) {
            return "{}";
        }
    }

    private boolean isRelevantConfig(String key) {
        if (key == null) {
            return false;
        }
        // 排除一些无用的内部参数，保留大部分调优相关参数
        if (key.startsWith("spark.app.") || key.contains("classpath") || key.contains("library.path")) {
            return false;
        }
        
        return key.startsWith("spark.executor") || 
               key.startsWith("spark.driver") ||
               key.startsWith("spark.sql") ||
               key.startsWith("spark.memory") ||
               key.startsWith("spark.reducer") ||
               key.startsWith("spark.shuffle") ||
               key.startsWith("spark.default") ||
               key.startsWith("spark.dynamicAllocation") ||
               key.startsWith("spark.serializer") ||
               key.startsWith("spark.kryoserializer");
    }
}
