package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gold_storage_rdds")
public class GoldStorageRddModel {
    @TableId
    private String id; // appId:rddId
    private String appId;
    private Integer rddId;
    private String name;
    private String storageLevel;
    private Integer numPartitions;
    private Integer numCached_partitions;
    private Long memorySize;
    private Long diskSize;
}
