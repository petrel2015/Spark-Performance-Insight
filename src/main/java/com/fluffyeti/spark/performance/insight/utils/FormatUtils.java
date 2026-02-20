package com.fluffyeti.spark.performance.insight.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for formatting durations and data sizes.
 */
public final class FormatUtils {

    private FormatUtils() {
        // Utility class
    }

    /**
     * Formats duration in milliseconds to human-readable string (e.g., 1h 2m 3s or 500ms).
     */
    public static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = (ms / (1000 * 60 * 60));

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    /**
     * Formats bytes to human-readable string (e.g., 1.5 GiB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "iB";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
