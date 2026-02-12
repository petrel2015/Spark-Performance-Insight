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
     * @param appId   预推断的 App ID (可为 null)
     */
    void parse(File logFile, String appId) throws InterruptedException;

    /**
     * 并行解析属于同一个 App 的多个 EventLog 文件
     */
    default void parseFiles(java.util.List<java.io.File> logFiles, String appId) throws InterruptedException {
        for (java.io.File file : logFiles) {
            parse(file, appId);
        }
    }

    /**
     * 是否支持该版本的日志解析
     */
    boolean supports(String version);
}
