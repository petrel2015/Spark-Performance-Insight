package com.fluffyeti.spark.performance.insight.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ParsingQueueServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StatusBroadcaster broadcaster;
    @Mock
    private ApplicationService applicationService;
    @Mock
    private EventLogWatcherService eventLogWatcherService;

    private ParsingQueueService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ParsingQueueService(jdbcTemplate, broadcaster, applicationService, eventLogWatcherService);
    }

    @Test
    @DisplayName("Should submit app to queue")
    void shouldSubmitToQueue() {
        String appId = "app-1";
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(appId))).thenReturn("Test App");

        service.submit(appId, "FULL");

        // Verify status update in gold_applications
        verify(applicationService).updateStatusAtomic(eq(appId), eq("QUEUED"), eq(0.0), anyString(), any());

        // Verify DELETE duplicates
        verify(jdbcTemplate).update(contains("DELETE FROM sys_parsing_queue"), eq(appId));
        // Verify UPDATE gold_app
        verify(jdbcTemplate).update(contains("UPDATE gold_applications"), eq(appId));
        // Verify INSERT new job. Params: id, appId, type, status, submit_time
        verify(jdbcTemplate).update(contains("INSERT INTO sys_parsing_queue"), any(), eq(appId), eq("FULL"), anyLong());
        
        verify(broadcaster).broadcastStatus(eq(appId), eq("QUEUED"), anyDouble(), anyString(), eq("Test App"), any());
    }

    @Test
    @DisplayName("Should cancel queued job")
    void shouldCancelJob() {
        String appId = "app-1";
        when(jdbcTemplate.update(anyString(), eq(appId))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(appId))).thenReturn("Test App");

        service.cancel(appId);

        verify(jdbcTemplate).update(contains("DELETE FROM sys_parsing_queue"), eq(appId));
        verify(applicationService).updateStatusAtomic(eq(appId), eq("PENDING_LOAD"), eq(0.0), anyString());
        verify(broadcaster).broadcastStatus(eq(appId), eq("CANCELLED"), anyDouble(), anyString(), eq("Test App"), any());
    }

    @Test
    @DisplayName("Should process queue when jobs are available and no job is running")
    void shouldProcessQueue() {
        // No job running
        when(jdbcTemplate.queryForList(contains("WHERE q.status = 'RUNNING'"), eq(String.class))).thenReturn(Collections.emptyList());
        
        // One job in queue
        when(jdbcTemplate.queryForList(contains("WHERE status = 'QUEUED'")))
                .thenReturn(List.of(Map.of("id", "uuid-1", "app_id", "app-1", "type", "FULL")));
        
        // Mock current job details for the workaround (DELETE+INSERT)
        when(jdbcTemplate.queryForMap(anyString(), eq("uuid-1")))
                .thenReturn(Map.of("submit_time", "2026-01-01 00:00:00"));

        service.processQueue();

        // Verify the DuckDB workaround (DELETE then INSERT with RUNNING status)
        verify(jdbcTemplate).update(eq("DELETE FROM sys_parsing_queue WHERE id = ?"), eq("uuid-1"));
        
        // Match only the status 'RUNNING' in the INSERT query. Now has start_time param at the end.
        verify(jdbcTemplate).update(contains("'RUNNING'"), eq("uuid-1"), eq("app-1"), eq("FULL"), eq("2026-01-01 00:00:00"), anyLong());
        
        verify(eventLogWatcherService).executePipeline(eq("app-1"), eq("FULL"), any());
    }

    @Test
    @DisplayName("Should not process queue when a job is already running")
    void shouldNotProcessWhenBusy() {
        when(jdbcTemplate.queryForList(contains("WHERE q.status = 'RUNNING'"), eq(String.class))).thenReturn(List.of("running-app"));

        service.processQueue();

        verify(jdbcTemplate, never()).queryForList(contains("WHERE status = 'QUEUED'"));
        verify(eventLogWatcherService, never()).executePipeline(anyString(), anyString(), any());
    }
}
