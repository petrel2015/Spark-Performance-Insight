<template>
  <div class="stage-detail-container">
    <div class="breadcrumb-nav">
      <button @click="$emit('back')" class="back-btn">← Back to Stages</button>
      <div class="stage-title">
        <h3>
          Details for Stage {{ stageId }}
          <span v-if="currentStage?.attemptId > 0" class="attempt-badge">(Attempt {{ currentStage.attemptId }})</span>
          <small v-if="currentStage?.jobId">
            of Job <router-link :to="'/app/' + appId + '/job/' + currentStage.jobId" class="job-link">{{ currentStage.jobId }}</router-link>
          </small>
        </h3>
        <span v-if="currentStage" class="stage-name-subtitle">{{ currentStage.stageName }}</span>
        <div v-if="currentStage?.localitySummary" class="locality-summary">
          <span class="locality-label">Locality Level:</span>
          <span class="locality-value">{{ currentStage.localitySummary }}</span>
        </div>
      </div>
    </div>

    <!-- RDD Lineage Visualization -->
    <CollapsibleCard v-if="currentStage && currentStage.rddInfo" title="DAG Visualization (RDD Lineage)"
                     :initial-collapsed="true">
      <template #actions>
        <button class="lock-btn"
                v-if="dagRef"
                @click="dagRef.toggleZoomLock()"
                :title="dagRef.isZoomLocked ? 'Unlock Zoom' : 'Lock Zoom'">
          <span class="material-symbols-outlined" style="font-size: 14px; vertical-align: middle; margin-right: 4px;">
            {{ dagRef.isZoomLocked ? 'lock' : 'lock_open' }}
          </span>
          {{ dagRef.isZoomLocked ? 'Locked' : 'Unlocked' }}
        </button>
      </template>
      <StageDAG ref="dagRef" :stage="currentStage"/>
    </CollapsibleCard>

    <!-- Timeline Chart -->
    <CollapsibleCard v-if="currentStage" title="Event Timeline" :initial-collapsed="true">
      <template #actions>
        <button class="lock-btn"
                v-if="timelineRef"
                @click="timelineRef.toggleZoomLock()"
                :title="timelineRef.isZoomLocked ? 'Unlock Zoom' : 'Lock Zoom'">
          <span class="material-symbols-outlined" style="font-size: 14px; vertical-align: middle; margin-right: 4px;">
            {{ timelineRef.isZoomLocked ? 'lock' : 'lock_open' }}
          </span>
          {{ timelineRef.isZoomLocked ? 'Locked' : 'Unlocked' }}
        </button>
      </template>
      <StageTimeline ref="timelineRef" :app-id="appId" :stage-id="stageId" :attempt-id="currentStage?.attemptId"/>
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
    <CollapsibleCard v-if="stageStats && stageStats.length > 0"
                     :title="`Summary Metrics for Stage ${stageId} (${currentStage?.numCompletedTasks || 0} completed tasks)`">
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
          :attempt-id="currentStage?.attemptId"
          :visible-metrics="selectedMetrics"
      />
    </CollapsibleCard>
  </div>
</template>

<script setup>
import {ref, onMounted, watch} from 'vue';
import {getStage, getStageStats, getExecutorSummary} from '../../api';
import {AVAILABLE_METRICS, DEFAULT_METRICS} from '../../constants/metrics';
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
  },
  attemptId: {
    type: Number,
    default: null
  }
});

const emit = defineEmits(['back', 'view-job']);

const currentStage = ref(null);
const stageStats = ref([]);
const executorSummary = ref([]);
const selectedMetrics = ref([...DEFAULT_METRICS]);

const timelineRef = ref(null);
const dagRef = ref(null);

const selectAllMetrics = () => {
  selectedMetrics.value = AVAILABLE_METRICS.map(m => m.key);
};

const clearAllMetrics = () => {
  selectedMetrics.value = [];
};

const fetchStageDetails = async () => {
  if (!props.stageId || !props.appId) return;
  try {
    // 1. Get Stage info first to determine attemptId if not provided
    const stageRes = await getStage(props.appId, props.stageId, props.attemptId);
    currentStage.value = stageRes.data;

    if (currentStage.value) {
      // Use the actual attemptId from the fetched stage (it might be the latest one if props.attemptId was null)
      const actualAttemptId = currentStage.value.attemptId || 0;

      const [statsRes, execSummaryRes] = await Promise.all([
        getStageStats(props.appId, props.stageId, actualAttemptId),
        getExecutorSummary(props.appId, props.stageId, actualAttemptId)
      ]);
      stageStats.value = statsRes.data;
      executorSummary.value = execSummaryRes.data;
      console.log("Stage Details Loaded:", {stage: currentStage.value, stats: stageStats.value});
    }
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
  gap: 1rem;
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

.breadcrumb-nav h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #2c3e50;
}

.breadcrumb-nav h3 small {
  font-weight: normal;
  color: #7f8c8d;
  margin-left: 5px;
}

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

.locality-summary {
  font-size: 0.8rem;
  margin-top: 4px;
}

.locality-label {
  font-weight: 600;
  color: #555;
  margin-right: 6px;
}

.locality-value {
  color: #34495e;
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

.back-btn:hover {
  background: #5a6268;
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
