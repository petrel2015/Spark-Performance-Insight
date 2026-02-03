package com.spark.insight;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.spark.insight.mapper")
public class SparkPerformanceInsightApplication {
    public static void main(String[] args) {
        SpringApplication.run(SparkPerformanceInsightApplication.class, args);
    }
}
