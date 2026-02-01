<template>
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
</template>

<script setup>
import { computed } from 'vue';
import { formatTime, formatBytes, formatNum } from '../../utils/format';

const props = defineProps({
  stats: {
    type: Array,
    default: () => []
  },
  stageId: [Number, String],
  visibleMetrics: {
    type: Array,
    default: () => []
  }
});

const getStat = (name) => props.stats.find(s => s.metricName === name);

const displayRows = computed(() => {
  const allRows = [];
  
  const addRow = (key, label, type) => {
    if (!props.visibleMetrics.includes(key)) return;
    const s = getStat(key);
    allRows.push({ label, type, data: s || {} });
  };

  const addComposite = (key, nameBytes, nameRecords, label) => {
    if (!props.visibleMetrics.includes(key)) return;
    allRows.push({
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
  
  addComposite('input', 'input_bytes', 'input_records', 'Input Size / Records');
  addComposite('shuffle_write', 'shuffle_write_bytes', 'shuffle_write_records', 'Shuffle Write Size / Records');
  
  addRow('shuffle_write_time', 'Shuffle Write Time', 'nanos');

  return allRows;
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
.matrix-wrapper {
  overflow-x: auto;
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
