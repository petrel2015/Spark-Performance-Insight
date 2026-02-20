package com.fluffyeti.spark.performance.insight.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormatUtilsTest {

    @ParameterizedTest
    @CsvSource({
        "500, 500ms",
        "1000, 1s",
        "61000, 1m 1s",
        "3661000, 1h 1m 1s",
        "3600000, 1h 0m 0s"
    })
    @DisplayName("Should format duration correctly")
    void shouldFormatDuration(long ms, String expected) {
        assertThat(FormatUtils.formatDuration(ms)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should format bytes correctly")
    void shouldFormatBytes() {
        assertThat(FormatUtils.formatBytes(512)).isEqualTo("512 B");
        assertThat(FormatUtils.formatBytes(1024)).isEqualTo("1.0 KiB");
        assertThat(FormatUtils.formatBytes(1536)).isEqualTo("1.5 KiB");
        assertThat(FormatUtils.formatBytes(1048576)).isEqualTo("1.0 MiB");
        assertThat(FormatUtils.formatBytes(1073741824L)).isEqualTo("1.0 GiB");
    }
}
