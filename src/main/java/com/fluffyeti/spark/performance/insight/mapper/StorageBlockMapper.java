package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStorageBlockModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StorageBlockMapper extends BaseMapper<GoldStorageBlockModel> {
}
