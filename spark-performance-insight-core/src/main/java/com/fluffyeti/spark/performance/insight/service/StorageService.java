package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.StorageRddMapper;
import com.fluffyeti.spark.performance.insight.model.GoldStorageRddModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageService extends ServiceImpl<StorageRddMapper, GoldStorageRddModel> {
}
