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
Due to the long-running nature of Spark log analysis, we use a **Submit-then-Poll** asynchronous pattern:

#### `submit_spark_analysis`
- **Arguments**:
  - `path`: (string, Required) Full path to a Spark EventLog file or a V2 log directory.
- **Responsibility**: Validates the path, infers the App ID, and submits it to the background parsing queue.
- **Output**: JSON containing the `appId` and initial status. The LLM should record this ID for future status checks.

#### `get_analysis_status`
- **Arguments**:
  - `appId`: (string, Required) The application ID returned by the submission tool.
- **Responsibility**: Checks the current parsing progress.
- **Smart Output**: 
  - If processing: Returns `progress` percentage and `progressText`.
  - If completed: Returns the **LLM-Ready JSON** performance insights along with the success status.

## 📊 LLM-Friendly Output Schema
The response focuses on actionable metrics for LLM reasoning:

```json
{
  "app_metadata": {
    "app_id": "application_123",
    "health_score": 72
  },
  "critical_bottlenecks": [
    {
      "type": "DATA_SKEW",
      "severity": "HIGH",
      "stage_id": 15,
      "details": { "skew_ratio": 50.0 }
    }
  ]
}
```

## 🚀 Roadmap
1. **Refactor `DiagnosisService`**: Extract a `generateStructuredInsight(appId)` method returning a DTO.
2. **Bootstrap MCP Module**: Create the module and add `spring-ai-mcp-starter`.
3. **Register Tool**: Use the `@Tool` annotation to wrap the parsing pipeline.
4. **Stdio Configuration**: Configure the Spring Boot runner for standard stream interaction.

---
*With this integration, the LLM transforms from a chatbot into a powerful Spark Tuning Commander with direct access to local performance data.*
