<template>
  <div class="app-detail">
    <div class="header-bar">
      <h2>{{ app?.appName }} <small>{{ app?.appId }}</small></h2>
      <div class="tabs">
        <button v-for="tab in tabList" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab = tab">
          {{ tab }}
        </button>
      </div>
    </div>

    <div class="content-area">
      <!-- 1. Diagnosis Tab -->
      <div v-if="activeTab === 'Diagnosis'" class="diagnosis-layout">
        <div class="main-report">
          <div class="markdown-body" v-html="renderedReport"></div>
        </div>
      </div>

      <!-- 2. Jobs Tab -->
      <JobsTab v-if="activeTab === 'Jobs' && app" :app-id="app.appId" @view-job-stages="handleViewJobStages" />

      <!-- 3. Stages Tab -->
      <div v-if="activeTab === 'Stages'" class="stages-view">
        <!-- List View -->
        <div v-if="selectedStageId === null">
          <StageTable v-if="app" :app-id="app.appId" @view-stage-detail="navigateToStage" />
        </div>

        <!-- Detail View -->
        <StageDetailView 
          v-else 
          :app-id="app.appId" 
          :stage-id="selectedStageId" 
          @back="navigateBackToStages" 
        />
      </div>

      <!-- 4. Executors Tab -->
      <ExecutorsTab v-if="activeTab === 'Executors'" :executors="executors" />

      <!-- 5. Environment Tab -->
      <EnvironmentTab v-if="activeTab === 'Environment'" :configs="environment" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getAppStages, getDiagnosisReport, getAppExecutors, getApps, getAppEnvironment } from '../api';
import { marked } from 'marked';
import JobsTab from '../components/job/JobsTab.vue';
import ExecutorsTab from '../components/executor/ExecutorsTab.vue';
import StageTable from '../components/stage/StageTable.vue';
import StageDetailView from '../components/stage/StageDetailView.vue';
import EnvironmentTab from '../components/environment/EnvironmentTab.vue';

const route = useRoute();
const router = useRouter();

const activeTab = ref('Diagnosis');
const selectedStageId = computed(() => {
  return route.params.stageId ? parseInt(route.params.stageId) : null;
});

const tabList = ['Diagnosis', 'Jobs', 'Stages', 'Executors', 'Environment'];

const app = ref(null);
const executors = ref([]);
const environment = ref([]);
const report = ref('');

const renderedReport = computed(() => marked(report.value));

const navigateToStage = (stageId) => {
  router.push(`/app/${app.value.appId}/stage/${stageId}`);
};

const navigateBackToStages = () => {
  router.push(`/app/${app.value.appId}`);
};

const handleViewJobStages = (job) => {
  activeTab.value = 'Stages';
};

onMounted(async () => {
  const appId = route.params.id;
  if (route.params.stageId) {
    activeTab.value = 'Stages';
  }
  
  const appsRes = await getApps();
  app.value = appsRes.data.find(a => a.appId === appId);

  const [reportRes, execRes, envRes] = await Promise.all([
    getDiagnosisReport(appId),
    getAppExecutors(appId),
    getAppEnvironment(appId)
  ]);

  report.value = reportRes.data;
  executors.value = execRes.data;
  environment.value = envRes.data;
});
</script>

<style scoped>
.app-detail { padding: 0; display: flex; flex-direction: column; height: calc(100vh - 60px); }
.header-bar { background: white; padding: 1rem 2rem; border-bottom: 1px solid #ddd; display: flex; justify-content: space-between; align-items: center; }
.header-bar h2 { margin: 0; font-size: 1.2rem; }
.header-bar small { color: #999; font-weight: normal; margin-left: 10px; }

.tabs button { background: none; border: none; padding: 0.8rem 1.5rem; cursor: pointer; font-weight: bold; color: #666; border-bottom: 3px solid transparent; }
.tabs button.active { color: #3498db; border-bottom-color: #3498db; }

.content-area { flex: 1; overflow-y: auto; padding: 1.5rem; background: #f8f9fa; }

.diagnosis-layout { display: flex; gap: 1.5rem; height: 100%; }
.main-report { flex: 1; background: white; border-radius: 8px; padding: 2rem; box-shadow: 0 2px 4px rgba(0,0,0,0.05); overflow-y: auto; }

.markdown-body { line-height: 1.6; }
</style>
