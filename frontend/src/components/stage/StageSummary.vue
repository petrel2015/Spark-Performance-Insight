<template>
  <div class="summary-card full-width metrics-matrix-card">
    <h4>Summary Metrics for Stage {{ stageId }}</h4>
    <div class="matrix-wrapper">
      <table class="matrix-table">
        <thead>
          <tr>
            <th>Metric</th>
            <th>Min</th>
            <th>25th Pct</th>
            <th>Median</th>
            <th>75th Pct</th>
            <th>95th Pct</th>
            <th>Max</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in displayRows" :key="row.label">
            <td class="metric-label">{{ row.label }}</td>
            <td v-for="key in ['minValue', 'p25', 'p50', 'p75', 'p95', 'maxValue']" :key="key">
              {{ formatCell(row, key) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  stats: {
    type: Array,
    default: () => []
  },
  stageId: [Number, String]
});

const formatTime = (ms) => {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1) return '0 ms'; // Round down small values
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(1)} s`;
  const m = Math.floor(s / 60);
  const rs = Math.round(s % 60);
  return `${m} m ${rs} s`;
};

const formatBytes = (bytes) => {
  if (bytes === null || bytes === undefined) return '-';
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

const formatNum = (num) => {
  if (num === null || num === undefined) return '-';
  if (num >= 1000000) return (num / 1000000).toFixed(1) + ' M';
  if (num >= 1000) return (num / 1000).toFixed(1) + ' K';
  return num.toString();
};

const getStat = (name) => props.stats.find(s => s.metricName === name);

const displayRows = computed(() => {
  const rows = [];
  
  const addRow = (name, label, type) => {
    const s = getStat(name);
    rows.push({ label, type, data: s || {} });
  };

  const addComposite = (nameBytes, nameRecords, label) => {
    rows.push({
      label,
      type: 'composite',
      bytes: getStat(nameBytes) || {},
      records: getStat(nameRecords) || {}
    });
  };

  addRow('task_deserialization_time', 'Task Deserialization Time', 'time');
  addRow('duration', 'Duration', 'time');
  addRow('gc_time', 'GC Time', 'time');
  addRow('result_serialization_time', 'Result Serialization Time', 'time');
  addRow('getting_result_time', 'Getting Result Time', 'time');
  addRow('scheduler_delay', 'Scheduler Delay', 'time');
  addRow('peak_execution_memory', 'Peak Execution Memory', 'bytes');
  addRow('memory_spill', 'Spill (memory)', 'bytes');
  addRow('disk_spill', 'Spill (disk)', 'bytes');
  
  addComposite('input_bytes', 'input_records', 'Input Size / Records');
  addComposite('shuffle_write', 'shuffle_write_records', 'Shuffle Write Size / Records');
  
  addRow('shuffle_write_time', 'Shuffle Write Time', 'nanos');

  return rows;
});

const formatCell = (row, key) => {
  if (row.type === 'composite') {
    const b = row.bytes[key];
    const r = row.records[key];
    if (b === undefined && r === undefined) return '-';
    return `${formatBytes(b || 0)} / ${formatNum(r || 0)}`;
  }
  
  const val = row.data[key];
  if (val === undefined || val === null) return '-';
  
  if (row.type === 'time') return formatTime(val);
  if (row.type === 'nanos') return formatTime(val / 1000000);
  if (row.type === 'bytes') return formatBytes(val);
  return val;
};
</script>

<style scoped>
.summary-card {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  margin-bottom: 1.5rem;
}

.summary-card h4 {
  margin: 0 0 10px 0;
  font-size: 0.95rem;
  color: #2c3e50;
  border-bottom: 1px solid #f1f1f1;
  padding-bottom: 6px;
}

.matrix-wrapper {
  overflow-x: auto;
  margin-top: 10px;
}

.matrix-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.matrix-table th, .matrix-table td {
  padding: 12px 8px;
  text-align: right;
  border-bottom: 1px solid #eee;
}

.matrix-table tbody tr:hover {
  background-color: #f7fbff;
}

.matrix-table tbody tr:hover .metric-label {
  background-color: #f0f7ff;
  color: #3498db;
}

.matrix-table th {
  background: #f8f9fa;
  color: #333;
  font-weight: 600;
}

.matrix-table th:first-child {
  text-align: left;
}

.matrix-table .metric-label {
  text-align: left;
  font-weight: bold;
  color: #2c3e50;
  background: #fdfdfd;
}
</style>
