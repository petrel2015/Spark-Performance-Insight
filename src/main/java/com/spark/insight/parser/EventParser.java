package com.spark.insight.parser;

import java.io.File;

/**
 * EventLog 解析器接口，预留双轨制实现
 */
public interface EventParser {
    /**
     * 解析指定的 EventLog 文件并入库
     *
     * @param logFile 原始日志文件
     */
    void parse(File logFile);

    /**
     * 解析指定的 EventLog 文件并更新进度
     *
     * @param logFile          原始日志文件
     * @param currentFileIndex 当前文件序号 (1-based)
     * @param totalFiles       总文件数
     */
    default void parse(File logFile, int currentFileIndex, int totalFiles) {
        parse(logFile);
    }

    /**
     * 是否支持该版本的日志解析
     */
    boolean supports(String version);
}
