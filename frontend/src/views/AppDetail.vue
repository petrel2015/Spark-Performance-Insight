<template>
  <div class="app-detail">
    <div class="header-bar">
      <div class="tabs">
        <router-link v-for="tab in tabList" :key="tab"
                :to="getTabRoute(tab)"
                class="tab-link"
                :class="{ active: activeTab === tab }">
          {{ tab }}
        </router-link>
      </div>
      <div class="app-info" v-if="app">
        <h2>
          {{ app.appName }}
          <span v-if="app.sparkVersion && app.sparkVersion !== 'unknown'" class="spark-version-badge">
            {{ app.sparkVersion }}
          </span>
          <small>{{ app.appId }}</small>
        </h2>
      </div>
    </div>

    <div class="content-area">
      <!-- 1. Diagnosis Tab -->
      <div v-if="activeTab === 'Diagnosis'" class="diagnosis-layout">
        <div v-if="loading.diagnosis" class="loading-placeholder">
          Generating expert diagnosis report...
        </div>
        <div v-else class="folder-card">
          <!-- Sub Tabs -->
          <div class="folder-tabs">
            <div 
              class="folder-tab" 
              :class="{ active: activeDiagSubTab === 'Rule' }"
              @click="activeDiagSubTab = 'Rule'"
            >
              <span class="material-symbols-outlined">rule</span>
              规则引擎诊断报告 (Rule-Based Diagnostic Report)
            </div>
            <div 
              class="folder-tab" 
              :class="{ active: activeDiagSubTab === 'LLM' }"
              @click="activeDiagSubTab = 'LLM'"
            >
              <span class="material-symbols-outlined">auto_awesome</span>
              LLM 深度诊断报告 (LLM Diagnostic Report)
            </div>
          </div>

          <!-- Folder Content -->
          <div class="folder-content">
            <!-- LLM Content -->
            <div v-if="activeDiagSubTab === 'LLM'">
              <!-- Initial State: Show only if no report, not loading, and not generating -->
              <div v-if="!llmReport && !loading.llm && !isGenerating" class="ai-intro">
                <p>利用智谱 GLM-4.7 大语言模型对 Spark 应用进行全方位深度分析，挖掘隐藏的性能瓶颈并提供专家级优化建议。</p>
                <button class="generate-btn" @click="generateAIReport()">
                  <span class="material-symbols-outlined">auto_awesome</span>
                  生成深度诊断报告
                </button>
              </div>
              
              <!-- Loading/Generating State -->
              <div v-if="loading.llm || isGenerating" class="ai-loading">
                <div class="spinner"></div>
                <span>AI 正在思考中，这可能需要 30 秒左右...</span>
                <small class="polling-hint" v-if="isGenerating && elapsedSeconds > 0">
                  已耗时: {{ elapsedSeconds }} 秒
                </small>
              </div>

              <!-- Report Display -->
              <div v-if="llmReport && llmReport !== '[GENERATING]' && llmReport !== 'GENERATING' && !isGenerating && !loading.llm" class="llm-report-wrapper">
                <div class="report-header">
                  <div class="header-left">
                    <span class="report-title">
                      <span class="material-symbols-outlined">psychology</span> 
                      深度分析报告
                    </span>
                    <div class="generation-meta" v-if="generationMeta.startTime">
                      <span class="meta-item">生成时间: {{ formatDateTime(generationMeta.endTime || generationMeta.startTime) }}</span>
                      <span class="meta-item" v-if="generationMeta.duration">耗时: {{ formatTime(generationMeta.duration) }}</span>
                    </div>
                  </div>
                  <button class="regenerate-btn" @click="forceRegenerateAIReport" :disabled="loading.llm">
                    <span class="material-symbols-outlined">refresh</span>
                    重新生成报告
                  </button>
                </div>
                <div class="markdown-body" v-html="renderedLLMReport"></div>
              </div>
            </div>

            <!-- Rule Content -->
            <div v-if="activeDiagSubTab === 'Rule'">
              <div class="markdown-body" v-html="renderedReport"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Other Tabs -->
      <div v-if="activeTab === 'SQL / DataFrame'" class="sql-view">
        <SQLTab v-if="selectedExecutionId === null && app" :app-id="app.appId" @view-sql-detail="navigateToSql" />
        <SQLDetailView v-else-if="app" :app-id="app.appId" :execution-id="selectedExecutionId" @back="navigateBackToSqlList" />
      </div>

      <div v-if="activeTab === 'Jobs'" class="jobs-view">
        <JobsTab v-if="selectedJobId === null && app" :app-id="app.appId" @view-sql-detail="navigateToSql" @view-job-detail="navigateToJob" />
        <JobDetailView v-else-if="app" :app-id="app.appId" :job-id="selectedJobId" @back="navigateBackToJobs" @view-stage="navigateToStage" />
      </div>

      <div v-if="activeTab === 'Stages'" class="stages-view">
        <div v-if="selectedStageId === null">
          <StageTable v-if="app" :app-id="app.appId" @view-stage-detail="navigateToStage" @view-job-detail="navigateToJob"/>
        </div>
        <StageDetailView v-else-if="app" :app-id="app.appId" :stage-id="selectedStageId" :attempt-id="selectedAttemptId" @back="navigateBackToStages" @view-job="handleViewJob" />
      </div>

      <ExecutorsTab v-if="activeTab === 'Executors'" :executors="executors"/>
      <StorageTab v-if="activeTab === 'Storage' && app" :app-id="app.appId" />
      <EnvironmentTab v-if="activeTab === 'Environment'" :configs="environment"/>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, onUnmounted, computed, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {getDiagnosisReport, getAppExecutors, getApp, getAppEnvironment, getLLMReport} from '../api';
