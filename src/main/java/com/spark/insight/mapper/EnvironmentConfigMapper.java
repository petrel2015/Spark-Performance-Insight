package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.EnvironmentConfigModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface EnvironmentConfigMapper extends BaseMapper<EnvironmentConfigModel> {
    
    @Update("<script>" +
            "INSERT OR REPLACE INTO environment_configs (id, app_id, param_key, param_value, category) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.appId}, #{item.paramKey}, #{item.paramValue}, #{item.category})" +
            "</foreach>" +
            "</script>")
    void upsertBatch(List<EnvironmentConfigModel> list);
}