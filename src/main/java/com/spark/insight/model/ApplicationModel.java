package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("applications")
public class ApplicationModel {
    @TableId
    private String appId;
    private String appName;
    private String userName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private String sparkVersion;
    private String status;
}
