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
        <div class="sidebar">
          <h3>Stages Summary</h3>
          <div v-for="stage in stages" :key="stage.stageId" class="stage-item" :class="{ skew: stage.isSkewed }">
            <strong>Stage {{ stage.stageId }}</strong>
            <small>P95: {{ stage.durationP95 }}ms</small>
          </div>
        </div>
        <div class="main-report">
          <div class="markdown-body" v-html="renderedReport"></div>
        </div>
      </div>

      <!-- 2. Jobs Tab -->
      <JobsTab v-if="activeTab === 'Jobs'" :jobs="jobs" @view-job-stages="handleViewJobStages" />

      <!-- 3. Stages Tab -->
      <div v-if="activeTab === 'Stages'" class="stages-view">
        <!-- List View -->
        <div v-if="selectedStageId === null">
          <div v-if="selectedJob" class="filter-banner">
            <span>Filtering by Job ID: <strong>{{ selectedJob.jobId }}</strong></span>
            <button @click="selectedJob = null" class="clear-filter-btn">Clear Filter</button>
          </div>
          <table class="styled-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Tasks</th>
                <th>Input</th>
                <th>Shuffle Read</th>
                <th>P95 Duration</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="stage in filteredStages" :key="stage.stageId">
                <td>{{ stage.stageId }}</td>
                <td>{{ stage.stageName }}</td>
                <td>{{ stage.numTasks }}</td>
                <td>{{ (stage.inputBytes / 1024 / 1024).toFixed(1) }} MB</td>
                <td>{{ (stage.shuffleReadBytes / 1024 / 1024).toFixed(1) }} MB</td>
                <td>{{ stage.durationP95 }} ms</td>
                <td>
                  <button @click="router.push(`/app/${app.appId}/stage/${stage.stageId}`)" class="view-tasks-btn">Details</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Detail View -->
        <div v-else class="stage-detail-container">
          <div class="breadcrumb-nav">
            <button @click="router.push(`/app/${app.appId}`)" class="back-btn">← Back to Stages</button>
            <div class="stage-title">
              <h3>Details for Stage {{ selectedStageId }}</h3>
              <span v-if="currentStage" class="stage-name-subtitle">{{ currentStage.stageName }}</span>
            </div>
            <div class="stage-quick-stats" v-if="currentStage">
              <div class="stat-pill"><strong>Tasks:</strong> {{ currentStage.numTasks }}</div>
              <div class="stat-pill"><strong>Input:</strong> {{ formatBytes(currentStage.inputBytes) }}</div>
            </div>
          </div>
          <!-- Task Details Section -->
          <TaskTable :app-id="app.appId" :stage-id="selectedStageId" />
        </div>
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
import { useRoute } from 'vue-router';
import { getAppStages, getDiagnosisReport, getAppJobs, getAppExecutors, getApps, getAppEnvironment } from '../api';
import { marked } from 'marked';
import JobsTab from '../components/JobsTab.vue';
import ExecutorsTab from '../components/ExecutorsTab.vue';
import TaskTable from '../components/TaskTable.vue';
import EnvironmentTab from '../components/EnvironmentTab.vue';

const route = useRoute();
import { useRouter } from 'vue-router';
const router = useRouter();

const activeTab = ref('Diagnosis');
const selectedStageId = computed(() => {
  return route.params.stageId ? parseInt(route.params.stageId) : null;
});

const tabList = ['Diagnosis', 'Jobs', 'Stages', 'Executors', 'Environment'];

const app = ref(null);
const stages = ref([]);
const jobs = ref([]);
const executors = ref([]);
const environment = ref([]);
const report = ref('');
const selectedJob = ref(null);
const stageStats = ref([]);

const renderedReport = computed(() => marked(report.value));

const filteredStages = computed(() => {
  if (!selectedJob.value) return stages.value;
  if (!selectedJob.value.stageIds) return [];
  const stageIds = selectedJob.value.stageIds.split(',').map(id => parseInt(id));
  return stages.value.filter(s => stageIds.includes(s.stageId));
});

const currentStage = computed(() => {
  if (selectedStageId.value === null) return null;
  return stages.value.find(s => s.stageId === selectedStageId.value);
});

