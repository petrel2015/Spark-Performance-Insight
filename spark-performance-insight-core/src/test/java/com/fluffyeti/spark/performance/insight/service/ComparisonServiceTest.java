package com.fluffyeti.spark.performance.insight.service;

import com.fluffyeti.spark.performance.insight.config.SystemProperties;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import com.fluffyeti.spark.performance.insight.model.GoldEnvironmentConfigModel;
import com.fluffyeti.spark.performance.insight.model.GoldStageModel;
import com.fluffyeti.spark.performance.insight.model.dto.ComparisonResult;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComparisonServiceTest {

    @Mock
    private ApplicationService applicationService;
    @Mock
    private StageService stageService;
    @Mock
    private JobService jobService;
    @Mock
    private EnvironmentConfigService envService;
    @Mock
    private TaskService taskService;

    private ComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SystemProperties props = new SystemProperties();
        comparisonService = new ComparisonService(applicationService, stageService, jobService, envService, taskService, props);
    }

    @Test
    @DisplayName("Should compare two applications correctly")
    @SuppressWarnings("unchecked")
    void shouldCompareApplications() {
        String appId1 = "app-1";
        String appId2 = "app-2";

        GoldApplicationModel app1 = new GoldApplicationModel();
        app1.setAppId(appId1);
        app1.setAppName("App 1");
        app1.setDuration(1000L);

        GoldApplicationModel app2 = new GoldApplicationModel();
        app2.setAppId(appId2);
        app2.setAppName("App 2");
        app2.setDuration(1500L);

        when(applicationService.getById(appId1)).thenReturn(app1);
        when(applicationService.getById(appId2)).thenReturn(app2);
        
        LambdaQueryChainWrapper<GoldEnvironmentConfigModel> envQuery = mock(LambdaQueryChainWrapper.class);
        when(envService.lambdaQuery()).thenReturn(envQuery);
        when(envQuery.eq(any(), any())).thenReturn(envQuery);
        when(envQuery.list()).thenReturn(Collections.emptyList());

        ComparisonResult result = comparisonService.compare("app", appId1, appId1, appId2, appId2);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("APPLICATION");
        assertThat(result.getConclusionType()).isEqualTo("REGRESSED");
    }

    @Test
    @DisplayName("Should compare two stages correctly")
    @SuppressWarnings("unchecked")
    void shouldCompareStages() {
        String appId1 = "app-1";
        String appId2 = "app-2";
        long stageId1 = 1L;
        long stageId2 = 1L;

        GoldStageModel stage1 = new GoldStageModel();
        stage1.setDuration(100L);
        stage1.setGcTimeSum(10L);
        stage1.setDiskBytesSpilledSum(0L);
        stage1.setMemoryBytesSpilledSum(0L);
        stage1.setInputBytes(1024L);
        stage1.setShuffleReadBytes(0L);
        stage1.setShuffleWriteBytes(0L);
        stage1.setNumTasks(10L);

        GoldStageModel stage2 = new GoldStageModel();
        stage2.setDuration(50L);
        stage2.setGcTimeSum(5L);
        stage2.setDiskBytesSpilledSum(0L);
        stage2.setMemoryBytesSpilledSum(0L);
        stage2.setInputBytes(1024L);
        stage2.setShuffleReadBytes(0L);
        stage2.setShuffleWriteBytes(0L);
        stage2.setNumTasks(10L);

        when(stageService.getStage(appId1, stageId1, 0L)).thenReturn(stage1);
        when(stageService.getStage(appId2, stageId2, 0L)).thenReturn(stage2);
        when(taskService.getExecutorCountForStage(anyString(), anyLong())).thenReturn(2L);
        
        LambdaQueryChainWrapper<GoldEnvironmentConfigModel> envQuery = mock(LambdaQueryChainWrapper.class);
        when(envService.lambdaQuery()).thenReturn(envQuery);
        when(envQuery.eq(any(), any())).thenReturn(envQuery);
        when(envQuery.list()).thenReturn(Collections.emptyList());

        ComparisonResult result = comparisonService.compare("stage", appId1, "1", appId2, "1");

        assertThat(result).isNotNull();
        assertThat(result.getConclusionType()).isEqualTo("IMPROVED");
        
        verify(stageService).getStage(appId1, 1L, 0L);
        verify(stageService).getStage(appId2, 1L, 0L);
    }
}
