package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.SysApplicationLogModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationLogMapper extends BaseMapper<SysApplicationLogModel> {
}
