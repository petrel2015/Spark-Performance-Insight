package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.GoldStorageBlockModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StorageBlockMapper extends BaseMapper<GoldStorageBlockModel> {
}
