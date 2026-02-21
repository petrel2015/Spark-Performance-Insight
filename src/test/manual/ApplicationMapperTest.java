package com.fluffyeti.spark.performance.insight.mapper;

import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("IntegrationTest")
class ApplicationMapperTest {

    @Autowired
    private ApplicationMapper applicationMapper;

    @Test
    @DisplayName("Should verify ApplicationMapper SQL compatibility with Schema")
    void shouldSelectFromApplicationTable() {
        GoldApplicationModel app = new GoldApplicationModel();
        app.setAppId("test-app-1");
        app.setAppName("Test App");
        
        applicationMapper.insert(app);
        
        GoldApplicationModel retrieved = applicationMapper.selectById("test-app-1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getAppName()).isEqualTo("Test App");
    }

    @Test
    @DisplayName("Should verify complex update metrics SQL")
    void shouldExecuteUpdateAppMetrics() {
        applicationMapper.updateAppMetrics("non-existent-app");
    }
}
