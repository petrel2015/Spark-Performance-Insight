package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.SqlExecutionMapper;
import com.spark.insight.model.SqlExecutionModel;
import org.springframework.stereotype.Service;

@Service
public class SqlExecutionService extends ServiceImpl<SqlExecutionMapper, SqlExecutionModel> {
}
