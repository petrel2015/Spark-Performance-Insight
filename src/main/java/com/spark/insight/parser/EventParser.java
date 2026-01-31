package com.spark.insight.parser;

import java.io.File;

/**
 * EventLog 解析器接口，预留双轨制实现
 */
public interface EventParser {
    /**
     * 解析指定的 EventLog 文件并入库
     * @param logFile 原始日志文件
     */
    void parse(File logFile);

    /**
     * 是否支持该版本的日志解析
     */
    boolean supports(String version);
}
