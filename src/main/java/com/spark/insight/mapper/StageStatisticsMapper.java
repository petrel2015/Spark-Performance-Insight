package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.StageStatisticsModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StageStatisticsMapper extends BaseMapper<StageStatisticsModel> {
    
    @Select("SELECT * FROM stage_statistics WHERE app_id = #{appId} AND stage_id = #{stageId} AND attempt_id = #{attemptId}")
    List<StageStatisticsModel> selectByStage(String appId, Integer stageId, Integer attemptId);
}
