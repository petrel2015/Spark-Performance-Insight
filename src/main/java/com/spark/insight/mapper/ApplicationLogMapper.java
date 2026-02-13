package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.ApplicationLogModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationLogMapper extends BaseMapper<ApplicationLogModel> {
}
