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

    @Mock
    private PipelineProgressService progressService;

    @Mock
    private com.fluffyeti.spark.performance.insight.config.SystemProperties systemProperties;

    private GoldAggregationService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Mock properties for default batch size
        com.fluffyeti.spark.performance.insight.config.SystemProperties.Transformation trans = new com.fluffyeti.spark.performance.insight.config.SystemProperties.Transformation();
        when(systemProperties.getTransformation()).thenReturn(trans);
        
        service = new GoldAggregationService(jdbcTemplate, duckDBManager, stageService, Runnable::run, progressService, systemProperties);
        
        // Inject self reference for internal AOP-proxied calls
        java.lang.reflect.Field selfField = GoldAggregationService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(service, service);
        
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
        // In the new fine-grained mode, calculateStageMetrics is called per stage or app aggregate is called.
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), eq("app-1"));
        }
        }

