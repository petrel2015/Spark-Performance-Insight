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
      <JobDAG ref="dagRef" :app-id="appId" :job-id="jobId" />
    </CollapsibleCard>

    <!-- 1. Aggregated Metrics by Executor -->
    <CollapsibleCard v-if="executorSummary && executorSummary.length > 0" title="Aggregated Metrics by Executor (Job Level)">
      <ExecutorSummary
        :summary="executorSummary"
        :visible-metrics="DEFAULT_METRICS"
      />
    </CollapsibleCard>

    <!-- 2. Job Stages List -->
    <CollapsibleCard title="Stages">
      <StageTable 
        :app-id="appId" 
        :job-id="jobId" 
        :hide-title="true" 
        :plain="true"
        @view-stage-detail="onViewStage" 
      />
    </CollapsibleCard>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getJobExecutorSummary, getJob } from '../../api';
import CollapsibleCard from '../common/CollapsibleCard.vue';
import StageTable from '../stage/StageTable.vue';
import ExecutorSummary from '../stage/ExecutorSummary.vue';
import JobDAG from './JobDAG.vue';
import { DEFAULT_METRICS } from '../../constants/metrics';

const props = defineProps({
  appId: { type: String, required: true },
  jobId: { type: Number, required: true }
});

const emit = defineEmits(['back', 'view-stage']);

const currentJob = ref(null);
const executorSummary = ref([]);
const dagRef = ref(null);

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
.job-detail-container { display: flex; flex-direction: column; gap: 1.5rem; }
.breadcrumb-nav { display: flex; align-items: center; gap: 20px; background: white; padding: 10px 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.job-title h3 { margin: 0; font-size: 1.1rem; color: #2c3e50; }
.job-description-subtitle { font-size: 0.8rem; color: #7f8c8d; }
.back-btn { background: #6c757d; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 0.85rem; }

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
