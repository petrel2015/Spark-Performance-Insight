<template>
  <div class="overview-grid">
    <div class="overview-item">
      <span class="label">Status:</span>
      <span :class="'status-badge status-' + (stage.status || 'UNKNOWN').toLowerCase()">
        {{ stage.status || 'UNKNOWN' }}
      </span>
    </div>
    <div class="overview-item">
      <span class="label">Submission Time:</span>
      <span>{{ formatDateTime(stage.submissionTime) }}</span>
    </div>
    <div class="overview-item">
      <span class="label">Duration:</span>
      <span>{{ commonFormatTime(stage.tasksDurationSum) }}</span>
    </div>
    <div class="overview-item">
      <span class="label">Input:</span>
      <span>{{ formatBytes(stage.inputBytes) }} / {{ formatNum(stage.inputRecords) }}</span>
    </div>
    <div class="overview-item">
      <span class="label">Output:</span>
      <span>{{ formatBytes(stage.outputBytes) }}</span>
    </div>
    <div class="overview-item">
      <span class="label">Shuffle Read:</span>
      <span>{{ formatBytes(stage.shuffleReadBytes) }} / {{ formatNum(stage.shuffleReadRecords) }}</span>
    </div>
    <div class="overview-item">
      <span class="label">Shuffle Write:</span>
      <span>{{ formatBytes(stage.shuffleWriteBytes) }} / {{ formatNum(stage.shuffleWriteRecords) }}</span>
    </div>
  </div>
</template>

<script setup>
import { formatTime as commonFormatTime, formatBytes, formatNum } from '../../utils/format';

defineProps({
  stage: {
    type: Object,
    required: true
  }
});

const formatDateTime = (t) => t ? new Date(t).toLocaleString() : '-';
</script>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: #fcfcfc;
  border-radius: 4px;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.overview-item:hover {
  background: #f7fbff;
  border-color: #d6eaff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  transform: translateY(-1px);
}

.overview-item .label {
  font-size: 0.75rem;
  color: #888;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.overview-item span:not(.label) {
  font-size: 0.9rem;
  color: #333;
  font-weight: 500;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  text-transform: uppercase;
  font-weight: bold;
  display: inline-block;
  align-self: flex-start;
}

.status-succeeded, .status-complete { background: #e8f5e9; color: #2e7d32; }
.status-failed { background: #ffebee; color: #c62828; }
.status-running { background: #fff3e0; color: #ef6c00; }
.status-skipped { background: #f5f5f5; color: #757575; }
</style>
