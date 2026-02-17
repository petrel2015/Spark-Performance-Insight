package com.fluffyeti.spark.performance.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastStatus(String appId, String status, Double progressValue, String progressText, String appName, java.time.LocalDateTime startTime) {
        Long startTimeMillis = (startTime != null) ? startTime.toInstant(ZoneOffset.UTC).toEpochMilli() : null;
        StatusMessage msg = new StatusMessage(appId, status, progressValue, progressText, appName, startTimeMillis);
        messagingTemplate.convertAndSend("/topic/status", msg);
    }

    public record StatusMessage(String appId, String status, Double progressValue, String progressText, String appName, Long startTime) {}
}