import {marked} from 'marked';
import {formatDateTime, formatTime} from '../utils/format';
import JobsTab from '../components/job/JobsTab.vue';
import JobDetailView from '../components/job/JobDetailView.vue';
import SQLTab from '../components/sql/SQLTab.vue';
import SQLDetailView from '../components/sql/SQLDetailView.vue';
import ExecutorsTab from '../components/executor/ExecutorsTab.vue';
import StorageTab from '../components/storage/StorageTab.vue';
import StageTable from '../components/stage/StageTable.vue';
import StageDetailView from '../components/stage/StageDetailView.vue';
import EnvironmentTab from '../components/environment/EnvironmentTab.vue';

const route = useRoute();
const router = useRouter();

const app = ref(null);
const executors = ref([]);
const environment = ref([]);
const report = ref('');
const llmReport = ref('');
const activeTab = ref('Diagnosis');
const activeDiagSubTab = ref('Rule');
const isGenerating = ref(false);
const elapsedSeconds = ref(0);
const generationMeta = ref({
  startTime: null,
  endTime: null,
  duration: null
});

let pollTimer = null;
let elapsedTimer = null;

const loading = ref({
  app: false,
  diagnosis: false,
  llm: false,
  executors: false,
  environment: false
});

const tabList = ['Diagnosis', 'Jobs', 'Stages', 'Executors', 'Storage', 'Environment', 'SQL / DataFrame'];

const getTabRoute = (tab) => {
  const appId = route.params.id;
  if (tab === 'Diagnosis') return `/app/${appId}`;
  if (tab === 'SQL / DataFrame') return `/app/${appId}/sql`;
  if (tab === 'Jobs') return `/app/${appId}/jobs`;
  if (tab === 'Stages') return `/app/${appId}/stages`;
  if (tab === 'Executors') return `/app/${appId}/executors`;
  if (tab === 'Storage') return `/app/${appId}/storage`;
  if (tab === 'Environment') return `/app/${appId}/environment`;
  return `/app/${appId}`;
};

const selectedStageId = computed(() => route.params.stageId ? parseInt(route.params.stageId) : null);
const selectedAttemptId = computed(() => route.query.attemptId ? parseInt(route.query.attemptId) : null);
const selectedJobId = computed(() => route.params.jobId ? parseInt(route.params.jobId) : null);
const selectedExecutionId = computed(() => route.params.executionId ? parseInt(route.params.executionId) : null);

