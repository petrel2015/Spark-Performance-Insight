package com.fluffyeti.spark.performance.insight.benchmark;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;

/**
 * JUnit wrapper to trigger JMH benchmarks.
 */
class JmhBenchmarkRunnerTest {

    @Test
    @Disabled("JMH benchmarks are for manual execution only due to DuckDB native library conflicts in concurrent testing")
    void runBenchmarks() throws Exception {
        // Ensure output directory exists
        new File("target/jmh").mkdirs();

        Options opt = new OptionsBuilder()
                .include(PipelineBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result("target/jmh/results.json")
                .shouldDoGC(true)
                .build();

        new Runner(opt).run();
    }
}
