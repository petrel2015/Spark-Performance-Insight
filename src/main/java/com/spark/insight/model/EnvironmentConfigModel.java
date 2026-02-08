package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("environment_configs")
public class EnvironmentConfigModel {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appId;
    private String paramKey;
    private String paramValue;
    private String category;
}
