package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_storage_rdds")
public class GoldStorageRddModel {
    @TableId
    private String id;
    private String appId;
    private Long rddId;
    private String name;
    private String storageLevel;
    private Long numPartitions;
    private Long numCachedPartitions;
    private Long memorySize;
    private Long diskSize;
}
