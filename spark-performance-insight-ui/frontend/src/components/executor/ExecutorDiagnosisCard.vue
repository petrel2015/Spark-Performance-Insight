<template>
  <div v-if="anomalies && anomalies.length > 0" class="diagnosis-card">
    <div class="diagnosis-header">
      <span class="material-symbols-outlined icon">warning</span>
      <span class="title">Executor 性能诊断告警</span>
    </div>
    <div class="anomaly-list">
      <div v-for="(anomaly, index) in anomalies" :key="index" class="anomaly-item">
        <div class="anomaly-main">
          <span class="executor-id">Executor {{ anomaly.executorId }}</span>
          <span class="anomaly-reason">{{ anomaly.reason }}</span>
        </div>
        <div class="anomaly-details">
          <div class="detail-item">
            <span class="label">当前值:</span>
            <span class="value">{{ formatValue(anomaly.value, anomaly.metric) }}</span>
          </div>
          <div class="detail-item">
            <span class="label">集群中位数:</span>
            <span class="value">{{ formatValue(anomaly.median, anomaly.metric) }}</span>
          </div>
          <div class="detail-item">
            <span class="label">比例:</span>
            <span class="value ratio" :class="{ critical: anomaly.ratio < 0.4 }">
              {{ (anomaly.ratio * 100).toFixed(1) }}%
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatBytes, formatNum } from '../../utils/format';

const props = defineProps({
  anomalies: { type: Array, default: () => [] }
});

const formatValue = (val, metric) => {
  if (metric === 'Task Throughput') {
    return formatNum(val) + ' tasks/s';
  } else {
    return formatBytes(val) + '/s';
  }
};
</script>

<style scoped>
.diagnosis-card {
  background: #fff5f5;
  border: 1px solid #feb2b2;
  border-radius: 8px;
  padding: 1rem 1.5rem;
  margin-bottom: 1.5rem;
}

.diagnosis-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 1rem;
  color: #c53030;
}

.diagnosis-header .icon {
  font-size: 24px;
}

.diagnosis-header .title {
  font-weight: 700;
  font-size: 1rem;
}

.anomaly-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.anomaly-item {
  background: white;
  border: 1px solid #fed7d7;
  border-radius: 6px;
  padding: 10px 15px;
}

.anomaly-main {
  margin-bottom: 8px;
}

.executor-id {
  font-weight: 600;
  color: #2d3748;
  margin-right: 12px;
  background: #edf2f7;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.85rem;
}

.anomaly-reason {
  color: #4a5568;
  font-size: 0.9rem;
}

.anomaly-details {
  display: flex;
  gap: 20px;
  font-size: 0.8rem;
  color: #718096;
  border-top: 1px dashed #edf2f7;
  padding-top: 8px;
}

.detail-item {
  display: flex;
  gap: 6px;
}

.detail-item .value {
  color: #2d3748;
  font-weight: 600;
}

.detail-item .ratio {
  color: #e53e3e;
}

.detail-item .ratio.critical {
  font-weight: 800;
  text-decoration: underline;
}
</style>