const formatMetricName = (name) => {
  const map = {
    'duration': 'Duration',
    'gc_time': 'GC Time',
    'memory_spill': 'Memory Spill',
    'disk_spill': 'Disk Spill',
    'shuffle_read': 'Shuffle Read Size',
    'shuffle_read_records': 'Shuffle Read Records',
    'shuffle_write': 'Shuffle Write Size',
    'shuffle_write_records': 'Shuffle Write Records'
  };
  return map[name] || name;
};

const formatMetricValue = (metric, val) => {
  if (val === null || val === undefined) return '-';
  if (metric.endsWith('_records')) return val.toLocaleString();
  if (metric === 'duration' || metric === 'gc_time') {
    if (val < 1000) return `${val} ms`;
    return `${(val / 1000).toFixed(1)} s`;
  }
  return formatBytes(val);
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

import { watch } from 'vue';
watch(selectedStageId, async (newId) => {
  if (newId !== null) {
    const res = await getStageStats(app.value.appId, newId, 0);
    stageStats.value = res.data;
  } else {
    stageStats.value = [];
  }
});

const handleViewJobStages = (job) => {
  selectedJob.value = job;
  activeTab.value = 'Stages';
};

onMounted(async () => {
  const appId = route.params.id;
  if (route.params.stageId) {
    activeTab.value = 'Stages';
  }
  // 获取基础信息
  const appsRes = await getApps();
  app.value = appsRes.data.find(a => a.appId === appId);

  // 并发加载数据
  const [stagesRes, reportRes, jobsRes, execRes, envRes] = await Promise.all([
    getAppStages(appId),
    getDiagnosisReport(appId),
    getAppJobs(appId),
    getAppExecutors(appId),
    getAppEnvironment(appId)
  ]);

  stages.value = stagesRes.data;
  report.value = reportRes.data;
  jobs.value = jobsRes.data;
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
.sidebar { width: 250px; background: white; border-radius: 8px; padding: 1rem; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.main-report { flex: 1; background: white; border-radius: 8px; padding: 2rem; box-shadow: 0 2px 4px rgba(0,0,0,0.05); overflow-y: auto; }

.stage-item { padding: 8px; border-bottom: 1px solid #eee; display: flex; flex-direction: column; }
.stage-item.skew { border-left: 4px solid #e74c3c; background: #fff5f5; }

.styled-table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; }
.styled-table th, .styled-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
.styled-table th { background: #f1f3f5; }

.view-tasks-btn { background: #3498db; color: white; border: none; padding: 4px 8px; border-radius: 4px; cursor: pointer; font-size: 0.8rem; }
.view-tasks-btn:hover { background: #2980b9; }

.filter-banner {
  background: #e3f2fd;
  padding: 10px 15px;
  border-radius: 4px;
  margin-bottom: 15px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid #bbdefb;
}

.clear-filter-btn {
  background: #f44336;
  color: white;
  border: none;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
}

/* Stage Detail Styles */
.stage-detail-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.breadcrumb-nav {
  display: flex;
  align-items: center;
  gap: 20px;
  background: white;
  padding: 10px 15px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.breadcrumb-nav h3 { margin: 0; font-size: 1.1rem; color: #2c3e50; }

.stage-title {
  display: flex;
  flex-direction: column;
}

.stage-name-subtitle {
  font-size: 0.8rem;
  color: #7f8c8d;
}

.stage-quick-stats {
  margin-left: auto;
  display: flex;
  gap: 15px;
}

.stat-pill {
  background: #f1f3f5;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.85rem;
  color: #495057;
}

.back-btn {
  background: #6c757d;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.2s;
}
.back-btn:hover { background: #5a6268; }

.stage-summary-cards {
  display: flex;
  gap: 1rem;
}

.summary-card.full-width {
  width: 100%;
}

.summary-card {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}


.stats-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.stats-table th, .stats-table td { padding: 8px; text-align: right; border-bottom: 1px solid #eee; }
.stats-table th { background: #f8f9fa; color: #666; font-weight: 600; }
.stats-table td.metric-name { text-align: left; font-weight: bold; color: #333; background: #fdfdfd; }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.metric { font-size: 0.9rem; color: #666; }
.metric span { font-weight: bold; color: #333; margin-right: 5px; }

tr.selected { background-color: #ebf5fb; }

.markdown-body { line-height: 1.6; }
</style>