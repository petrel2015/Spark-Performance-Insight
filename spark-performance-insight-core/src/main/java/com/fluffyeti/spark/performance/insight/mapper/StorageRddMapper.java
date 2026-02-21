package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStorageRddModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StorageRddMapper extends BaseMapper<GoldStorageRddModel> {
}
