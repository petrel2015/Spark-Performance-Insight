package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.StorageBlockMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStorageBlockModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageBlockService extends ServiceImpl<StorageBlockMapper, GoldStorageBlockModel> {
}
