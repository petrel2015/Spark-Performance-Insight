package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_storage_blocks")
public class GoldStorageBlockModel {
    @TableId
    private String id;
    private String appId;
    private Long rddId;
    private String blockName;
    private String storageLevel;
    private Long memorySize;
    private Long diskSize;
    private String executorId;
    private String host;
}
