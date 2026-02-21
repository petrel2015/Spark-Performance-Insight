# Spark Performance Insight MCP 使用指南

本模块允许 LLM (如 Claude, Gemini) 直接调用本地工具解析并分析 Spark EventLog。

## 1. Claude Desktop 配置
Claude Desktop 是目前 MCP 支持最完善的桌面客户端。

1.  打开 Claude Desktop 的配置文件：
    *   **macOS**: `~/Library/Application\ Support/Claude/claude_desktop_config.json`
    *   **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
2.  在 `mcpServers` 节点下添加以下配置（请替换其中的 JAR 包绝对路径）：

```json
{
  "mcpServers": {
    "spark-insight": {
      "command": "java",
      "args": [
        "-jar",
        "/你的项目绝对路径/spark-performance-insight-mcp/target/spark-performance-insight-mcp-1.1.0.jar"
      ]
    }
  }
}
```
3.  重启 Claude Desktop，你应该能在对话框右下角看到一个 🔨 按钮，点击可见 `submit_spark_analysis` 工具。

---

## 2. Gemini CLI 配置
如果你正在使用 `@google/gemini-cli`，可以通过配置文件或命令行参数挂载 MCP 服务。

1.  打开 `~/.gemini-cli/config.json` (或项目根目录下的配置文件)。
2.  添加 `mcpServers` 配置：

```json
{
  "mcpServers": [
    {
      "name": "spark-insight",
      "command": "java",
      "args": [
        "-jar",
        "./spark-performance-insight-mcp/target/spark-performance-insight-mcp-1.1.0.jar"
      ]
    }
  ]
}
```
3.  启动时，Gemini 会自动连接该服务并获得 Spark 分析能力。

---

## 3. Claude Code (CLI) 配置
`claude-code` 是 Anthropic 推出的命令行交互工具。

直接运行以下命令即可添加：
```bash
claude mcp add spark-insight --command "java -jar /你的项目绝对路径/spark-performance-insight-mcp/target/spark-performance-insight-mcp-1.1.0.jar"
```

或者手动编辑 `~/.claude/mcp.json`：
```json
{
  "mcpServers": {
    "spark-insight": {
      "command": "java",
      "args": ["-jar", ".../spark-performance-insight-mcp/target/spark-performance-insight-mcp-1.1.0.jar"]
    }
  }
}
```

---

## 4. 推荐对话流程 (User Prompt)
配置完成后，你可以尝试这样与 AI 对话：

> **User**: "帮我分析一下这个 Spark 日志是否存在性能问题：`/data/logs/application_123.zstd`"
>
> **AI (自动操作)**:
> 1. 调用 `submit_spark_analysis(path="/data/logs/application_123.zstd")` -> 获得 `appId`.
> 2. 轮询 `get_analysis_status(appId="...")` 直到进度 100%.
> 3. AI 获取 `insight` JSON 数据，并直接在对话中给出优化建议。

## ⚠️ 注意事项
*   **Java 版本**: 请确保系统默认 `java` 命令是 Java 21。如果不是，请在 `command` 处填写 Java 21 的绝对路径。
*   **权限**: 确保 AI 进程有权读取你提供的 EventLog 路径。
