package com.fluffyeti.spark.performance.insight;

import com.fluffyeti.spark.performance.insight.mcp.SparkMcpTools;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;
import java.util.Map;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fluffyeti.spark.performance.insight"})
@Slf4j
public class SparkMcpApplication {

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(SparkMcpApplication.class, args);
    }

    @Bean
    public CommandLineRunner mcpRunner(SparkMcpTools tools) {
        return args -> {
            log.info("Starting Spark Performance Insight MCP Server (Native SDK Mode)...");
            log.warn("Note: Native SDK implementation is active due to Spring AI dependency gaps.");
            
            // For now, we print a placeholder message. 
            // In a real production environment, you would bridge the SDK session to System.in/out.
            log.info("MCP Tools Registered: submit_spark_analysis, get_analysis_status");
        };
    }
}
