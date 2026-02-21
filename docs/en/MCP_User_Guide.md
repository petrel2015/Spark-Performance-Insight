# Spark Performance Insight MCP User Guide

This module allows LLMs (e.g., Claude, Gemini) to directly invoke local tools to parse and analyze Spark EventLogs.

## 1. Gemini CLI Configuration
If you are using `@google/gemini-cli`, you can add the MCP server using the following command:

```bash
gemini mcp add --transport sse spark-insight http://localhost:18082/mcp/sse
```

![Gemini CLI Setup Success](../img/mcp_gemini_setup1.png)

*(Note: Screenshot showing successful service addition in Gemini CLI)*

![Gemini Autonomous Analysis Example](../img/mcp_gemini_tool_call.png)

*(Note: Example showing Gemini autonomously calling Spark analysis tools and providing optimization advice)*

---

## 2. Claude Code (CLI) Configuration
`claude-code` is the official command-line tool from Anthropic.

Run the following command to add the server:
```bash
claude mcp add --transport sse spark-insight http://localhost:18082/mcp/sse
```

![Claude Code Setup Success 1](../img/mcp_claude_code_setup1.png)
![Claude Code Setup Success 2](../img/mcp_claude_code_setup2.png)

*(Note: Screenshots showing successful service addition and internal confirmation in Claude Code)*

---

## 3. Recommended Interaction Flow
Once configured, try the following prompt:

> **User**: "Analyze this Spark log for performance bottlenecks: `/data/logs/application_123.zstd`"
>
> **AI (Autonomous Actions)**:
> 1. Invokes `submit_spark_analysis(path="/data/logs/application_123.zstd")` -> gets `appId`.
> 2. Polls `get_analysis_status(appId="...")` until progress is 100%.
> 3. AI retrieves the structured `insight` JSON and provides tuning advice directly in the chat.

![MCP Autonomous Analysis Example](../img/mcp_tool_call_result.png)
*(Note: Example showing an AI agent autonomously calling the Spark analysis tools and providing feedback)*

## ⚠️ Important Notes
*   **Java Version**: Ensure your default `java` command is Java 21. If not, use the full path to your Java 21 binary in the `command` field.
*   **Permissions**: Ensure the AI process has read access to the EventLog file paths you provide.
