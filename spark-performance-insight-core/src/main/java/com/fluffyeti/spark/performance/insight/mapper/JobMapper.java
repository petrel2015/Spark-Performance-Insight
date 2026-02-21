package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper extends BaseMapper<GoldJobModel> {
    void calculateJobMetrics(@Param("appId") String appId);
}
