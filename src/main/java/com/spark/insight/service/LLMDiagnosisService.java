package com.spark.insight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.insight.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.ai.zhipuai.api-key}")
    private String apiKey;

    @Value("${spring.ai.zhipuai.chat.options.model:glm-4.7}")
    private String modelName;

    private static final String SYSTEM_PROMPT = """
            # Role
            你是一位资深的 Spark 性能调优专家，拥有处理分布式计算瓶颈和 JVM 优化的丰富经验。
            
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

    public String generateReport(String appId, boolean force) {
        try {
            // 1. 检查缓存或生成状态
            ApplicationModel app = applicationService.getById(appId);
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
                    .eq(ApplicationModel::getAppId, appId)
                    .set(ApplicationModel::getLlmReport, "[GENERATING]")
                    .set(ApplicationModel::getLlmStartTime, System.currentTimeMillis())
                    .set(ApplicationModel::getLlmEndTime, null)
                    .update();

            Map<String, Object> contextMap = buildAnalysisContextMap(appId);
            String compactJson = objectMapper.writeValueAsString(contextMap);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(contextMap);
            
            log.info(">>> Sending request to LLM for app: {}", appId);
            log.info(">>> [System Prompt]:\n{}", SYSTEM_PROMPT);
            log.info(">>> [Metrics Context JSON (Pretty Print for Log)]:\n{}", prettyJson);
            
            // 3. 调用 AI (1.0.0-M1 原生 SDK)
            ZhiPuAiApi zhipuAiApi = new ZhiPuAiApi(apiKey);
            ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(zhipuAiApi);
            
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultOptions(ZhiPuAiChatOptions.builder()
                            .withModel(modelName)
                            .withTemperature(0.1f)
                            .build())
                    .build();
            
            String report = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Spark Application Metrics Context:\n" + compactJson)
                    .call()
                    .content();

            log.info(">>> [LLM Response]:\n{}", report);

            // 4. 更新结果 (记录结束时间)
            if (report != null && !report.isEmpty()) {
                applicationService.lambdaUpdate()
                        .eq(ApplicationModel::getAppId, appId)
                        .set(ApplicationModel::getLlmReport, report)
                        .set(ApplicationModel::getLlmEndTime, System.currentTimeMillis())
                        .update();
            } else {
                applicationService.lambdaUpdate()
                        .eq(ApplicationModel::getAppId, appId)
                        .set(ApplicationModel::getLlmReport, null)
                        .update();
            }
            
            return report;
            
        } catch (Exception e) {
            log.error("LLM Diagnosis failed", e);
            // 重置状态
            applicationService.lambdaUpdate()
                    .eq(ApplicationModel::getAppId, appId)
                    .set(ApplicationModel::getLlmReport, null)
                    .update();
            return "Failed to generate deep diagnosis report: " + e.getMessage();
        }
    }

    private Map<String, Object> buildAnalysisContextMap(String appId) {
        try {
            ApplicationModel app = applicationService.getById(appId);
            if (app == null) return new HashMap<>();

            Map<String, Object> ctx = new HashMap<>();
            
            // 1. App Metadata (原始信息)
            ctx.put("application", Map.of(
                "name", app.getAppName(),
                "duration_ms", app.getDuration() != null ? app.getDuration() : 0,
                "spark_version", app.getSparkVersion()
            ));

            // 2. Top 10 Slowest Stages (纯原始指标，不含评分)
            List<StageModel> stages = stageService.lambdaQuery()
                    .eq(StageModel::getAppId, appId)
                    .orderByDesc(StageModel::getDuration) // 按耗时倒序
                    .last("LIMIT 10")
                    .list();
            
            ctx.put("top_10_slowest_stages", stages.stream().map(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("stage_id", s.getStageId());
                m.put("name", s.getStageName());
                m.put("duration_ms", s.getDuration());
                m.put("num_tasks", s.getNumTasks());
                // GC Time
                m.put("gc_time_ms", s.getGcTimeSum());
                // I/O & Shuffle (原始字节数/时间)
                m.put("input_bytes", s.getInputBytes());
                m.put("output_bytes", s.getOutputBytes());
                m.put("shuffle_read_bytes", s.getShuffleReadBytes());
                m.put("shuffle_write_bytes", s.getShuffleWriteBytes());
                m.put("shuffle_write_time_ns", s.getShuffleWriteTimeSum());
                // Disk Spill
                m.put("disk_spill_bytes", s.getDiskBytesSpilledSum());
                m.put("memory_spill_bytes", s.getMemoryBytesSpilledSum());
                // Task Skew Indicators
                m.put("max_task_duration_ms", s.getMaxTaskDuration());
                m.put("median_task_duration_ms", s.getDurationP50());
                m.put("p95_task_duration_ms", s.getDurationP95());
                return m;
            }).collect(Collectors.toList()));

            // 3. Environment Configs (全量关键参数)
            List<EnvironmentConfigModel> allConfigs = envService.lambdaQuery()
                    .eq(EnvironmentConfigModel::getAppId, appId)
                    .list();
            
            Map<String, String> configs = allConfigs.stream()
                    .filter(c -> isRelevantConfig(c.getParamKey()))
                    .collect(Collectors.toMap(EnvironmentConfigModel::getParamKey, EnvironmentConfigModel::getParamValue, (v1, v2) -> v1));
            ctx.put("spark_configuration", configs);

            return ctx;
        } catch (Exception e) {
            log.error("Error building context", e);
            return new HashMap<>();
        }
    }

    private String buildAnalysisContext(String appId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(buildAnalysisContextMap(appId));
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isRelevantConfig(String key) {
        if (key == null) return false;
        // 排除一些无用的内部参数，保留大部分调优相关参数
        if (key.startsWith("spark.app.") || key.contains("classpath") || key.contains("library.path")) return false;
        
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