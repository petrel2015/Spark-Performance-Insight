package com.fluffyeti.spark.performance.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluffyeti.spark.performance.insight.model.GoldEnvironmentConfigModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EnvironmentConfigMapper extends BaseMapper<GoldEnvironmentConfigModel> {

    @Update("<script>" +
            "INSERT OR REPLACE INTO environment_configs (id, app_id, param_key, param_value, category) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.appId}, #{item.paramKey}, #{item.paramValue}, #{item.category})" +
            "</foreach>" +
            "</script>")
    void upsertBatch(List<GoldEnvironmentConfigModel> list);
}