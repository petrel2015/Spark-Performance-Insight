package com.spark.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastStatus(String appId, String status, Double progressValue, String progressText, String appName, java.time.LocalDateTime startTime) {
        StatusMessage msg = new StatusMessage(appId, status, progressValue, progressText, appName, startTime);
        messagingTemplate.convertAndSend("/topic/status", msg);
    }

    public record StatusMessage(String appId, String status, Double progressValue, String progressText, String appName, java.time.LocalDateTime startTime) {}
}
