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
        <div v-else class="report-wrapper">
          <CollapsibleCard title="规则引擎诊断报告 (Rule-Based Diagnostic Report)" :initial-collapsed="false">
            <div class="markdown-body" v-html="renderedReport"></div>
          </CollapsibleCard>
        </div>
      </div>

      <!-- 1.5 SQL Tab -->
      <div v-if="activeTab === 'SQL / DataFrame'" class="sql-view">
        <SQLTab
            v-if="selectedExecutionId === null && app"
            :app-id="app.appId"
            @view-sql-detail="navigateToSql"
        />
        <SQLDetailView
            v-else-if="app"
            :app-id="app.appId"
            :execution-id="selectedExecutionId"
            @back="navigateBackToSqlList"
        />
      </div>

      <!-- 2. Jobs Tab -->
      <div v-if="activeTab === 'Jobs'" class="jobs-view">
        <JobsTab
            v-if="selectedJobId === null && app"
            :app-id="app.appId"
            @view-job-detail="navigateToJob"
        />

        <JobDetailView
            v-else-if="app"
            :app-id="app.appId"
            :job-id="selectedJobId"
            @back="navigateBackToJobs"
            @view-stage="navigateToStage"
        />
      </div>

      <!-- 3. Stages Tab -->
      <div v-if="activeTab === 'Stages'" class="stages-view">
        <!-- List View -->
        <div v-if="selectedStageId === null">
          <StageTable v-if="app" :app-id="app.appId" @view-stage-detail="navigateToStage"
                      @view-job-detail="navigateToJob"/>
        </div>

        <!-- Detail View -->
        <StageDetailView
            v-else-if="app"
            :app-id="app.appId"
            :stage-id="selectedStageId"
            :attempt-id="selectedAttemptId"
            @back="navigateBackToStages"
            @view-job="handleViewJob"
        />
      </div>

      <!-- 4. Executors Tab -->
      <ExecutorsTab v-if="activeTab === 'Executors'" :executors="executors"/>

      <!-- 4.5. Storage Tab -->
      <StorageTab v-if="activeTab === 'Storage' && app" :app-id="app.appId" />

      <!-- 5. Environment Tab -->
      <EnvironmentTab v-if="activeTab === 'Environment'" :configs="environment"/>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, computed, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {getDiagnosisReport, getAppExecutors, getApp, getAppEnvironment} from '../api';
import {marked} from 'marked';
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
const activeTab = ref('Diagnosis');

const loading = ref({
  app: false,
  diagnosis: false,
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

const selectedStageId = computed(() => {
  return route.params.stageId ? parseInt(route.params.stageId) : null;
});

const selectedAttemptId = computed(() => {
  return route.query.attemptId ? parseInt(route.query.attemptId) : null;
});

const selectedJobId = computed(() => {
  return route.params.jobId ? parseInt(route.params.jobId) : null;
});

const selectedExecutionId = computed(() => {
  return route.params.executionId ? parseInt(route.params.executionId) : null;
});

const renderedReport = computed(() => marked(report.value));

const fetchDataForTab = async (tab) => {
  const appId = route.params.id;
  if (!appId) return;

  if (tab === 'Diagnosis' && !report.value && !loading.value.diagnosis) {
    loading.value.diagnosis = true;
    try {
      const res = await getDiagnosisReport(appId);
      report.value = res.data;
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
  if (path.includes('/stage/')) {
    newTab = 'Stages';
  } else if (path.includes('/job/')) {
    newTab = 'Jobs';
  } else if (path.includes('/sql')) {
    newTab = 'SQL / DataFrame';
  } else if (path.endsWith('/jobs')) {
    newTab = 'Jobs';
  } else if (path.endsWith('/stages')) {
    newTab = 'Stages';
  } else if (path.endsWith('/executors')) {
    newTab = 'Executors';
  } else if (path.endsWith('/storage')) {
    newTab = 'Storage';
  } else if (path.endsWith('/environment')) {
    newTab = 'Environment';
  }
  
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

const navigateToJob = (jobId) => {
  router.push(`/app/${route.params.id}/job/${jobId}`);
};

const navigateBackToJobs = () => {
  router.push(`/app/${route.params.id}/jobs`);
};

const navigateToSql = (executionId) => {
  router.push(`/app/${route.params.id}/sql/${executionId}`);
};

const navigateBackToSqlList = () => {
  router.push(`/app/${route.params.id}/sql`);
};

const navigateToStage = (payload) => {
  let stageId, attemptId;
  if (typeof payload === 'object') {
    stageId = payload.stageId;
    attemptId = payload.attemptId;
  } else {
    stageId = payload;
  }

  if (attemptId !== undefined && attemptId !== null) {
    router.push({path: `/app/${route.params.id}/stage/${stageId}`, query: {attemptId}});
  } else {
    router.push(`/app/${route.params.id}/stage/${stageId}`);
  }
};

const navigateBackToStages = () => {
  router.push(`/app/${route.params.id}/stages`);
};

const handleViewJobStages = (job) => {
  navigateToTab('Stages');
};

const handleViewJob = (jobId) => {
  navigateToJob(jobId);
};

watch(() => route.path, () => {
  syncTabWithRoute();
});

onMounted(async () => {
  const appId = route.params.id;
  syncTabWithRoute();

  loading.value.app = true;
  try {
    const appRes = await getApp(appId);
    app.value = appRes.data;
  } finally {
    loading.value.app = false;
  }
});
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

/* Hide scrollbar for Chrome, Safari and Opera */
.tabs::-webkit-scrollbar {
  display: none;
}

/* Hide scrollbar for IE, Edge and Firefox */
.tabs {
  -ms-overflow-style: none;  /* IE and Edge */
  scrollbar-width: none;  /* Firefox */
}

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

.tab-link:hover {
  color: #3498db;
  background: #f9f9f9;
}

.tab-link.active {
  color: #3498db;
  border-bottom-color: #3498db;
}

.content-area {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem;
  background: #f8f9fa;
}

.diagnosis-layout {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.report-wrapper {
  width: 100%;
}

.markdown-body {
  line-height: 1.8;
  color: #2c3e50;
  font-size: 0.95rem;
}

.markdown-body :deep(h1), 
.markdown-body :deep(h2) {
  border-bottom: 2px solid #eee;
  padding-bottom: 0.5rem;
  margin-top: 1.5rem;
  color: #34495e;
}

.markdown-body :deep(h3) {
  color: #2980b9;
  margin-top: 1.2rem;
}

.markdown-body :deep(code) {
  background-color: #f8f9fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #e74c3c;
  font-family: 'Courier New', Courier, monospace;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #42b983;
  padding: 0.5rem 1rem;
  background: #f0fff4;
  margin: 1rem 0;
  border-radius: 0 4px 4px 0;
}

.loading-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: #666;
  font-style: italic;
  font-size: 1.1rem;
}
</style>