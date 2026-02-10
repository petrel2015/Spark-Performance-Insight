package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("parsed_event_logs")
public class ParsedEventLogModel {
    @TableId
    private String fileName;
    private LocalDateTime lastModified;
    private Long fileSize;
    private String fileHash;
    private LocalDateTime parsedAt;
    private EventLogStatus status;
}