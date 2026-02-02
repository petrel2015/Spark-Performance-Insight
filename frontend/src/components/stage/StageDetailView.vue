<template>
  <div class="stage-detail-container">
    <div class="breadcrumb-nav">
      <button @click="$emit('back')" class="back-btn">← Back to Stages</button>
    <div class="stage-title">
        <h3>
          Details for Stage {{ stageId }} 
          <small v-if="currentStage?.jobId">
            of Job <a href="#" class="job-link" @click.prevent="$emit('view-job', currentStage.jobId)">{{ currentStage.jobId }}</a>
          </small>
        </h3>
        <span v-if="currentStage" class="stage-name-subtitle">{{ currentStage.stageName }}</span>
      </div>
    </div>

    <!-- Timeline Chart -->
    <CollapsibleCard v-if="currentStage" title="Event Timeline">
      <StageTimeline :app-id="appId" :stage-id="stageId" />
    </CollapsibleCard>

    <!-- RDD Lineage Visualization -->
    <CollapsibleCard v-if="currentStage && currentStage.rddInfo" title="RDD Lineage">
      <StageDAG :stage="currentStage" />
    </CollapsibleCard>

    <!-- Metric Visibility Selector -->
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

    <!-- Summary Metrics Cards -->
    <CollapsibleCard v-if="stageStats && stageStats.length > 0" :title="'Summary Metrics for Stage ' + stageId">
      <StageSummary 
        :stats="stageStats" 
        :stage-id="stageId" 
        :visible-metrics="selectedMetrics"
        :stage="currentStage"
      />
    </CollapsibleCard>
    
    <div v-else-if="currentStage" style="color: #999; padding: 10px;">
      No detailed statistics available.
    </div>

    <!-- Executor Summary Card -->
    <CollapsibleCard v-if="executorSummary && executorSummary.length > 0" title="Aggregated Metrics by Executor">
      <ExecutorSummary
        :summary="executorSummary"
        :visible-metrics="selectedMetrics"
      />
    </CollapsibleCard>

    <!-- Task Details Section -->
    <CollapsibleCard v-if="currentStage" title="Tasks List">
      <TaskTable 
        :app-id="appId" 
        :stage-id="stageId" 
        :visible-metrics="selectedMetrics"
      />
    </CollapsibleCard>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getStage, getStageStats, getExecutorSummary } from '../../api';
import { AVAILABLE_METRICS, DEFAULT_METRICS } from '../../constants/metrics';
import CollapsibleCard from '../common/CollapsibleCard.vue';
import StageSummary from './StageSummary.vue';
import ExecutorSummary from './ExecutorSummary.vue';
import StageTimeline from './StageTimeline.vue';
import TaskTable from '../task/TaskTable.vue';
import StageDAG from './StageDAG.vue';

const props = defineProps({
  appId: {
    type: String,
    required: true
  },
  stageId: {
    type: Number,
    required: true
  }
});

const emit = defineEmits(['back', 'view-job']);

const currentStage = ref(null);
const stageStats = ref([]);
const executorSummary = ref([]);
const selectedMetrics = ref([...DEFAULT_METRICS]);

const selectAllMetrics = () => {
  selectedMetrics.value = AVAILABLE_METRICS.map(m => m.key);
};

const clearAllMetrics = () => {
  selectedMetrics.value = [];
};

const fetchStageDetails = async () => {
  if (!props.stageId || !props.appId) return;
  try {
    const [stageRes, statsRes, execSummaryRes] = await Promise.all([
      getStage(props.appId, props.stageId),
      getStageStats(props.appId, props.stageId, 0),
      getExecutorSummary(props.appId, props.stageId)
    ]);
    currentStage.value = stageRes.data;
    stageStats.value = statsRes.data;
    executorSummary.value = execSummaryRes.data;
    console.log("Stage Details Loaded:", { stage: currentStage.value, stats: stageStats.value });
  } catch (err) {
    console.error("Failed to fetch stage details:", err);
    console.error("AppID:", props.appId, "StageID:", props.stageId);
    currentStage.value = null;
    stageStats.value = [];
  }
};

onMounted(fetchStageDetails);

watch(() => props.stageId, fetchStageDetails);
</script>

<style scoped>
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
.breadcrumb-nav h3 small { font-weight: normal; color: #7f8c8d; margin-left: 5px; }

.job-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}
.job-link:hover {
  text-decoration: underline;
}

.stage-title {
  display: flex;
  flex-direction: column;
}

.stage-name-subtitle {
  font-size: 0.8rem;
  color: #7f8c8d;
}

.metric-selector-card {
  background: white;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 8px;
}

.selector-header strong { font-size: 0.9rem; color: #2c3e50; }

.selector-actions { display: flex; gap: 10px; }
.selector-actions button { 
  background: none; border: 1px solid #ddd; padding: 2px 8px; border-radius: 4px; 
  font-size: 0.75rem; cursor: pointer; color: #666;
}
.selector-actions button:hover { border-color: #3498db; color: #3498db; }

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

.checkbox-item input { cursor: pointer; }

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
</style>
