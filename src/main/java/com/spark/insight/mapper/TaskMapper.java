package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.TaskModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskMapper extends BaseMapper<TaskModel> {

    @Select("SELECT count(DISTINCT executor_id) FROM tasks WHERE app_id = #{appId} AND stage_id = #{stageId}")
    long getExecutorCountForStage(@Param("appId") String appId, @Param("stageId") int stageId);

    @Select("SELECT count(DISTINCT t.executor_id) FROM tasks t JOIN stages s ON t.app_id = s.app_id AND t.stage_id = s.stage_id WHERE t.app_id = #{appId} AND s.job_id = #{jobId}")
    long getExecutorCountForJob(@Param("appId") String appId, @Param("jobId") int jobId);
}
