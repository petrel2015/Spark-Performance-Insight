package com.spark.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.insight.mapper.ApplicationLogMapper;
import com.spark.insight.model.ApplicationLogModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class ApplicationLogService extends ServiceImpl<ApplicationLogMapper, ApplicationLogModel> {

    public void logEvent(String appId, String type, String name, String details) {
        try {
            ApplicationLogModel logEntry = new ApplicationLogModel();
            logEntry.setId(UUID.randomUUID().toString());
            logEntry.setAppId(appId);
            logEntry.setEventType(type);
            logEntry.setEventName(name);
            logEntry.setDetails(details);
            logEntry.setCreatedAt(LocalDateTime.now());
            save(logEntry);
            log.info("AppLog [{}]: {} - {}", appId, name, details);
        } catch (Exception e) {
            log.error("Failed to save application log for {}", appId, e);
        }
    }

    public java.util.List<String> getCompletedStages(String appId) {
        return lambdaQuery()
                .eq(ApplicationLogModel::getAppId, appId)
                .in(ApplicationLogModel::getEventName, "Bronze Finished", "Silver Finished", "Gold Finished")
                .select(ApplicationLogModel::getEventName)
                .list()
                .stream()
                .map(ApplicationLogModel::getEventName)
                .collect(java.util.stream.Collectors.toList());
    }
}