const renderedReport = computed(() => marked(report.value || ''));
const renderedLLMReport = computed(() => marked(llmReport.value || ''));

const startElapsedTimer = (startTime) => {
  if (elapsedTimer) clearInterval(elapsedTimer);
  const start = startTime || Date.now();
  
  // Update immediately
  elapsedSeconds.value = Math.floor((Date.now() - start) / 1000);
  
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value = Math.floor((Date.now() - start) / 1000);
  }, 1000);
};

const stopElapsedTimer = () => {
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
};

const startPolling = (remoteStartTime) => {
  if (pollTimer) return;
  isGenerating.value = true;
  
  // Start visual timer
  startElapsedTimer(remoteStartTime);

  pollTimer = setInterval(async () => {
    try {
      const res = await getApp(route.params.id);
      const appData = res.data;
      if (appData.llmReport && appData.llmReport !== '[GENERATING]' && appData.llmReport !== 'GENERATING') {
        llmReport.value = appData.llmReport;
        
        // Update meta
        generationMeta.value = {
          startTime: appData.llmStartTime,
          endTime: appData.llmEndTime,
          duration: (appData.llmEndTime && appData.llmStartTime) ? (appData.llmEndTime - appData.llmStartTime) : null
        };
        
        stopPolling();
      }
    } catch (e) {
      console.error('Polling failed', e);
    }
  }, 3000);
};

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  stopElapsedTimer();
  isGenerating.value = false;
};

const fetchDataForTab = async (tab) => {
  const appId = route.params.id;
  if (!appId) return;

  if (tab === 'Diagnosis' && !report.value && !loading.value.diagnosis) {
    loading.value.diagnosis = true;
    try {
      const res = await getDiagnosisReport(appId);
      report.value = res.data;
      
      const appRes = await getApp(appId);
      app.value = appRes.data;
      
      // Sync meta info
      generationMeta.value = {
        startTime: app.value.llmStartTime,
        endTime: app.value.llmEndTime,
        duration: (app.value.llmEndTime && app.value.llmStartTime) ? (app.value.llmEndTime - app.value.llmStartTime) : null
      };

      if (app.value.llmReport === '[GENERATING]' || app.value.llmReport === 'GENERATING') {
        startPolling(app.value.llmStartTime);
      } else {
        llmReport.value = app.value.llmReport;
      }
    } finally {
      loading.value.diagnosis = false;
    }
  } else if (tab === 'Executors' && executors.value.length === 0 && !loading.value.executors) {
    loading.value.executors = true;
    try {
      const res = await getAppExecutors(appId);
      executors.value = res.data;
    } finally {
      loading.value.executors = false;
    }
  } else if (tab === 'Environment' && environment.value.length === 0 && !loading.value.environment) {
    loading.value.environment = true;
    try {
      const res = await getAppEnvironment(appId);
      environment.value = res.data;
    } finally {
      loading.value.environment = false;
    }
  }
};

const syncTabWithRoute = () => {
  const path = route.path;
  let newTab = 'Diagnosis';
  if (path.includes('/stage/')) newTab = 'Stages';
  else if (path.includes('/job/')) newTab = 'Jobs';
  else if (path.includes('/sql')) newTab = 'SQL / DataFrame';
  else if (path.endsWith('/jobs')) newTab = 'Jobs';
  else if (path.endsWith('/stages')) newTab = 'Stages';
  else if (path.endsWith('/executors')) newTab = 'Executors';
  else if (path.endsWith('/storage')) newTab = 'Storage';
  else if (path.endsWith('/environment')) newTab = 'Environment';
  
  activeTab.value = newTab;
  fetchDataForTab(newTab);
};

