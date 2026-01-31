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
      <JobsTab v-if="activeTab === 'Jobs'" :jobs="jobs" />

      <!-- 3. Stages Tab -->
      <div v-if="activeTab === 'Stages'" class="stages-view">
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
            <tr v-for="stage in stages" :key="stage.stageId" :class="{ selected: selectedStageId === stage.stageId }">
              <td>{{ stage.stageId }}</td>
              <td>{{ stage.stageName }}</td>
              <td>{{ stage.numTasks }}</td>
              <td>{{ (stage.inputBytes / 1024 / 1024).toFixed(1) }} MB</td>
              <td>{{ (stage.shuffleReadBytes / 1024 / 1024).toFixed(1) }} MB</td>
              <td>{{ stage.durationP95 }} ms</td>
              <td>
                <button @click="selectedStageId = stage.stageId" class="view-tasks-btn">View Tasks</button>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Task Details Section -->
        <TaskTable v-if="selectedStageId !== null" :app-id="app.appId" :stage-id="selectedStageId" />
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
const activeTab = ref('Diagnosis');
const selectedStageId = ref(null);
const tabList = ['Diagnosis', 'Jobs', 'Stages', 'Executors', 'Environment'];

const app = ref(null);
const stages = ref([]);
const jobs = ref([]);
const executors = ref([]);
const environment = ref([]);
const report = ref('');

const renderedReport = computed(() => marked(report.value));

onMounted(async () => {
  const appId = route.params.id;
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

tr.selected { background-color: #ebf5fb; }

.markdown-body { line-height: 1.6; }
</style>