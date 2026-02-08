package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.EnvironmentConfigMapper;
import com.spark.insight.model.EnvironmentConfigModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnvironmentConfigService extends ServiceImpl<EnvironmentConfigMapper, EnvironmentConfigModel> {

    public void upsertBatch(List<EnvironmentConfigModel> list) {
        if (list == null || list.isEmpty()) return;
        baseMapper.upsertBatch(list);
    }

    public String getConfigValue(String appId, String key) {
        return lambdaQuery()
                .eq(EnvironmentConfigModel::getAppId, appId)
                .eq(EnvironmentConfigModel::getParamKey, key)
                .oneOpt()
                .map(EnvironmentConfigModel::getParamValue)
                .orElse(null);
    }
}
