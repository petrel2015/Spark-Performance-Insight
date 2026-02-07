<template>
  <div class="diagnosis-card-container">
    <div class="score-section">
      <div class="score-circle tooltip-container" :class="scoreClass">
        <span class="score-value">{{ Math.round(performanceScore || 0) }}</span>
        <span class="score-label">Score</span>
        <div class="tooltip-text score-tooltip">
          <strong>Weight Distribution:</strong><br/>
          • GC, Shuffle (Read/Write), Skew, Spill: 15% each<br/>
          • I/O Wait: 10%<br/>
          • Delay, Serialization, Result: 5% each<br/>
          <em>Higher score indicates more severe performance issues.</em>
        </div>
      </div>
      <div class="score-desc">
        Performance Impact Score
        <small>Weighted evaluation of bottleneck factors</small>
      </div>
    </div>

    <div class="dimensions-list">
      <div v-for="item in topDimensions" :key="item.dimension" class="dimension-item">
        <div class="dim-header tooltip-container">
          <div class="dim-name-wrapper">
            <span class="dim-name">{{ item.dimension }}</span>
            <span class="material-symbols-outlined help-icon">help</span>
          </div>
          <div class="dim-status-group">
            <span class="dim-score">{{ item.score }}</span>
            <span class="status-dot" :style="{ backgroundColor: getBarColor(item.score) }"></span>
          </div>
          <!-- Tooltip moved to be a direct child of the relative-positioned container -->
          <div class="tooltip-text">{{ getTooltip(item.dimension) }}</div>
        </div>
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
  const allPossibleDimensions = [
    'GC Impact',
    'Shuffle Write Impact',
    'Shuffle Read Blocked',
    'Serialization Impact',
    'Scheduler Delay Impact',
    'I/O Wait',
    'Result Fetching',
    'Data Skew',
    'Disk Spill'
  ];

  try {
    const items = JSON.parse(props.diagnosisInfo || '[]');
    const itemMap = new Map(items.map(i => [i.dimension, i.score]));
    
    // Merge all possible dimensions with actual scores
    const merged = allPossibleDimensions.map(dim => ({
      dimension: dim,
      score: itemMap.has(dim) ? itemMap.get(dim) : 0
    }));

    // Sort descending by score
    return merged.sort((a, b) => b.score - a.score);
  } catch (e) {
    return allPossibleDimensions.map(dim => ({ dimension: dim, score: 0 }));
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

const tooltips = {
  'GC Impact': 'Percentage of total task duration spent on Garbage Collection. High score (>10) indicates memory pressure. Lower is better.',
  'Shuffle Write Impact': 'Percentage of total task duration spent writing shuffle data. High score (>20) suggests I/O bottlenecks. Lower is better.',
  'Shuffle Read Blocked': 'Percentage of time tasks are blocked waiting for shuffle data from remote executors. High score means network issues or upstream slowness.',
  'I/O Wait': 'Percentage of execution time not spent on CPU. Formula: 1 - (CPU Time / Run Time). High score (>80) indicates tasks are bottlenecked by reading/writing to storage (HDFS/S3).',
  'Result Fetching': 'Percentage of time spent sending results back to Driver. High score implies "collect()" is fetching too much data, risking Driver OOM.',
  'Serialization Impact': 'Percentage of time spent serializing/deserializing data. High score (>10) means CPU is busy converting data formats. Lower is better.',
  'Scheduler Delay Impact': 'Percentage of time waiting for task scheduling. High score (>20) implies cluster or driver overload. Lower is better.',
  'Data Skew': 'Skew severity based on (Max Duration / Median Duration). Score 0-100. High score indicates uneven work distribution. Lower is better.',
  'Disk Spill': 'Indicates if data spilled from memory to disk (Score 100 if spilled). Causes severe slowdowns. Ideally should be 0.'
};

const getTooltip = (dimension) => tooltips[dimension] || 'Performance impact score based on resource usage ratio.';
</script>

<style scoped>
.diagnosis-card-container {
  display: flex;
  gap: 2rem;
  padding: 10px;
  align-items: flex-start;
}

.help-icon {
  font-size: 14px;
  vertical-align: middle;
  color: #999;
  cursor: help;
  margin-left: 4px;
}

.tooltip-container {
  position: relative;
  display: inline-flex;
  align-items: center;
  cursor: help;
}

.tooltip-text {
  visibility: hidden;
  width: 260px;
  background-color: #333;
  color: #fff;
  text-align: left;
  border-radius: 6px;
  padding: 10px 14px;
  position: absolute;
  z-index: 9999;
  top: 110%;
  left: 50%;
  transform: translateX(-50%);
  opacity: 0;
  transition: opacity 0.2s;
  font-size: 0.75rem;
  line-height: 1.5;
  font-weight: normal;
  pointer-events: none;
  box-shadow: 0 4px 15px rgba(0,0,0,0.3);
}

.tooltip-text::after {
  content: "";
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  border-width: 6px;
  border-style: solid;
  border-color: transparent transparent #333 transparent;
}

.tooltip-container:hover .tooltip-text {
  visibility: visible;
  opacity: 1;
}

.score-tooltip {
  left: 50%;
  transform: translateX(-50%);
  width: 220px;
  bottom: 110%;
  top: auto;
}

.score-tooltip::after {
  top: 100%;
  bottom: auto;
  left: 50%;
  transform: translateX(-50%);
  border-color: #333 transparent transparent transparent;
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
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 12px;
  padding-left: 1rem;
}

@media (max-width: 800px) {
  .diagnosis-card-container {
    flex-direction: column;
    align-items: center;
  }
  .dimensions-list {
    border-left: none;
    padding-left: 0;
    width: 100%;
    margin-top: 1.5rem;
    border-top: 1px solid #eee;
    padding-top: 1.5rem;
  }
}

.dimension-item {
  display: flex;
  flex-direction: column;
  background: #fcfcfc;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
  transition: transform 0.2s, box-shadow 0.2s;
}

.dimension-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.05);
  background: #fff;
}

.dim-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8rem;
  font-weight: 600;
  color: #444;
  height: 24px;
}

.dim-name-wrapper {
  display: flex;
  align-items: center;
  overflow: hidden;
  flex: 1;
}

.dim-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dim-status-group {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
}

.dim-score {
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9rem;
  min-width: 30px;
  text-align: right;
  color: #666;
}

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: inset 0 0 2px rgba(0,0,0,0.2);
  transition: all 0.3s;
}

.no-issues {
  font-style: italic;
  color: #999;
  padding: 20px;
  text-align: center;
}
</style>
