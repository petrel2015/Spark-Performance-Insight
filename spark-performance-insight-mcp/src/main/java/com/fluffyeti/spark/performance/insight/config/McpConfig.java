package com.fluffyeti.spark.performance.insight.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluffyeti.spark.performance.insight.mcp.SparkMcpTools;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "insight.mcp", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class McpConfig {

    @Bean
    public HttpServletSseServerTransportProvider mcpTransport() {
        return HttpServletSseServerTransportProvider.builder()
                .baseUrl("http://localhost:18082/mcp")
                .sseEndpoint("/sse")
                .messageEndpoint("/messages")
                .build();
    }

    @Bean
    public McpSyncServer mcpServer(HttpServletSseServerTransportProvider transport, SparkMcpTools tools, ObjectMapper objectMapper) {
        log.info("Configuring MCP Sync Server with HTTP/SSE Transport...");
        
        McpSchema.JsonSchema submitSchema = new McpSchema.JsonSchema(
            "object",
            Map.of("path", Map.of("type", "string", "description", "Full path to a Spark EventLog file or directory")),
            List.of("path"),
            false,
            null,
            null
        );

        McpSchema.JsonSchema statusSchema = new McpSchema.JsonSchema(
            "object",
            Map.of("appId", Map.of("type", "string", "description", "The application ID returned by the submission tool")),
            List.of("appId"),
            false,
            null,
            null
        );

        return McpServer.sync(transport)
                .serverInfo("spark-performance-insight", "1.1.0")
                .tool(
                    new McpSchema.Tool("submit_spark_analysis", "Spark Insight Submission Tool", "Submit a Spark EventLog (file or directory) for detailed performance analysis.", submitSchema, null, null, null),
                    (exchange, params) -> {
                        String path = (String) params.get("path");
                        log.info("MCP Tool submit_spark_analysis called with path: {}", path);
                        try {
                            SparkMcpTools.SubmissionResponse response = tools.submit_spark_analysis(path);
                            String json = objectMapper.writeValueAsString(response);
                            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), false);
                        } catch (Exception e) {
                            log.error("Error in submit_spark_analysis: {}", e.getMessage(), e);
                            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    new McpSchema.Tool("get_analysis_status", "Spark Insight Status Tool", "Check the parsing progress or retrieve the performance diagnosis once the analysis is completed.", statusSchema, null, null, null),
                    (exchange, params) -> {
                        String appId = (String) params.get("appId");
                        log.info("MCP Tool get_analysis_status called with appId: {}", appId);
                        try {
                            SparkMcpTools.StatusResponse response = tools.get_analysis_status(appId);
                            String json = objectMapper.writeValueAsString(response);
                            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), false);
                        } catch (Exception e) {
                            log.error("Error in get_analysis_status: {}", e.getMessage(), e);
                            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpServlet(HttpServletSseServerTransportProvider transport) {
        log.info("Registering MCP Servlet at /mcp/*");
        ServletRegistrationBean<HttpServletSseServerTransportProvider> registration = new ServletRegistrationBean<>(transport, "/mcp/*");
        registration.setAsyncSupported(true);
        return registration;
    }
}
