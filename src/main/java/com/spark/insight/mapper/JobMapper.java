package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.GoldJobModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper extends BaseMapper<GoldJobModel> {
    void calculateJobMetrics(@Param("appId") String appId);
}
