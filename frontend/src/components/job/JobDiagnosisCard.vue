<template>
  <div class="job-diagnosis-card">
    <div class="score-section">
      <div class="score-circle tooltip-container" :class="scoreClass">
        <span class="score-value">{{ Math.round(performanceScore || 0) }}</span>
        <span class="score-label">Health</span>
        <div class="tooltip-text score-tooltip">
          <strong>Job Health Score:</strong><br/>
          Weighted average of all Stage health scores based on duration.<br/>
          <em>Higher score is better.</em>
        </div>
      </div>
      <div class="score-desc">
        Job Health Score
        <small>Aggregated from {{ stages.length }} stages</small>
      </div>
    </div>

    <div class="top-stages-list" v-if="stages.length > 0">
      <div class="list-title">Top Stages (Sorted by Health Risk)</div>
      <div v-for="stage in topStages" :key="stage.stageId" class="stage-item" @click="$emit('view-stage', stage.stageId)">
        <div class="stage-header">
          <div class="stage-name-info">
            <span class="stage-name">Stage {{ stage.stageId }}</span>
            <small class="stage-duration">{{ formatTime(stage.duration) }}</small>
          </div>
          <span class="stage-score" :class="getScoreClass(stage.performanceScore)">
            {{ Math.round(stage.performanceScore || 0) }}
          </span>
        </div>
        <div class="stage-bar-bg">
          <div class="stage-bar-fill" 
               :style="{ width: Math.max(2, Math.min(100, stage.performanceScore || 0)) + '%', backgroundColor: getBarColor(stage.performanceScore || 0) }">
          </div>
        </div>
      </div>
    </div>
    <div v-else class="no-issues">
      No stages data available for this job.
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { formatTime } from '../../utils/format';

const props = defineProps({
  performanceScore: { type: Number, default: 0 },
  stages: { type: Array, default: () => [] }
});

const emit = defineEmits(['view-stage']);

import { onMounted, watch } from 'vue';
onMounted(() => {
  console.log("JobDiagnosisCard Mounted. Score:", props.performanceScore, "Stages:", props.stages.length);
});
watch(() => props.stages, (newVal) => {
  console.log("JobDiagnosisCard Stages Updated. Count:", newVal.length);
}, { deep: true });

const topStages = computed(() => {
  return [...props.stages]
      .sort((a, b) => {
        // Primary sort: Score ascending (show least healthy first)
        const scoreDiff = (a.performanceScore || 0) - (b.performanceScore || 0);
        if (Math.abs(scoreDiff) > 0.1) return scoreDiff;
        // Secondary sort: Duration descending
        return (b.duration || 0) - (a.duration || 0);
      })
      .slice(0, 5);
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
.job-diagnosis-card {
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
  min-width: 120px;
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

.top-stages-list {
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

.stage-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 6px;
  transition: background 0.2s;
}

.stage-item:hover {
  background: #f9f9f9;
}

.stage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  color: #444;
}

.stage-name-info {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.stage-name {
  font-weight: 500;
  color: #3498db;
}

.stage-duration {
  color: #999;
  font-size: 0.75rem;
}

.stage-score {
  font-weight: bold;
  font-family: 'Courier New', monospace;
}

.stage-score.critical { color: #e74c3c; }
.stage-score.warning { color: #f39c12; }
.stage-score.good { color: #2ecc71; }

.stage-bar-bg {
  height: 6px;
  background: #eee;
  border-radius: 3px;
  overflow: hidden;
}

.stage-bar-fill {
  height: 100%;
  border-radius: 3px;
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
