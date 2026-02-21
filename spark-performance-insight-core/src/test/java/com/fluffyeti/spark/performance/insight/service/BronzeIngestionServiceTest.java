package com.fluffyeti.spark.performance.insight.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BronzeIngestionServiceTest {

    private final BronzeIngestionService service = new BronzeIngestionService(null, null, null);

    @Test
    @DisplayName("Should sort files in natural order (v2 logs support)")
    void shouldSortFilesNaturally() {
        List<String> files = new ArrayList<>(List.of(
            "events_10_app-1",
            "events_1_app-1",
            "events_2_app-1",
            "appstatus_app-1"
        ));

        // Use reflection to access the private method for sorting or use it via public ingest
        // Since naturalOrderCompare is private, we can test it indirectly or via Reflection
        files.sort((s1, s2) -> (Integer) ReflectionTestUtils.invokeMethod(service, "naturalOrderCompare", s1, s2));

        assertThat(files).containsExactly(
            "appstatus_app-1",
            "events_1_app-1",
            "events_2_app-1",
            "events_10_app-1"
        );
    }
}
