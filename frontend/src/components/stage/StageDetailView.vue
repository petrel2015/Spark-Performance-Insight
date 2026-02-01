<template>
  <div class="stage-detail-container">
    <div class="breadcrumb-nav">
      <button @click="$emit('back')" class="back-btn">← Back to Stages</button>
      <div class="stage-title">
        <h3>Details for Stage {{ stageId }}</h3>
        <span v-if="currentStage" class="stage-name-subtitle">{{ currentStage.stageName }}</span>
      </div>
      <div class="stage-quick-stats" v-if="currentStage">
        <div class="stat-pill"><strong>Tasks:</strong> {{ currentStage.numTasks }}</div>
        <div class="stat-pill"><strong>Input:</strong> {{ formatBytes(currentStage.inputBytes) }}</div>
      </div>
    </div>

    <!-- Stage Overview Card -->
    <StageOverview v-if="currentStage" :stage="currentStage" />

    <!-- Summary Metrics Cards -->
    <StageSummary v-if="stageStats && stageStats.length > 0" :stats="stageStats" :stage-id="stageId" />
    <div v-else-if="currentStage" style="color: #999; padding: 10px;">
      No detailed statistics available.
    </div>

    <!-- Timeline Chart -->
    <StageTimeline v-if="currentStage" :app-id="appId" :stage-id="stageId" />

    <!-- Task Details Section -->
    <TaskTable :app-id="appId" :stage-id="stageId" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getStage, getStageStats } from '../../api';
import StageOverview from './StageOverview.vue';
import StageSummary from './StageSummary.vue';
import StageTimeline from './StageTimeline.vue';
import TaskTable from '../task/TaskTable.vue';

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

const emit = defineEmits(['back']);

const currentStage = ref(null);
const stageStats = ref([]);

const fetchStageDetails = async () => {
  if (!props.stageId || !props.appId) return;
  try {
    const [stageRes, statsRes] = await Promise.all([
      getStage(props.appId, props.stageId),
      getStageStats(props.appId, props.stageId, 0)
    ]);
    stageStats.value = statsRes.data;
    console.log("Stage Details Loaded:", { stage: currentStage.value, stats: stageStats.value });
  } catch (err) {
    console.error("Failed to fetch stage details:", err);
    console.error("AppID:", props.appId, "StageID:", props.stageId);
    currentStage.value = null;
    stageStats.value = [];
  }
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
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
</style>