const navigateToTab = (tab) => {
  const appId = route.params.id;
  if (tab === 'Diagnosis') router.push(`/app/${appId}`);
  else if (tab === 'SQL / DataFrame') router.push(`/app/${appId}/sql`);
  else if (tab === 'Jobs') router.push(`/app/${appId}/jobs`);
  else if (tab === 'Stages') router.push(`/app/${appId}/stages`);
  else if (tab === 'Executors') router.push(`/app/${appId}/executors`);
  else if (tab === 'Storage') router.push(`/app/${appId}/storage`);
  else if (tab === 'Environment') router.push(`/app/${appId}/environment`);
};

const navigateToJob = (jobId) => router.push(`/app/${route.params.id}/job/${jobId}`);
const navigateBackToJobs = () => router.push(`/app/${route.params.id}/jobs`);
const navigateToSql = (executionId) => router.push(`/app/${route.params.id}/sql/${executionId}`);
const navigateBackToSqlList = () => router.push(`/app/${route.params.id}/sql`);

const navigateToStage = (payload) => {
  let stageId = typeof payload === 'object' ? payload.stageId : payload;
  let attemptId = typeof payload === 'object' ? payload.attemptId : null;
  if (attemptId !== null) router.push({path: `/app/${route.params.id}/stage/${stageId}`, query: {attemptId}});
  else router.push(`/app/${route.params.id}/stage/${stageId}`);
};

const navigateBackToStages = () => router.push(`/app/${route.params.id}/stages`);
const handleViewJob = (jobId) => navigateToJob(jobId);

const generateAIReport = async (force = false) => {
  if (loading.value.llm || isGenerating.value) return;
  
  loading.value.llm = true;
  isGenerating.value = true;
  
  // Reset meta
  generationMeta.value = { startTime: Date.now(), endTime: null, duration: null };
  startElapsedTimer(Date.now());

  try {
    const res = await getLLMReport(route.params.id, force);
    if (res.data === 'GENERATING') {
      startPolling(Date.now()); // Using local start time as fallback
    } else {
      // Synchronous return
      llmReport.value = res.data;
      // Fetch latest app data to get accurate server-side timestamp
      const appRes = await getApp(route.params.id);
      const appData = appRes.data;
      generationMeta.value = {
        startTime: appData.llmStartTime,
        endTime: appData.llmEndTime,
        duration: (appData.llmEndTime && appData.llmStartTime) ? (appData.llmEndTime - appData.llmStartTime) : null
      };
      stopPolling();
    }
  } catch (err) {
    llmReport.value = '> ⚠️ Failed to generate AI report.';
    stopPolling();
  } finally {
    loading.value.llm = false;
  }
};

const forceRegenerateAIReport = async () => {
  llmReport.value = '';
  // 必须手动停止之前的轮询并开启新的请求
  stopPolling();
  await generateAIReport(true);
};

watch(() => route.path, () => syncTabWithRoute());

onMounted(async () => {
  syncTabWithRoute();
  loading.value.app = true;
  try {
    const appRes = await getApp(route.params.id);
    app.value = appRes.data;
    
    // Init meta info
    generationMeta.value = {
        startTime: app.value.llmStartTime,
        endTime: app.value.llmEndTime,
        duration: (app.value.llmEndTime && app.value.llmStartTime) ? (app.value.llmEndTime - app.value.llmStartTime) : null
    };

    if (app.value && (app.value.llmReport === '[GENERATING]' || app.value.llmReport === 'GENERATING')) {
      startPolling(app.value.llmStartTime);
    } else if (app.value) {
      llmReport.value = app.value.llmReport;
    }
  } finally {
    loading.value.app = false;
  }
});

onUnmounted(() => stopPolling());
</script>

<style scoped>
.app-detail {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
}

.header-bar {
  background: white;
  padding: 0 2rem;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 50px;
  gap: 20px;
}

@media (max-width: 900px) {
  .header-bar {
    flex-direction: column-reverse;
    align-items: flex-start;
    padding: 0.5rem 1rem;
    gap: 10px;
  }
}

