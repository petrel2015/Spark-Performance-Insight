package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.StorageBlockMapper;
import com.spark.insight.mapper.StorageRddMapper;
import com.spark.insight.model.StorageBlockModel;
import com.spark.insight.model.StorageRddModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final StorageRddMapper rddMapper;
    private final StorageBlockMapper blockMapper;

    public List<StorageRddModel> getRdds(String appId) {
        return rddMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StorageRddModel>()
                .eq(StorageRddModel::getAppId, appId)
                .orderByAsc(StorageRddModel::getRddId));
    }

    public List<StorageBlockModel> getRddBlocks(String appId, Integer rddId) {
        return blockMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StorageBlockModel>()
                .eq(StorageBlockModel::getAppId, appId)
                .eq(StorageBlockModel::getRddId, rddId));
    }

    @Transactional
    public void saveRdd(StorageRddModel rdd) {
        rddMapper.insert(rdd);
    }

    @Transactional
    public void updateBlock(StorageBlockModel block) {
        // 使用 INSERT OR REPLACE 逻辑或手动判断
        blockMapper.insert(block);
    }
}
