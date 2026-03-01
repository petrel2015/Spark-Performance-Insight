<template>
  <CollapsibleCard title="Executor Lifecycle & Performance Correlation" :initial-collapsed="false">
    <template #actions>
      <button class="lock-btn" 
              v-if="dashboardRef"
              @click.stop="dashboardRef.toggleZoomLock()" 
              :title="dashboardRef.isZoomLocked ? 'Unlock Zoom' : 'Lock Zoom'">
        <span class="material-symbols-outlined" style="font-size: 14px; vertical-align: middle; margin-right: 4px;">
          {{ dashboardRef.isZoomLocked ? 'lock' : 'lock_open' }}
        </span>
        {{ dashboardRef.isZoomLocked ? 'Locked' : 'Unlocked' }}
      </button>
    </template>
    <ExecutorCombinedChart ref="dashboardRef" :performanceData="performanceData" :executors="executors" />
  </CollapsibleCard>
</template>

<script setup>
import { ref } from 'vue';
import CollapsibleCard from '../common/CollapsibleCard.vue';
import ExecutorCombinedChart from './ExecutorCombinedChart.vue';

defineProps({
  performanceData: { type: Array, default: () => [] },
  executors: { type: Array, default: () => [] }
});

const dashboardRef = ref(null);
</script>

<style scoped>
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
