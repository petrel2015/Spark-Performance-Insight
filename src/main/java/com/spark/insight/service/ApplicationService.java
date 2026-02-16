package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.ApplicationMapper;
import com.spark.insight.model.ApplicationModel;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService extends ServiceImpl<ApplicationMapper, ApplicationModel> {
    public void updateAppMetrics(String appId) {
        baseMapper.updateAppMetrics(appId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusAtomic(String appId, String status, Double progressValue, String progressText) {
        lambdaUpdate()
                .eq(ApplicationModel::getAppId, appId)
                .set(ApplicationModel::getParsingStatus, status)
                .set(ApplicationModel::getParsingProgressValue, progressValue)
                .set(ApplicationModel::getParsingProgress, progressText)
                .update();
    }
}
