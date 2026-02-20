package com.fluffyeti.spark.performance.insight.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoldAggregationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DuckDBManagerService duckDBManager;
    @Mock
    private StageService stageService;

    private GoldAggregationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GoldAggregationService(jdbcTemplate, duckDBManager, stageService);
        
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(duckDBManager).runWithRetry(any(Runnable.class));
    }

    @Test
    void shouldRunFullAggregation() {
        BiConsumer<Double, String> progress = mock(BiConsumer.class);
        service.aggregate("app-1", progress);
        
        verify(progress, atLeastOnce()).accept(eq(100.0), contains("Completed"));
        verify(stageService, times(1)).calculateStageMetrics("app-1");
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), eq("app-1"));
    }
}
