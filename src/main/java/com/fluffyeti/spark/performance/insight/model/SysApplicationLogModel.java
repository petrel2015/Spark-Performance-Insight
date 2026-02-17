package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_application_logs")
public class SysApplicationLogModel {
    @TableId
    private String id;
    private String appId;
    private String eventType;
    private String eventName;
    private String details;
    private LocalDateTime createdAt;
}
