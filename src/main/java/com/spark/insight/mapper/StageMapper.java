package com.spark.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spark.insight.model.StageModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StageMapper extends BaseMapper<StageModel> {
    
    void updateStageMetrics(@Param("appId") String appId);

    /**
     * 计算并插入 Stage 的详细统计指标 (Min, P25, P50, P75, P95, Max)
     */
    @Update("INSERT OR REPLACE INTO stage_statistics (id, app_id, stage_id, attempt_id, metric_name, min_value, p25, p50, p75, p95, max_value) " +
            "SELECT " +
            "  t.app_id || ':' || t.stage_id || ':' || s.attempt_id || ':' || 'duration' as id, " +
            "  t.app_id, t.stage_id, s.attempt_id, 'duration' as metric_name, " +
            "  min(t.duration), quantile_cont(t.duration, 0.25), quantile_cont(t.duration, 0.5), quantile_cont(t.duration, 0.75), quantile_cont(t.duration, 0.95), max(t.duration) " +
            "FROM tasks t JOIN stages s ON t.app_id = s.app_id AND t.stage_id = s.stage_id " +
            "WHERE t.app_id = #{appId} GROUP BY t.app_id, t.stage_id, s.attempt_id " +
            "UNION ALL " +
            "SELECT " +
            "  t.app_id || ':' || t.stage_id || ':' || s.attempt_id || ':' || 'gc_time' as id, " +
            "  t.app_id, t.stage_id, s.attempt_id, 'gc_time' as metric_name, " +
            "  min(t.gc_time), quantile_cont(t.gc_time, 0.25), quantile_cont(t.gc_time, 0.5), quantile_cont(t.gc_time, 0.75), quantile_cont(t.gc_time, 0.95), max(t.gc_time) " +
            "FROM tasks t JOIN stages s ON t.app_id = s.app_id AND t.stage_id = s.stage_id " +
            "WHERE t.app_id = #{appId} GROUP BY t.app_id, t.stage_id, s.attempt_id " +
            "UNION ALL " +
            "SELECT " +
            "  t.app_id || ':' || t.stage_id || ':' || s.attempt_id || ':' || 'input_bytes' as id, " +
            "  t.app_id, t.stage_id, s.attempt_id, 'input_bytes' as metric_name, " +
            "  min(t.input_bytes), quantile_cont(t.input_bytes, 0.25), quantile_cont(t.input_bytes, 0.5), quantile_cont(t.input_bytes, 0.75), quantile_cont(t.input_bytes, 0.95), max(t.input_bytes) " +
            "FROM tasks t JOIN stages s ON t.app_id = s.app_id AND t.stage_id = s.stage_id " +
            "WHERE t.app_id = #{appId} GROUP BY t.app_id, t.stage_id, s.attempt_id " +
            "UNION ALL " +
            "SELECT " +
            "  t.app_id || ':' || t.stage_id || ':' || s.attempt_id || ':' || 'shuffle_read_bytes' as id, " +
            "  t.app_id, t.stage_id, s.attempt_id, 'shuffle_read_bytes' as metric_name, " +
            "  min(t.shuffle_read_bytes), quantile_cont(t.shuffle_read_bytes, 0.25), quantile_cont(t.shuffle_read_bytes, 0.5), quantile_cont(t.shuffle_read_bytes, 0.75), quantile_cont(t.shuffle_read_bytes, 0.95), max(t.shuffle_read_bytes) " +
            "FROM tasks t JOIN stages s ON t.app_id = s.app_id AND t.stage_id = s.stage_id " +
            "WHERE t.app_id = #{appId} GROUP BY t.app_id, t.stage_id, s.attempt_id")
    void calculateAndInsertTaskStats(@Param("appId") String appId);
}
