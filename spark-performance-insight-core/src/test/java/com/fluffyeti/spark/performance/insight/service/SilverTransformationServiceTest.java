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

    @Mock
    private PipelineProgressService progressService;

    @Mock
    private com.fluffyeti.spark.performance.insight.config.SystemProperties systemProperties;

    @Mock
    private ApplicationService applicationService;

    private SilverTransformationService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        com.fluffyeti.spark.performance.insight.config.SystemProperties.Transformation trans = new com.fluffyeti.spark.performance.insight.config.SystemProperties.Transformation();
        trans.setStageBatchSize(50);
        when(systemProperties.getTransformation()).thenReturn(trans);
        
        service = new SilverTransformationService(jdbcTemplate, duckDBManager, Runnable::run, progressService, systemProperties, applicationService);

        // Inject self reference for internal AOP-proxied calls
        java.lang.reflect.Field selfField = SilverTransformationService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(service, service);

        // Mock duckDBManager.runWithRetry to execute the runnable immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(duckDBManager).runWithRetry(any(Runnable.class));
        
        // Mock applicationService.executeLocked to execute immediately
        doAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(applicationService).executeLocked(anyString(), any());
    }

    @Test
    void shouldRunFullTransformation() {
        BiConsumer<Double, String> progress = mock(BiConsumer.class);
        service.transform("app-1", progress);

        // Verify terminal progress is reported
        verify(progress, atLeastOnce()).accept(eq(100.0), contains("Completed"));
        
        // Verify core DB operations are triggered
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), eq("app-1"));
    }
}
