# MCP Integration Design: Empowering LLMs with Spark Expertise

## 🎯 Vision
Expose Spark Performance Insight as a **Model Context Protocol (MCP)** service. This allows LLMs (and AI Agents) to autonomously trigger log analysis by providing a file path, enabling seamless "Conversation-to-Tuning" workflows.

## 🛠 Tech Stack
- **Core Framework**: [Spring AI MCP](https://github.com/spring-projects/spring-ai) (Boot Starters)
- **Protocol**: `stdio` (Standard input/output, ideal for local IDEs and desktop AI agents)
- **Module**: A new dedicated Maven module `spark-performance-insight-mcp`.

## 🧱 Architectural Design

### 1. Modular Decoupling
To ensure reusability, we will move core logic into a shared base:
- `spark-performance-insight-core`: Contains Medallion pipelines, DuckDB management, and the rule engine.
- `spark-performance-insight-ui`: The existing web interface.
- `spark-performance-insight-mcp`: **(NEW)** Wraps core logic with Spring AI MCP tool definitions.

### 2. Core Tool Definition

#### `analyze_spark_application`
- **Arguments**:
  - `path`: (string, Required) Full path to a Spark EventLog file or a V2 log directory.
- **Workflow**:
  1. **Detection**: Identify if the path is a single file (ZSTD/Plain) or a V2 directory.
  2. **Ingestion**: Trigger `BronzeIngestionService` to load data into memory DuckDB.
  3. **Transformation**: Execute Silver and Gold layer processing.
  4. **Extraction**: Call `DiagnosisService` to generate a **structured JSON insight**.
- **Output**: Clean, machine-readable JSON for LLM reasoning.

## 📊 LLM-Friendly Output Schema
The response will be stripped of UI elements, focusing on actionable metrics:

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
      "details": { "skew_ratio": 50.0 }
    }
  ],
  "efficiency_metrics": {
    "cpu_utilization": 0.45,
    "gc_time_ratio": 0.12
  }
}
```

## 🚀 Roadmap
1. **Refactor `DiagnosisService`**: Extract a `generateStructuredInsight(appId)` method returning a DTO.
2. **Bootstrap MCP Module**: Create the module and add `spring-ai-mcp-starter`.
3. **Register Tool**: Use the `@Tool` annotation to wrap the parsing pipeline.
4. **Stdio Configuration**: Configure the Spring Boot runner for standard stream interaction.

---
*With this integration, the LLM transforms from a chatbot into a powerful Spark Tuning Commander with direct access to local performance data.*
