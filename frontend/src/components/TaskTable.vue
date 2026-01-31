<template>
  <div class="task-table-container">
    
    <!-- Summary Metrics -->
    <div class="summary-section" v-if="stats.length > 0">
      <h4>Summary Metrics for Stage {{ stageId }}</h4>
      <table class="styled-table summary-table">
        <thead>
          <tr>
            <th>Metric</th>
            <th>Min</th>
            <th>25th %</th>
            <th>Median</th>
            <th>75th %</th>
            <th>95th %</th>
            <th>Max</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="stat in stats" :key="stat.metricName">
            <td>{{ formatMetricName(stat.metricName) }}</td>
            <td>{{ formatValue(stat.metricName, stat.minValue) }}</td>
            <td>{{ formatValue(stat.metricName, stat.p25) }}</td>
            <td>{{ formatValue(stat.metricName, stat.p50) }}</td>
            <td>{{ formatValue(stat.metricName, stat.p75) }}</td>
            <td>{{ formatValue(stat.metricName, stat.p95) }}</td>
            <td>{{ formatValue(stat.metricName, stat.maxValue) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-header">
      <h4>Tasks List</h4>
      <div class="pagination-controls">
        <button @click="changePage(-1)" :disabled="currentPage === 1">Prev</button>
        <span>Page {{ currentPage }}</span>
        <button @click="changePage(1)" :disabled="tasks.length < pageSize">Next</button>
      </div>
    </div>

    <table class="styled-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Index</th>
          <th>Host</th>
          <th>Executor</th>
          <th>Duration (ms)</th>
          <th>GC Time (ms)</th>
          <th>Input</th>
          <th>Spill (Disk)</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.taskId">
          <td>{{ task.taskId }}</td>
          <td>{{ task.taskId % 1000 }}</td> <!-- Simple index approximation -->
          <td>{{ task.host }}</td>
          <td>{{ task.executorId }}</td>
          <td>{{ task.duration }}</td>
          <td :class="{ 'high-gc': task.gcTime > task.duration * 0.1 }">{{ task.gcTime }}</td>
          <td>{{ (task.inputBytes / 1024 / 1024).toFixed(2) }} MB</td>
          <td :class="{ 'has-spill': task.diskBytesSpilled > 0 }">
            {{ (task.diskBytesSpilled / 1024 / 1024).toFixed(2) }} MB
          </td>
          <td>{{ task.status }}</td>
        </tr>
        <tr v-if="tasks.length === 0">
          <td colspan="9" style="text-align: center;">No tasks found or loading...</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getStageTasks, getStageStats } from '../api';

const props = defineProps({
  appId: String,
  stageId: Number
});

const tasks = ref([]);
const stats = ref([]);
const currentPage = ref(1);
const pageSize = ref(50);

const fetchTasks = async () => {
  try {
    const res = await getStageTasks(props.appId, props.stageId, currentPage.value, pageSize.value);
    tasks.value = res.data;
  } catch (err) {
    console.error("Failed to fetch tasks", err);
  }
};

const fetchStats = async () => {
  try {
    const res = await getStageStats(props.appId, props.stageId);
    stats.value = res.data;
  } catch (err) {
    console.error("Failed to fetch stage stats", err);
  }
};

const formatMetricName = (name) => {
  const map = {
    'duration': 'Duration (ms)',
    'gc_time': 'GC Time (ms)',
    'input_bytes': 'Input Bytes',
    'shuffle_read_bytes': 'Shuffle Read',
    'shuffle_write_bytes': 'Shuffle Write'
  };
  return map[name] || name;
};

const formatValue = (metric, value) => {
  if (value === null || value === undefined) return '-';
  if (metric.includes('bytes')) {
    if (value < 1024) return value + ' B';
    if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
    return (value / 1024 / 1024).toFixed(1) + ' MB';
  }
  return value;
};

const loadData = () => {
  fetchTasks();
  fetchStats();
};

onMounted(loadData);

// Re-fetch when stage changes
watch(() => props.stageId, () => {
  currentPage.value = 1;
  loadData();
});
</script>

<style scoped>
.task-table-container { margin-top: 1.5rem; background: white; border-radius: 8px; padding: 1rem; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
.table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.pagination-controls { display: flex; gap: 10px; align-items: center; }
.pagination-controls button { padding: 4px 12px; cursor: pointer; border: 1px solid #ddd; border-radius: 4px; background: #fff; }

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; margin-bottom: 2rem; }
.styled-table th, .styled-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
.styled-table th { background: #f8f9fa; color: #333; }

.summary-section { margin-bottom: 2rem; border-bottom: 2px solid #eee; padding-bottom: 1rem; }
.summary-table th { background-color: #e9ecef; }

.high-gc { color: #e67e22; font-weight: bold; }
.has-spill { color: #e74c3c; font-weight: bold; }
</style>
