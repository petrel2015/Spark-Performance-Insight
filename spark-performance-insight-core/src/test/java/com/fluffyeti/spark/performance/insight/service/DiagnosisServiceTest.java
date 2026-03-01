package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import com.fluffyeti.spark.performance.insight.model.dto.ApplicationInsight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisServiceTest {

    @Mock
    private ApplicationService applicationService;
    @Mock
    private StageService stageService;
    @Mock
    private JobService jobService;

    private DiagnosisService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DiagnosisService(applicationService, stageService, jobService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldComputeInsightCorrectly() {
        String appId = "app-1";
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId(appId);
        app.setAppName("Test App");
        app.setDuration(1000L);

        GoldJobModel job = new GoldJobModel();
        job.setPerformanceScore(85.0);
        job.setDuration(1000L);
        job.setJobId(1L);

        when(applicationService.getById(appId)).thenReturn(app);
        
        LambdaQueryChainWrapper<GoldJobModel> jobQuery = mock(LambdaQueryChainWrapper.class);
        when(jobService.lambdaQuery()).thenReturn(jobQuery);
        when(jobQuery.eq(any(), any())).thenReturn(jobQuery);
        when(jobQuery.list()).thenReturn(List.of(job));

        LambdaQueryChainWrapper<GoldStageModel> stageQuery = mock(LambdaQueryChainWrapper.class);
        when(stageService.lambdaQuery()).thenReturn(stageQuery);
        when(stageQuery.eq(any(), any())).thenReturn(stageQuery);
        when(stageQuery.lt(any(), any())).thenReturn(stageQuery);
        // Cast to avoid ambiguous call
        when(stageQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class))).thenReturn(stageQuery);
        when(stageQuery.last(anyString())).thenReturn(stageQuery);
        when(stageQuery.list()).thenReturn(Collections.emptyList());

        ApplicationInsight insight = service.computeApplicationInsight(appId);

        assertNotNull(insight);
        assertEquals(85.0, insight.getAppMetadata().getHealthScore());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDetectDataSkew() {
        String appId = "app-1";
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId(appId);
        app.setDuration(1000L);

        GoldJobModel job = new GoldJobModel();
        job.setPerformanceScore(50.0);
        job.setDuration(1000L);
        job.setJobId(1L);

        GoldStageModel skewedStage = new GoldStageModel();
        skewedStage.setStageId(1L);
        skewedStage.setStageName("Skewed Stage");
        skewedStage.setPerformanceScore(30.0);
        skewedStage.setDuration(500L);
        skewedStage.setIsSkewed(true);

        when(applicationService.getById(appId)).thenReturn(app);
        
        LambdaQueryChainWrapper<GoldJobModel> jobQuery = mock(LambdaQueryChainWrapper.class);
        when(jobService.lambdaQuery()).thenReturn(jobQuery);
        when(jobQuery.eq(any(), any())).thenReturn(jobQuery);
        when(jobQuery.list()).thenReturn(List.of(job));

        LambdaQueryChainWrapper<GoldStageModel> stageQuery = mock(LambdaQueryChainWrapper.class);
        when(stageService.lambdaQuery()).thenReturn(stageQuery);
        when(stageQuery.eq(any(), any())).thenReturn(stageQuery);
        when(stageQuery.lt(any(), any())).thenReturn(stageQuery);
        when(stageQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class))).thenReturn(stageQuery);
        when(stageQuery.last(anyString())).thenReturn(stageQuery);
        when(stageQuery.list()).thenReturn(List.of(skewedStage));

        ApplicationInsight insight = service.computeApplicationInsight(appId);

        assertNotNull(insight);
        assertFalse(insight.getCriticalBottlenecks().isEmpty());
        assertEquals(ApplicationInsight.Bottleneck.Type.DATA_SKEW, insight.getCriticalBottlenecks().get(0).getType());
    }
}
