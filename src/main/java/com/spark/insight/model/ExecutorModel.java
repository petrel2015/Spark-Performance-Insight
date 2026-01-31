package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("executors")
public class ExecutorModel {
    @TableId
    private String id;
    private String appId;
    private String executorId;
    private String host;
    private Integer totalCores;
    private Long memory;
    private Boolean isActive = true;
}
