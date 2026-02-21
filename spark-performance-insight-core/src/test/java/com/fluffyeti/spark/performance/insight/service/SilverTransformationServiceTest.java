package com.fluffyeti.spark.performance.insight.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SilverTransformationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DuckDBManagerService duckDBManager;

    private SilverTransformationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SilverTransformationService(jdbcTemplate, duckDBManager);
        
        // Mock duckDBManager.runWithRetry to execute the runnable immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(duckDBManager).runWithRetry(any(Runnable.class));
    }

    @Test
    void shouldRunFullTransformation() {
        BiConsumer<Double, String> progress = mock(BiConsumer.class);
        service.transform("app-1", progress);
        
        verify(progress, atLeastOnce()).accept(eq(100.0), contains("Completed"));
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), eq("app-1"), eq("app-1"));
    }
}
