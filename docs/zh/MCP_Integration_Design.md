# MCP 集成设计方案：让 LLM 具备 Spark 专家级分析能力

## 🎯 设计愿景
将 Spark Performance Insight 暴露为 **MCP (Model Context Protocol)** 服务。用户（或 Agent）只需提供日志路径，LLM 即可通过调用本地工具完成从日志解析到结构化诊断的全流程，实现“对话即调优”。

## 🛠 技术栈
- **核心框架**: [Spring AI MCP](https://github.com/spring-projects/spring-ai) (Boot Starters)
- **协议类型**: `stdio` (标准输入输出，适用于本地 IDE 和桌面 Agent 接入)
- **集成模式**: 作为一个独立的 Maven 模块 `spark-performance-insight-mcp`。

## 🧱 架构设计

### 1. 模块化拆分
为了保持核心逻辑的纯净，我们将现有的解析逻辑下沉，MCP 模块作为轻量级包装层：
- `spark-performance-insight-core`: 包含 Medallion 流水线、DuckDB 管理及规则引擎逻辑。
- `spark-performance-insight-ui`: 现有的 Web 模块。
- `spark-performance-insight-mcp`: **(新)** 引入 Spring AI MCP 依赖，实现工具注册。

### 2. 核心工具定义 (MCP Tools)
为了应对长时间的日志解析，我们采用了“提交 + 轮询”的异步设计模式：

#### `submit_spark_analysis`
- **输入参数**:
  - `path`: (string, Required) Spark EventLog 的文件路径或目录路径。
- **职责**: 验证路径、推断 App ID 并将其提交至异步解析队列。
- **输出**: 包含 `appId` 和初始状态的 JSON，LLM 应记录该 ID 以备后续查询。

#### `get_analysis_status`
- **输入参数**:
  - `appId`: (string, Required) 由提交工具返回的应用 ID。
- **职责**: 查询当前解析进度。
- **智能输出**: 
  - 若解析中：返回进度百分比 (`progress`) 和阶段描述 (`progressText`)。
  - 若已完成：除状态外，额外返回 **LLM-Ready JSON** 格式的性能洞察结果。

## 📊 LLM 友好型数据协议 (Output Schema)
返回结果将剔除 UI 相关的样式，专注于核心指标，便于 LLM 推理：

```json
{
  "app_metadata": {
    "app_id": "application_123",
    "name": "Daily_ETL_Job",
    "total_duration_ms": 3600000,
    "health_score": 72
  },
  "critical_bottlenecks": [
    {
      "type": "DATA_SKEW",
      "severity": "HIGH",
      "stage_id": 15,
      "details": {
        "max_task_duration_ms": 500000,
        "median_task_duration_ms": 10000,
        "skew_ratio": 50.0
      }
    },
    {
      "type": "DISK_SPILL",
      "severity": "MEDIUM",
      "stage_id": 8,
      "details": {
        "spill_size_bytes": 2147483648
      }
    }
  ],
  "efficiency_metrics": {
    "cpu_utilization": 0.45,
    "gc_time_ratio": 0.12,
    "shuffle_io_bytes": 5368709120
  }
}
```

## 🚀 实施步骤
1. **Service 重构**: 在 `DiagnosisService` 中抽取 `generateStructuredInsight(appId)` 方法，返回 DTO 而不是渲染好的 Markdown。
2. **MCP 模块创建**: 新建 Maven Module，引入 `spring-ai-mcp-starter`。
3. **工具注册**: 使用 `@Tool` 注解包装解析流水线。
4. **Stdio 适配**: 配置 Spring Boot 启动类，支持标准流交互。

---
*通过此设计，LLM 不再只是一个聊天窗口，而是一个能够直接操作本地数据、深入 Spark 引擎内部的 AI 指挥官。*
