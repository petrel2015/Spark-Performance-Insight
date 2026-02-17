package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.EnvironmentConfigMapper;
import com.spark.insight.model.GoldEnvironmentConfigModel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnvironmentConfigService extends ServiceImpl<EnvironmentConfigMapper, GoldEnvironmentConfigModel> {

    public void upsertBatch(List<GoldEnvironmentConfigModel> configList) {
        if (configList == null || configList.isEmpty()) {
            return;
        }
        baseMapper.upsertBatch(configList);
    }

    @Nullable
    public String getConfigValue(String appId, String key) {
        return lambdaQuery()
                .eq(GoldEnvironmentConfigModel::getAppId, appId)
                .eq(GoldEnvironmentConfigModel::getParamKey, key)
                .oneOpt()
                .map(GoldEnvironmentConfigModel::getParamValue)
                .orElse(null);
    }
}