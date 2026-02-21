package com.fluffyeti.spark.performance.insight.benchmark;

import com.fluffyeti.spark.performance.insight.SparkPerformanceInsightApplication;
import com.fluffyeti.spark.performance.insight.service.BronzeIngestionService;
import com.fluffyeti.spark.performance.insight.service.GoldAggregationService;
import com.fluffyeti.spark.performance.insight.service.SilverTransformationService;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Medallion Pipeline Layers.
 * Run via: mvn test-compile-bash (or similar command to run the generated benchmark jar)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 2, time = 10)
public class PipelineBenchmark {

    private ConfigurableApplicationContext context;
    private BronzeIngestionService bronzeService;
    private SilverTransformationService silverService;
    private GoldAggregationService goldService;

    private static final String APP_ID = "application_1771341615593_0001";
    private List<File> logFiles;

    @Setup
    public void setup() {
        context = SpringApplication.run(SparkPerformanceInsightApplication.class, "--spring.profiles.active=test");
        bronzeService = context.getBean(BronzeIngestionService.class);
        silverService = context.getBean(SilverTransformationService.class);
        goldService = context.getBean(GoldAggregationService.class);

        // Prepare files for Bronze
        logFiles = List.of(
            new File("workspace/eventlog/eventlog_v2_application_1771341615593_0001/events_1_application_1771341615593_0001.zstd")
        );
    }

    @TearDown
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public void testBronzeLayer() {
        bronzeService.ingest(APP_ID, logFiles, (p, m) -> {});
    }

    @Benchmark
    public void testSilverLayer() {
        silverService.transform(APP_ID, (p, m) -> {});
    }

    @Benchmark
    public void testGoldLayer() {
        goldService.aggregate(APP_ID, (p, m) -> {});
    }
}
