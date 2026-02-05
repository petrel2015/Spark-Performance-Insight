<template>
  <div class="diagnosis-card-container">
    <div class="score-section">
      <div class="score-circle" :class="scoreClass">
        <span class="score-value">{{ Math.round(performanceScore || 0) }}</span>
        <span class="score-label">Score</span>
      </div>
      <div class="score-desc">
        Performance Impact Score
        <small>Weighted evaluation of bottleneck factors</small>
      </div>
    </div>

    <div class="dimensions-list">
      <div v-for="item in topDimensions" :key="item.dimension" class="dimension-item">
        <div class="dim-header">
          <span class="dim-name">{{ item.dimension }}</span>
          <span class="dim-score">{{ item.score }}</span>
        </div>
        <div class="dim-bar-bg">
          <div class="dim-bar-fill" 
               :style="{ width: Math.min(100, item.score) + '%', backgroundColor: getBarColor(item.score) }">
          </div>
        </div>
      </div>
      <div v-if="topDimensions.length === 0" class="no-issues">
        No significant performance bottlenecks detected.
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  diagnosisInfo: { type: String, default: '[]' },
  performanceScore: { type: Number, default: 0 }
});

const topDimensions = computed(() => {
  try {
    const items = JSON.parse(props.diagnosisInfo || '[]');
    // Filter out 0 scores and sort descending
    return items.filter(i => i.score > 0).sort((a, b) => b.score - a.score).slice(0, 10);
  } catch (e) {
    return [];
  }
});

const scoreClass = computed(() => {
  const s = props.performanceScore;
  if (s > 50) return 'critical';
  if (s > 20) return 'warning';
  return 'good';
});

const getBarColor = (score) => {
  if (score > 50) return '#e74c3c';
  if (score > 20) return '#f39c12';
  return '#2ecc71';
};
</script>

<style scoped>
.diagnosis-card-container {
  display: flex;
  gap: 2rem;
  padding: 10px;
  align-items: flex-start;
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

.dimensions-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dimension-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dim-header {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  font-weight: 600;
  color: #444;
}

.dim-bar-bg {
  height: 8px;
  background: #eee;
  border-radius: 4px;
  overflow: hidden;
}

.dim-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.no-issues {
  font-style: italic;
  color: #999;
  padding: 20px;
  text-align: center;
}
</style>
