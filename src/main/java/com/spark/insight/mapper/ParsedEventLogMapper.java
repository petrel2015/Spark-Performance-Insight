package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.ParsedEventLogModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParsedEventLogMapper extends BaseMapper<ParsedEventLogModel> {
}
