<template>
  <div class="job-detail-container">
    <div class="breadcrumb-nav">
      <button @click="$emit('back')" class="back-btn">← Back to Jobs</button>
      <div class="job-title">
        <h3>Details for Job {{ jobId }}</h3>
        <span v-if="currentJob" class="job-description-subtitle">{{ currentJob.description }}</span>
      </div>
    </div>

    <!-- 0. Job DAG Visualization -->
    <CollapsibleCard v-if="currentJob" title="Job DAG Visualization" :initial-collapsed="false">
      <template #actions>
        <button class="lock-btn"
                v-if="dagRef"
                @click="dagRef.toggleZoomLock()"
                :title="dagRef.isZoomLocked ? 'Unlock Zoom' : 'Lock Zoom'">
          {{ dagRef.isZoomLocked ? '🔒 Locked' : '🔓 Unlocked' }}
        </button>
      </template>
      <JobDAG ref="dagRef" :app-id="appId" :job-id="jobId"/>
    </CollapsibleCard>

    <!-- 0.5. Job Event Timeline -->
    <CollapsibleCard v-if="currentJob" title="Event Timeline (Executors & Stages)" :initial-collapsed="false">
      <template #actions>
        <button class="lock-btn"
                v-if="timelineRef"
                @click="timelineRef.toggleZoomLock()"
                :title="timelineRef.isZoomLocked ? 'Unlock Zoom' : 'Lock Zoom'">
          {{ timelineRef.isZoomLocked ? '🔒 Locked' : '🔓 Unlocked' }}
        </button>
      </template>
      <JobTimeline ref="timelineRef" :app-id="appId" :job-id="jobId"/>
    </CollapsibleCard>

    <!-- 1. Aggregated Metrics by Executor -->
    <CollapsibleCard v-if="executorSummary && executorSummary.length > 0"
                     title="Aggregated Metrics by Executor (Job Level)">
      <ExecutorSummary
          :summary="executorSummary"
          :visible-metrics="selectedMetrics"
      />
    </CollapsibleCard>

    <!-- 1.5. Metric Visibility Selector -->
    <div class="metric-selector-card">
      <div class="selector-header">
        <strong>Select Metrics to Display:</strong>
        <div class="selector-actions">
          <button @click="selectAllMetrics">Select All</button>
          <button @click="clearAllMetrics">Clear All</button>
        </div>
      </div>
      <div class="checkbox-group">
        <label v-for="m in AVAILABLE_METRICS" :key="m.key" class="checkbox-item">
          <input type="checkbox" :value="m.key" v-model="selectedMetrics">
          {{ m.label }}
        </label>
      </div>
    </div>

    <!-- 2. Job Stages List -->
    <CollapsibleCard title="Stages">
      <StageTable
          :app-id="appId"
          :job-id="jobId"
          :hide-title="true"
          :plain="true"
          :visible-metrics="selectedMetrics"
          @view-stage-detail="onViewStage"
      />
    </CollapsibleCard>
  </div>
</template>

<script setup>
import {ref, onMounted, watch} from 'vue';
import {getJobExecutorSummary, getJob} from '../../api';
import CollapsibleCard from '../common/CollapsibleCard.vue';
import StageTable from '../stage/StageTable.vue';
import ExecutorSummary from '../stage/ExecutorSummary.vue';
import JobDAG from './JobDAG.vue';
import JobTimeline from './JobTimeline.vue';
import {AVAILABLE_METRICS, DEFAULT_METRICS} from '../../constants/metrics';

const props = defineProps({
  appId: {type: String, required: true},
  jobId: {type: Number, required: true}
});

const emit = defineEmits(['back', 'view-stage']);

const currentJob = ref(null);
const executorSummary = ref([]);
const dagRef = ref(null);
const timelineRef = ref(null);
const selectedMetrics = ref([...DEFAULT_METRICS]);

const selectAllMetrics = () => {
  selectedMetrics.value = AVAILABLE_METRICS.map(m => m.key);
};

const clearAllMetrics = () => {
  selectedMetrics.value = [];
};

const fetchJobDetails = async () => {
  try {
    const [jobRes, execRes] = await Promise.all([
      getJob(props.appId, props.jobId),
      getJobExecutorSummary(props.appId, props.jobId)
    ]);
    currentJob.value = jobRes.data;
    executorSummary.value = execRes.data;
  } catch (err) {
    console.error("Failed to fetch job details", err);
  }
};

const onViewStage = (stageId) => {
  emit('view-stage', stageId);
};

onMounted(fetchJobDetails);
watch(() => props.jobId, fetchJobDetails);
</script>

<style scoped>
.job-detail-container {
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
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.job-title h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #2c3e50;
}

.job-description-subtitle {
  font-size: 0.8rem;
  color: #7f8c8d;
}

.back-btn {
  background: #6c757d;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.metric-selector-card {
  background: white;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 8px;
}

.selector-header strong {
  font-size: 0.9rem;
  color: #2c3e50;
}

.selector-actions {
  display: flex;
  gap: 10px;
}

.selector-actions button {
  background: none;
  border: 1px solid #ddd;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
  color: #666;
}

.selector-actions button:hover {
  border-color: #3498db;
  color: #3498db;
}

.checkbox-group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px 15px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: #555;
  cursor: pointer;
  white-space: nowrap;
}

.checkbox-item input {
  cursor: pointer;
}

.lock-btn {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.7rem;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
  min-width: 80px;
  text-align: center;
}

.lock-btn:hover {
  background: #f0f0f0;
  color: #333;
}
</style>
