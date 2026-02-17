package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_executors")
public class GoldExecutorModel {
    @TableId
    private String id;
    private String appId;
    private String executorId;
    private String host;
    private java.time.LocalDateTime addTime;
    private java.time.LocalDateTime removeTime;
    private Integer totalCores;
    private Long memory;
    private Boolean isActive;

    // 扩展指标
    private Integer rddBlocks;
    private Long storageMemory;
    private Long onHeapStorageMemory;
    private Long offHeapStorageMemory;
    private Long peakJvmOnHeap;
    private Long peakJvmOffHeap;
    private Long peakExecutionOnHeap;
    private Long peakExecutionOffHeap;
    private Long peakStorageOnHeap;
    private Long peakStorageOffHeap;
    private Long peakPoolDirect;
    private Long peakPoolMapped;
    private Long diskUsed;
    private String resources;
    private Integer resourceProfileId;
    private Integer activeTasks;
    private Integer failedTasks;
    private Integer completedTasks;
    private Integer totalTasks;
    private Long taskTimeMs;
    private Long gcTimeMs;
    private Long inputBytes;
    private Long shuffleReadBytes;
    private Long shuffleWriteBytes;
    private String execLossReason;
    
    // Gold layer aggregations
    private Double avgTaskDurationMs;
    private Double cpuUtilizationRatio;
    private Long maxPeakMemory;
}
