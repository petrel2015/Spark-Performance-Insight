<template>
  <div class="job-timeline-container">
    <div class="chart-header">
      <div class="title">Event Timeline</div>
      <div class="actions">
        <button class="lock-btn" @click="toggleZoomLock">
          {{ isZoomLocked ? '🔒 Locked' : '🔓 Unlocked' }}
        </button>
      </div>
    </div>
    <div ref="chartDom" class="timeline-chart"></div>
    <div v-if="isLoading" class="loading-overlay">
      <div class="spinner"></div>
      <span>Loading Timeline...</span>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, onBeforeUnmount, nextTick} from 'vue';
import * as echarts from 'echarts';
import {getAppExecutors, getJobStages} from '../../api';
import {formatTime} from '../../utils/format';

const props = defineProps({
  appId: {type: String, required: true},
  jobId: {type: Number, required: true}
});

const chartDom = ref(null);
const isLoading = ref(false);
const isZoomLocked = ref(true);
let myChart = null;
let resizeObserver = null;

const toggleZoomLock = () => {
  isZoomLocked.value = !isZoomLocked.value;
  updateZoomState();
};

const updateZoomState = () => {
  if (!myChart) return;
  const option = {
    dataZoom: [
      {type: 'slider', xAxisIndex: [0, 1], zoomLock: isZoomLocked.value, brushSelect: !isZoomLocked.value},
      {type: 'inside', xAxisIndex: [0, 1], disabled: isZoomLocked.value}
    ]
  };
  myChart.setOption(option);
};

const fetchDataAndRender = async () => {
  if (!props.appId || !props.jobId) return;

  isLoading.value = true;
  try {
    const [execRes, stagesRes] = await Promise.all([
      getAppExecutors(props.appId),
      getJobStages(props.appId, props.jobId)
    ]);

    const allExecutors = execRes.data || [];
    const stages = stagesRes.data || [];

    if (stages.length === 0) {
      isLoading.value = false;
      return;
    }

    // Determine Job Time Window based on Stages
    // (Job object also has start/end, but stages give precise bounds for the bars)
    const stageStarts = stages.map(s => new Date(s.submissionTime).getTime()).filter(t => t > 0);
    const stageEnds = stages.map(s => new Date(s.completionTime).getTime()).filter(t => t > 0);

    // Default to current time if running
    const now = Date.now();

    let minTime = stageStarts.length > 0 ? Math.min(...stageStarts) : 0;
    let maxTime = stageEnds.length > 0 ? Math.max(...stageEnds) : 0;

    if (maxTime === 0 && minTime > 0) maxTime = now;
    // Buffer
    minTime -= 1000;
    maxTime += 1000;

    // Filter Executors overlapping with [minTime, maxTime]
    const relevantExecutors = allExecutors.filter(e => {
      const add = new Date(e.addTime).getTime();
      const remove = e.removeTime ? new Date(e.removeTime).getTime() : now;
      return add <= maxTime && remove >= minTime;
    }).sort((a, b) => {
      // Sort by add time
      return new Date(a.addTime).getTime() - new Date(b.addTime).getTime();
    });

    const sortedStages = [...stages].sort((a, b) => a.stageId - b.stageId);

    renderChart(relevantExecutors, sortedStages, minTime, maxTime);

  } catch (err) {
    console.error("Failed to fetch timeline data", err);
  } finally {
    isLoading.value = false;
  }
};

