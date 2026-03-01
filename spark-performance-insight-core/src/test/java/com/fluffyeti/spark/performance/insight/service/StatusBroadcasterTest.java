package com.fluffyeti.spark.performance.insight.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class StatusBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StatusBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        broadcaster = new StatusBroadcaster(messagingTemplate);
    }

    @Test
    void shouldBroadcastStatus() {
        long now = System.currentTimeMillis();
        broadcaster.broadcastStatus("app-1", "RUNNING", 50.0, "Testing", "App1", now);
        
        verify(messagingTemplate).convertAndSend(eq("/topic/status"), any(StatusBroadcaster.StatusMessage.class));
    }
}
