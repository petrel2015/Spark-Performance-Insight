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
        SpringApplication.run(SparkMcpApplication.class, args);
    }
}
