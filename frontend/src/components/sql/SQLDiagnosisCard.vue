<template>
  <div class="sql-diagnosis-card">
    <div class="score-section">
      <div class="score-circle tooltip-container" :class="scoreClass">
        <span class="score-value">{{ Math.round(performanceScore || 0) }}</span>
        <span class="score-label">Health</span>
        <div class="tooltip-text score-tooltip">
          <strong>SQL Health Score:</strong><br/>
          Weighted average of all associated Job health scores based on duration.<br/>
          <em>Higher score is better.</em>
        </div>
      </div>
      <div class="score-desc">
        SQL Health Score
        <small>Weighted evaluation</small>
      </div>
    </div>

    <div class="top-jobs-list" v-if="jobs.length > 0">
      <div class="list-title">Top Jobs (Sorted by Health Risk)</div>
      <div v-for="job in sortedJobs" :key="job.jobId" class="job-item" @click="$emit('view-job', job.jobId)">
        <div class="job-header">
          <div class="job-name-info">
            <span class="job-name">Job {{ job.jobId }}</span>
            <small class="job-duration" v-if="job.duration">{{ formatTime(job.duration) }}</small>
          </div>
          <span class="job-score" :class="getScoreClass(job.performanceScore)">
            {{ Math.round(job.performanceScore || 0) }}
          </span>
        </div>
        <div class="job-bar-bg">
          <div class="job-bar-fill" 
               :style="{ width: Math.max(2, Math.min(100, job.performanceScore || 0)) + '%', backgroundColor: getBarColor(job.performanceScore || 0) }">
          </div>
        </div>
      </div>
    </div>
    <div v-else class="no-issues">
      No associated jobs info available for diagnosis.
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { formatTime } from '../../utils/format';

const props = defineProps({
  performanceScore: { type: Number, default: 0 },
  jobs: { type: Array, default: () => [] }
});

const emit = defineEmits(['view-job']);

const sortedJobs = computed(() => {
  return [...props.jobs].sort((a, b) => {
    // Primary sort: Score ascending (show least healthy first)
    const scoreDiff = (a.performanceScore || 0) - (b.performanceScore || 0);
    if (Math.abs(scoreDiff) > 0.1) return scoreDiff;
    return (b.duration || 0) - (a.duration || 0);
  }).slice(0, 5);
});

const scoreClass = computed(() => {
  const s = props.performanceScore;
  if (s < 40) return 'critical';
  if (s < 80) return 'warning';
  return 'good';
});

const getScoreClass = (score) => {
  if (score < 40) return 'critical';
  if (score < 80) return 'warning';
  return 'good';
};

const getBarColor = (score) => {
  if (score < 40) return '#e74c3c';
  if (score < 80) return '#f39c12';
  return '#2ecc71';
};
</script>

<style scoped>
.sql-diagnosis-card {
  display: flex;
  gap: 2rem;
  padding: 10px;
  align-items: flex-start;
  background: white;
  border-radius: 8px;
  width: 100%;
}

.score-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  min-width: 140px;
}

.score-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border: 4px solid #eee;
  background: #fff;
}

.score-circle.good { border-color: #2ecc71; color: #2ecc71; }
.score-circle.warning { border-color: #f39c12; color: #f39c12; }
.score-circle.critical { border-color: #e74c3c; color: #e74c3c; }

.score-value {
  font-size: 1.8rem;
  font-weight: bold;
  line-height: 1;
}

.score-label {
  font-size: 0.7rem;
  text-transform: uppercase;
  font-weight: 600;
  opacity: 0.8;
}

.score-desc {
  text-align: center;
  font-size: 0.85rem;
  color: #666;
  display: flex;
  flex-direction: column;
}

.score-desc small {
  font-size: 0.7rem;
  color: #999;
}

.top-jobs-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-left: 1px solid #eee;
  padding-left: 2rem;
}

.list-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #666;
  margin-bottom: 4px;
}

.job-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 6px;
  transition: background 0.2s;
}

.job-item:hover {
  background: #f9f9f9;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  color: #444;
}

.job-name-info {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.job-name {
  font-weight: 500;
  color: #3498db;
}

.job-duration {
  color: #999;
  font-size: 0.75rem;
}

.job-score {
  font-weight: bold;
  font-family: 'Courier New', monospace;
}

.job-score.critical { color: #e74c3c; }
.job-score.warning { color: #f39c12; }
.job-score.good { color: #2ecc71; }

.job-bar-bg {
  height: 6px;
  background: #eee;
  border-radius: 3px;
  overflow: hidden;
}

.job-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

/* Tooltip Styles */
.tooltip-container {
  position: relative;
  cursor: help;
}

.tooltip-text {
  visibility: hidden;
  width: 240px;
  background-color: #333;
  color: #fff;
  text-align: left;
  border-radius: 6px;
  padding: 10px 14px;
  position: absolute;
  z-index: 9999;
  opacity: 0;
  transition: opacity 0.2s;
  font-size: 0.75rem;
  line-height: 1.5;
  font-weight: normal;
  pointer-events: none;
  box-shadow: 0 4px 15px rgba(0,0,0,0.3);
}

.tooltip-container:hover .tooltip-text {
  visibility: visible;
  opacity: 1;
}

.score-tooltip {
  left: 50%;
  transform: translateX(-50%);
  bottom: 110%;
}

.score-tooltip::after {
  content: "";
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border-width: 6px;
  border-style: solid;
  border-color: #333 transparent transparent transparent;
}

.no-issues {
  color: #999;
  font-style: italic;
  padding: 20px;
}
</style>