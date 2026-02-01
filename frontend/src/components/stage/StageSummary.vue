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
          <tr v-for="stat in stats" :key="stat.metricName">
            <td class="metric-label">{{ formatMetricName(stat.metricName) }}</td>
            <td>{{ formatMetricValue(stat.metricName, stat.minValue) }}</td>
            <td>{{ formatMetricValue(stat.metricName, stat.p25) }}</td>
            <td>{{ formatMetricValue(stat.metricName, stat.p50) }}</td>
            <td>{{ formatMetricValue(stat.metricName, stat.p75) }}</td>
            <td>{{ formatMetricValue(stat.metricName, stat.p95) }}</td>
            <td>{{ formatMetricValue(stat.metricName, stat.maxValue) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
defineProps({
  stats: {
    type: Array,
    default: () => []
  },
  stageId: [Number, String]
});

const formatMetricName = (name) => {
  const map = {
    'duration': 'Duration',
    'gc_time': 'GC Time',
    'memory_spill': 'Memory Spill',
    'disk_spill': 'Disk Spill',
    'shuffle_read': 'Shuffle Read Size',
    'shuffle_read_records': 'Shuffle Read Records',
    'shuffle_write': 'Shuffle Write Size',
    'shuffle_write_records': 'Shuffle Write Records'
  };
  return map[name] || name;
};

const formatMetricValue = (metric, val) => {
  if (val === null || val === undefined) return '-';
  if (metric.endsWith('_records')) return val.toLocaleString();
  if (metric === 'duration' || metric === 'gc_time') {
    if (val < 1000) return `${val} ms`;
    return `${(val / 1000).toFixed(1)} s`;
  }
  return formatBytes(val);
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
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
