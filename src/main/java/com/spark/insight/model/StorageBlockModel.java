package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("storage_blocks")
public class StorageBlockModel {
    @TableId
    private String id; // appId:rddId:blockName
    private String appId;
    private Integer rddId;
    private String blockName;
    private String storageLevel;
    private Long memorySize;
    private Long diskSize;
    private String executorId;
    private String host;
}
