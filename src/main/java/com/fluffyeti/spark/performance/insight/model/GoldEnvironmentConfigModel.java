package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_environment_configs")
public class GoldEnvironmentConfigModel {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appId;
    private String paramKey;
    private String paramValue;
    private String category;
}