.app-info h2 {
  margin: 0;
  font-size: 1rem;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.app-info small {
  color: #999;
  font-weight: normal;
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.tabs {
  display: flex;
  align-items: stretch;
  height: 50px;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  max-width: 100%;
}

.tabs::-webkit-scrollbar { display: none; }
.tabs { -ms-overflow-style: none; scrollbar-width: none; }

.tab-link {
  display: flex;
  align-items: center;
  text-decoration: none;
  padding: 0 1.2rem;
  cursor: pointer;
  font-weight: 600;
  color: #555;
  border-bottom: 3px solid transparent;
  height: 100%;
  transition: all 0.2s;
  font-size: 0.9rem;
  white-space: nowrap;
}

.tab-link:hover { color: #3498db; background: #f9f9f9; }
.tab-link.active { color: #3498db; border-bottom-color: #3498db; }

.content-area {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem;
  background: #f8f9fa;
}

.diagnosis-layout { display: flex; flex-direction: column; }

.folder-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid #eee;
}

.folder-tabs {
  display: flex;
  background: #f1f3f5;
  border-bottom: 1px solid #e9ecef;
  padding: 0 10px;
}

.folder-tab {
  padding: 12px 20px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.9rem;
  color: #666;
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  transition: all 0.2s;
  border-top-left-radius: 6px;
  border-top-right-radius: 6px;
  margin-top: 6px;
  border: 1px solid transparent;
  border-bottom: none;
}

.folder-tab:hover { color: #333; background: #e9ecef; }
.folder-tab.active {
  background: white;
  color: #3498db;
  border-color: #e9ecef;
  margin-bottom: -1px;
  z-index: 1;
}

.folder-content { padding: 2rem; min-height: 400px; }

.markdown-body { line-height: 1.8; color: #2c3e50; font-size: 0.95rem; }
.markdown-body :deep(h1), .markdown-body :deep(h2) {
  border-bottom: 2px solid #eee; padding-bottom: 0.5rem; margin-top: 1.5rem; color: #34495e;
}
.markdown-body :deep(h3) { color: #2980b9; margin-top: 1.2rem; }
.markdown-body :deep(code) {
  background-color: #f8f9fa; padding: 2px 6px; border-radius: 4px; color: #e74c3c;
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid #42b983; padding: 0.5rem 1rem; background: #f0fff4; border-radius: 0 4px 4px 0;
}

.loading-placeholder {
  display: flex; justify-content: center; align-items: center; height: 200px; color: #666; font-style: italic;
}

.ai-loading {
  display: flex; flex-direction: column; align-items: center; gap: 15px; padding: 3rem; color: #667eea;
}

.polling-hint { color: #999; font-size: 0.8rem; font-weight: normal; }

.spinner {
  width: 40px; height: 40px; border: 4px solid #f3f3f3; border-top: 4px solid #667eea; border-radius: 50%;
  animation: spin 1s linear infinite;
}

.report-header {

  display: flex;

  justify-content: space-between;

  align-items: center;

  margin-bottom: 1.5rem;

  padding-bottom: 1rem;

  border-bottom: 1px solid #eee;

}



.header-left {

  display: flex;

  flex-direction: column;

  gap: 4px;

}



.generation-meta {

  display: flex;

  gap: 12px;

  font-size: 0.75rem;

  color: #999;

}



.meta-item {

  display: flex;

  align-items: center;

  gap: 4px;

}



.report-title {

  font-weight: 600;

  color: #2c3e50;

  font-size: 1.1rem;

  display: flex;

  align-items: center;

  gap: 8px;

}

.regenerate-btn {
  background: white;
  border: 1px solid #dcdfe6;
  padding: 6px 12px;
  border-radius: 4px;
  color: #606266;
  font-size: 0.85rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.regenerate-btn:hover {
  background: #f5f7fa;
  color: #3498db;
  border-color: #3498db;
  box-shadow: 0 4px 8px rgba(52, 152, 219, 0.15);
}

.regenerate-btn .material-symbols-outlined {
  font-size: 1.1rem;
}

.ai-intro { text-align: center; padding: 2rem; color: #555; }
.generate-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; padding: 10px 24px; border-radius: 24px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; gap: 8px;
}

@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>