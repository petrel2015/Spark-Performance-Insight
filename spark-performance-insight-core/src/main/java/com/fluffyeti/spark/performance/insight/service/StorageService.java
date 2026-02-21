package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.StorageBlockMapper;
import com.fluffyeti.spark.performance.insight.mapper.StorageRddMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStorageBlockModel;
import com.fluffyeti.spark.performance.insight.model.GoldStorageRddModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final StorageRddMapper rddMapper;
    private final StorageBlockMapper blockMapper;

    public List<GoldStorageRddModel> getRdds(String appId) {
        return rddMapper.selectList(new LambdaQueryWrapper<GoldStorageRddModel>()
                .eq(GoldStorageRddModel::getAppId, appId)
                .orderByAsc(GoldStorageRddModel::getRddId));
    }

    public List<GoldStorageBlockModel> getRddBlocks(String appId, Integer rddId) {
        return blockMapper.selectList(new LambdaQueryWrapper<GoldStorageBlockModel>()
                .eq(GoldStorageBlockModel::getAppId, appId)
                .eq(GoldStorageBlockModel::getRddId, rddId));
    }

    @Transactional
    public void saveRdd(GoldStorageRddModel rdd) {
        rddMapper.insert(rdd);
    }

    @Transactional
    public void updateBlock(GoldStorageBlockModel block) {
        // 使用 INSERT OR REPLACE 逻辑或手动判断
        blockMapper.insert(block);
    }
}
