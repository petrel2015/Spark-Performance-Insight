package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.GoldStageStatisticsModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StageStatisticsMapper extends BaseMapper<GoldStageStatisticsModel> {

    @Select("SELECT * FROM gold_stage_statistics WHERE app_id = #{appId} AND stage_id = #{stageId} AND attempt_id = #{attemptId}")
    List<GoldStageStatisticsModel> selectByStage(String appId, Integer stageId, Integer attemptId);
}
