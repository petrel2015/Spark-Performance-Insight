<template>
  <div class="sql-detail-container">
    <div class="breadcrumb-nav">
      <button @click="$emit('back')" class="back-btn">← Back to SQL List</button>
      <div class="sql-title">
        <h3>Details for SQL Execution {{ executionId }}</h3>
        <span v-if="sql" class="sql-description-subtitle">{{ sql.description }}</span>
      </div>
    </div>

    <div class="detail-cards" v-if="sql">
      <!-- 0. Performance Diagnosis -->
      <CollapsibleCard title="Performance Diagnosis" :initial-collapsed="false">
        <SQLDiagnosisCard 
          :performance-score="sql.performanceScore || sql.performance_score || 0" 
          :jobs="sql.jobList || []"
          @view-job="navigateToJob"
        />
      </CollapsibleCard>

      <!-- Info Card -->
      <div class="info-card">
        <div class="info-item">
          <strong>Status:</strong>
          <span :class="'status-' + sql.status">{{ sql.status }}</span>
        </div>
        <div class="info-item">
          <strong>Submitted:</strong>
          <span>{{ formatDateTime(sql.startTime) }}</span>
        </div>
        <div class="info-item">
          <strong>Duration:</strong>
          <span>{{ formatDuration(sql.duration) }}</span>
        </div>
        <div class="info-item" v-if="sql.jobIds && sql.jobIds.length > 0">
          <strong>Associated Jobs:</strong>
          <span>
            <span v-for="(jid, idx) in sql.jobIds" :key="jid">
              <router-link :to="'/app/' + appId + '/job/' + jid" class="job-id-link">{{ jid }}</router-link>
              <span v-if="idx < sql.jobIds.length - 1">, </span>
            </span>
          </span>
        </div>
      </div>

      <!-- Plan Visualization -->
      <CollapsibleCard title="Plan Visualization" :initial-collapsed="true" v-if="parsedPlanInfo">
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
        <SQLDAG ref="dagRef" :plan-info="parsedPlanInfo" />
      </CollapsibleCard>

      <!-- Physical Plan Card -->
      <CollapsibleCard title="Physical Plan" :initial-collapsed="true">
        <pre class="physical-plan">{{ sql.physicalPlan }}</pre>
      </CollapsibleCard>

      <!-- Associated Jobs List -->
      <CollapsibleCard title="Associated Jobs">
        <JobsTab 
          :app-id="appId" 
          :sql-execution-id="executionId" 
          :hide-toolbar="true"
        />
      </CollapsibleCard>
    </div>
    
    <div v-else-if="loading" class="loading-placeholder">
      Loading SQL details...
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, computed} from 'vue';
import {getSqlExecution} from '../../api';
import {formatTime as formatDuration, formatDateTime} from '../../utils/format';
import CollapsibleCard from '../common/CollapsibleCard.vue';
import SQLDAG from './SQLDAG.vue';
import SQLDiagnosisCard from './SQLDiagnosisCard.vue';
import JobsTab from '../job/JobsTab.vue';
import { useRouter } from 'vue-router';

const props = defineProps({
  appId: {type: String, required: true},
  executionId: {type: Number, required: true}
});

const emit = defineEmits(['back']);
const router = useRouter();

const sql = ref(null);
const loading = ref(false);
const dagRef = ref(null);

const parsedPlanInfo = computed(() => {
  if (!sql.value || !sql.value.planInfo) return null;
  try {
    return JSON.parse(sql.value.planInfo);
  } catch (e) {
    console.warn("Failed to parse planInfo JSON", e);
    return null;
  }
});

const fetchDetails = async () => {
  loading.value = true;
  try {
    const res = await getSqlExecution(props.appId, props.executionId);
    sql.value = res.data;
  } catch (err) {
    console.error("Failed to fetch SQL details", err);
  } finally {
    loading.value = false;
  }
};

const navigateToJob = (jobId) => {
  router.push(`/app/${props.appId}/job/${jobId}`);
};

onMounted(fetchDetails);
watch(() => props.executionId, fetchDetails);
</script>

<style scoped>
.sql-detail-container {
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

.sql-title h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #2c3e50;
}

.sql-description-subtitle {
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

.detail-cards {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.info-card {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-wrap: wrap;
  gap: 2rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item strong {
  font-size: 0.8rem;
  color: #7f8c8d;
  text-transform: uppercase;
}

.info-item span {
  font-size: 1rem;
  color: #2c3e50;
  font-weight: 600;
}

.physical-plan {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 4px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.85rem;
  white-space: pre-wrap;
  word-break: break-all;
  color: #333;
  margin: 0;
}

.job-id-link {
  color: #3498db;
  text-decoration: none;
}

.job-id-link:hover {
  text-decoration: underline;
}

.status-SUCCEEDED {
  color: #27ae60;
}

.status-FAILED {
  color: #e74c3c;
}

.status-RUNNING {
  color: #f39c12;
}

.loading-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: #666;
  font-style: italic;
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