const renderChart = (executors, stages, minTime, maxTime) => {
  if (!chartDom.value) return;
  if (!myChart) myChart = echarts.init(chartDom.value);

  // --- Prepare Data ---

  // 1. Executor Series
  // Y-axis: Executor IDs
  const executorYData = executors.map(e => e.executorId);
  const executorSeriesData = executors.map((e, index) => {
    const start = new Date(e.addTime).getTime();
    const end = e.removeTime ? new Date(e.removeTime).getTime() : maxTime; // Clamp to view or extend
    return {
      name: e.executorId,
      value: [index, start, end, e] // index mapped to Y
    };
  });

  // 2. Stage Series
  // Y-axis: Stage IDs
  const stageYData = sortedStages.map(s => `Stage ${s.stageId}`);
  const stageSeriesData = sortedStages.map((s, index) => {
    const start = new Date(s.submissionTime).getTime();
    const end = s.completionTime ? new Date(s.completionTime).getTime() : maxTime;
    return {
      name: `Stage ${s.stageId}`,
      value: [index, start, end, s]
    };
  });

  // --- Layout Calculation ---
  const execHeight = Math.max(150, executors.length * 30);
  const stageHeight = Math.max(150, stages.length * 30);
  const totalHeight = execHeight + stageHeight + 100; // padding

  chartDom.value.style.height = `${totalHeight}px`;
  myChart.resize();

  // --- Options ---
  const option = {
    tooltip: {
      formatter: (params) => {
        const v = params.value;
        if (params.seriesIndex === 0) {
          // Executor
          const e = v[3];
          return `<b>Executor ${e.executorId}</b><br/>
                  Host: ${e.host}<br/>
                  Add: ${formatTime(v[1])}<br/>
                  Remove: ${e.removeTime ? formatTime(v[2]) : 'Active'}`;
        } else {
          // Stage
          const s = v[3];
          return `<b>Stage ${s.stageId}</b> (${s.attemptId})<br/>
                  Name: ${s.stageName}<br/>
                  Start: ${formatTime(v[1])}<br/>
                  End: ${s.completionTime ? formatTime(v[2]) : 'Running'}<br/>
                  Duration: ${s.duration ? (s.duration / 1000).toFixed(1) + 's' : '-'}`;
        }
      }
    },
    grid: [
      {left: 100, right: 50, top: 30, height: execHeight, tooltip: {trigger: 'item'}}, // Top: Executors
      {left: 100, right: 50, top: 30 + execHeight + 40, height: stageHeight, tooltip: {trigger: 'item'}}  // Bottom: Stages
    ],
    xAxis: [
      {
        type: 'time',
        gridIndex: 0,
        min: minTime,
        max: maxTime,
        axisLabel: {show: false}, // Hide label for top chart to reduce clutter
        splitLine: {show: true, lineStyle: {type: 'dashed'}}
      },
      {
        type: 'time',
        gridIndex: 1,
        min: minTime,
        max: maxTime,
        position: 'bottom',
        splitLine: {show: true, lineStyle: {type: 'dashed'}}
      }
    ],
    yAxis: [
      {
        type: 'category',
        gridIndex: 0,
        data: executorYData,
        inverse: true,
        name: 'Executors',
        nameLocation: 'start',
        nameGap: 10,
        nameTextStyle: {fontWeight: 'bold'}
      },
      {
        type: 'category',
        gridIndex: 1,
        data: stageYData,
        inverse: true,
        name: 'Stages',
        nameLocation: 'start',
        nameGap: 10,
        nameTextStyle: {fontWeight: 'bold'}
      }
    ],
    dataZoom: [
      {
        type: 'slider',
        xAxisIndex: [0, 1],
        bottom: 10,
        height: 20,
        zoomLock: isZoomLocked.value,
        brushSelect: !isZoomLocked.value
      },
      {
        type: 'inside',
        xAxisIndex: [0, 1],
        disabled: isZoomLocked.value
      }
    ],
    series: [
      {
        name: 'Executors',
        type: 'custom',
        xAxisIndex: 0,
        yAxisIndex: 0,
        renderItem: renderGanttItem,
        itemStyle: {
          color: '#73c0de',
          opacity: 0.8
        },
        encode: {
          x: [1, 2],
          y: 0
        },
        data: executorSeriesData
      },
      {
        name: 'Stages',
        type: 'custom',
        xAxisIndex: 1,
        yAxisIndex: 1,
        renderItem: renderGanttItem,
        itemStyle: {
          color: '#fac858',
          opacity: 0.8
        },
        encode: {
          x: [1, 2],
          y: 0
        },
        data: stageSeriesData
      }
    ]
  };

  myChart.setOption(option);
};

function renderGanttItem(params, api) {
  const categoryIndex = api.value(0);
  const start = api.value(1);
  const end = api.value(2);

  const startCoord = api.coord([start, categoryIndex]);
  const endCoord = api.coord([end, categoryIndex]);

  const height = api.size([0, 1])[1] * 0.6;
  const width = endCoord[0] - startCoord[0];

  return {
    type: 'rect',
    shape: {
      x: startCoord[0],
      y: startCoord[1] - height / 2,
      width: width,
      height: height
    },
    style: api.style()
  };
}

onMounted(() => {
  fetchDataAndRender();
  resizeObserver = new ResizeObserver(() => {
    myChart?.resize();
  });
  if (chartDom.value) resizeObserver.observe(chartDom.value);
});

watch(() => props.jobId, fetchDataAndRender);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  myChart?.dispose();
});
</script>

<style scoped>
.job-timeline-container {
  width: 100%;
  border: 1px solid #eee;
  border-radius: 8px;
  background: white;
  position: relative;
  overflow: hidden;
  padding: 15px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-weight: bold;
  color: #666;
  font-size: 0.9rem;
}

.lock-btn {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.7rem;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
}

.lock-btn:hover {
  background: #f0f0f0;
  color: #333;
}

.timeline-chart {
  width: 100%;
  min-height: 200px;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 10;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>
