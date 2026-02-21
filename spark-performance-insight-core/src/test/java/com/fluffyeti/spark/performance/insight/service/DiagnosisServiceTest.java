package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.GoldJobModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private DiagnosisService diagnosisService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        diagnosisService = new DiagnosisService(applicationService, stageService, jobService);
    }

    @Test
    @DisplayName("Should return 'Application not found' when app is null")
    void shouldReturnNotFound() {
        when(applicationService.getById(anyString())).thenReturn(null);
        String report = diagnosisService.generateMarkdownReport("app-1");
        assertThat(report).isEqualTo("Application not found.");
    }

    @Test
    @DisplayName("Should generate a basic healthy report")
    @SuppressWarnings("unchecked")
    void shouldGenerateHealthyReport() {
        String appId = "app-1";
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId(appId);
        app.setAppName("Test App");
        app.setDuration(1000L);
        when(applicationService.getById(appId)).thenReturn(app);

        // Mock jobService lambda query
        LambdaQueryChainWrapper<GoldJobModel> jobQuery = mock(LambdaQueryChainWrapper.class);
        when(jobService.lambdaQuery()).thenReturn(jobQuery);
        when(jobQuery.eq(any(), any())).thenReturn(jobQuery);
        when(jobQuery.list()).thenReturn(Collections.emptyList());

        // Mock stageService lambda query
        LambdaQueryChainWrapper<GoldStageModel> stageQuery = mock(LambdaQueryChainWrapper.class);
        when(stageService.lambdaQuery()).thenReturn(stageQuery);
        when(stageQuery.eq(any(), any())).thenReturn(stageQuery);
        when(stageQuery.lt(any(), any())).thenReturn(stageQuery);
        when(stageQuery.orderByAsc(any(SFunction.class))).thenReturn(stageQuery);
        when(stageQuery.last(anyString())).thenReturn(stageQuery);
        when(stageQuery.list()).thenReturn(Collections.emptyList());

        String report = diagnosisService.generateMarkdownReport(appId);
        
        assertThat(report).contains("应用健康概览");
        assertThat(report).contains("健康 (Healthy: 100)");
        assertThat(report).contains("未发现严重性能异常的阶段");
    }

    @Test
    @DisplayName("Should identify skewed stages and GC pressure")
    @SuppressWarnings("unchecked")
    void shouldIdentifyIssues() {
        String appId = "app-1";
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId(appId);
        app.setAppName("Bad App");
        app.setDuration(5000L);
        when(applicationService.getById(appId)).thenReturn(app);

        GoldJobModel job = new GoldJobModel();
        job.setAppId(appId);
        job.setJobId(1);
        job.setPerformanceScore(50.0);
        job.setDuration(4000L);
        
        LambdaQueryChainWrapper<GoldJobModel> jobQuery = mock(LambdaQueryChainWrapper.class);
        when(jobService.lambdaQuery()).thenReturn(jobQuery);
        when(jobQuery.eq(any(), any())).thenReturn(jobQuery);
        when(jobQuery.list()).thenReturn(List.of(job));

        GoldStageModel stage = new GoldStageModel();
        stage.setAppId(appId);
        stage.setStageId(1);
        stage.setStageName("Skewed Stage");
        stage.setPerformanceScore(30.0);
        stage.setIsSkewed(true);
        stage.setDiskBytesSpilledSum(1024L);
        stage.setGcTimeSum(1000L);
        stage.setTasksDurationSum(2000L); // 50% GC time
        stage.setNumTasks(100);
        stage.setDuration(3000L);

        LambdaQueryChainWrapper<GoldStageModel> stageQuery = mock(LambdaQueryChainWrapper.class);
        when(stageService.lambdaQuery()).thenReturn(stageQuery);
        when(stageQuery.eq(any(), any())).thenReturn(stageQuery);
        when(stageQuery.lt(any(), any())).thenReturn(stageQuery);
        when(stageQuery.orderByAsc(any(SFunction.class))).thenReturn(stageQuery);
        when(stageQuery.last(anyString())).thenReturn(stageQuery);
        when(stageQuery.list()).thenReturn(List.of(stage));

        String report = diagnosisService.generateMarkdownReport(appId);

        assertThat(report).contains("一般 (Warning: 50)"); // avg of job scores
        assertThat(report).contains("数据倾斜");
        assertThat(report).contains("磁盘溢写");
        assertThat(report).contains("内存压力");
        assertThat(report).contains("GC 时间占比超过 10%");
    }
}
