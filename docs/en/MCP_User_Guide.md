# Spark Performance Insight MCP User Guide

This module allows LLMs (e.g., Claude, Gemini) to directly invoke local tools to parse and analyze Spark EventLogs.

## 1. Claude Desktop Configuration
Claude Desktop provides the most comprehensive support for MCP servers.

1.  Open your Claude Desktop configuration file:
    *   **macOS**: `~/Library/Application\ Support/Claude/claude_desktop_config.json`
    *   **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
2.  Add the following under the `mcpServers` node (replace with your absolute path to the JAR):

```json
{
  "mcpServers": {
    "spark-insight": {
      "command": "java",
      "args": [
        "-jar",
        "/YOUR_PROJECT_PATH/spark-performance-insight-mcp/target/spark-performance-insight-mcp-1.1.0.jar"
      ]
    }
  }
}
```
3.  Restart Claude Desktop. You should see a 🔨 icon in the chat box, indicating that `submit_spark_analysis` is available.

---

## 2. Gemini CLI Configuration
If you are using `@google/gemini-cli`, you can mount the MCP server via config.

1.  Open `~/.gemini-cli/config.json`.
2.  Add the `mcpServers` configuration:

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

---

## 3. Claude Code (CLI) Configuration
`claude-code` is the official command-line tool from Anthropic.

Run the following command to add the server:
```bash
claude mcp add spark-insight --command "java -jar /YOUR_PROJECT_PATH/spark-performance-insight-mcp/target/spark-performance-insight-mcp-1.1.0.jar"
```

---

## 4. Recommended Interaction Flow
Once configured, try the following prompt:

> **User**: "Analyze this Spark log for performance bottlenecks: `/data/logs/application_123.zstd`"
>
> **AI (Autonomous Actions)**:
> 1. Invokes `submit_spark_analysis(path="/data/logs/application_123.zstd")` -> gets `appId`.
> 2. Polls `get_analysis_status(appId="...")` until progress is 100%.
> 3. AI retrieves the structured `insight` JSON and provides tuning advice directly in the chat.

## ⚠️ Important Notes
*   **Java Version**: Ensure your default `java` command is Java 21. If not, use the full path to your Java 21 binary in the `command` field.
*   **Permissions**: Ensure the AI process has read access to the EventLog file paths you provide.
