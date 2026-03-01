package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gold_executors")
public class GoldExecutorModel {
    @TableId
    private String id;
    private String appId;
    private String executorId;
    private String host;
    private LocalDateTime addTime;
    private LocalDateTime removeTime;
    private Long totalCores;
    private Long memory;
    private Boolean isActive;
    private Long rddBlocks;
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
    private Long resourceProfileId;
    private Long activeTasks;
    private Long failedTasks;
    private Long completedTasks;
    private Long totalTasks;
    private Long taskTimeMs;
    private Long gcTimeMs;
    private Long inputBytes;
    private Long shuffleReadBytes;
    private Long shuffleWriteBytes;
    private String execLossReason;
    private Double avgTaskDurationMs;
    private Double cpuUtilizationRatio;
    private Long maxPeakMemory;
}
