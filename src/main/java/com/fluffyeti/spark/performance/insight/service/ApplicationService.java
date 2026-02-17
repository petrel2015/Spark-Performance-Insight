package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.ApplicationMapper;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService extends ServiceImpl<ApplicationMapper, GoldApplicationModel> {
    public void updateAppMetrics(String appId) {
        baseMapper.updateAppMetrics(appId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusAtomic(String appId, String status, Double progressValue, String progressText, java.time.LocalDateTime startTime) {
        var updateChain = lambdaUpdate()
                .eq(GoldApplicationModel::getAppId, appId)
                .set(GoldApplicationModel::getParsingStatus, status)
                .set(GoldApplicationModel::getParsingProgressValue, progressValue)
                .set(GoldApplicationModel::getParsingProgress, progressText);
        
        if (startTime != null) {
            updateChain.set(GoldApplicationModel::getParsingStartTime, startTime);
        }
        
        updateChain.update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusAtomic(String appId, String status, Double progressValue, String progressText) {
        updateStatusAtomic(appId, status, progressValue, progressText, null);
    }
}
