package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_log_scans")
public class EventLogScanModel {
    @TableId
    private String id;
    private String appId;
    private String filePaths; // JSON string
    private Long totalSize;
    private String previousStatus;
    private LocalDateTime detectedTime;
}
