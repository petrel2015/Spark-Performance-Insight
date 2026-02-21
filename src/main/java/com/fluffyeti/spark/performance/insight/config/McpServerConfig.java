package com.fluffyeti.spark.performance.insight.config;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "insight.mcp.enabled", havingValue = "true")
public class McpServerConfig {

    @Bean
    public McpSchema.ServerCapabilities mcpServerCapabilities() {
        return McpSchema.ServerCapabilities.builder()
                .tools(true) // Enable tools support
                .build();
    }

    @Bean
    public McpSchema.Implementation mcpServerImplementation() {
        return new McpSchema.Implementation("Spark Performance Insight MCP Server", "1.1.0");
    }
}
