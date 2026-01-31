package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.ApplicationMapper;
import com.spark.insight.model.ApplicationModel;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService extends ServiceImpl<ApplicationMapper, ApplicationModel> {
}
