<template>
  <div class="job-timeline-container">
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

defineExpose({
  isZoomLocked,
  toggleZoomLock
});

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

    console.log("Timeline Data Fetched:", { executors: allExecutors.length, stages: stages.length });

    if (stages.length === 0) {
      console.warn("No stages found for job:", props.jobId);
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

    console.log("Timeline Window:", { minTime, maxTime, startCount: stageStarts.length, endCount: stageEnds.length });

    // Filter Executors overlapping with [minTime, maxTime]
    const relevantExecutors = allExecutors.filter(e => {
      const add = new Date(e.addTime).getTime();
      const remove = e.removeTime ? new Date(e.removeTime).getTime() : now;
      return add <= maxTime && remove >= minTime;
    }).sort((a, b) => {
      // Sort by add time
      return new Date(a.addTime).getTime() - new Date(b.addTime).getTime();
    });

    console.log("Relevant Executors:", relevantExecutors.length);

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

  // 1. Executor Events
  // Y-axis: Executor IDs
  const executorYData = executors.map(e => e.executorId);
  const executorSeriesData = [];
  executors.forEach((e, index) => {
    const addTime = new Date(e.addTime).getTime();
    executorSeriesData.push({
      name: `Executor ${e.executorId} Added`,
      value: [index, addTime, 0, e] // Type 0: Added
    });
    if (e.removeTime) {
      const removeTime = new Date(e.removeTime).getTime();
      executorSeriesData.push({
        name: `Executor ${e.executorId} Removed`,
        value: [index, removeTime, 1, e] // Type 1: Removed
      });
    }
  });

  // 2. Stage Series
  // Y-axis: Stage IDs
  const stageYData = stages.map(s => `Stage ${s.stageId}`);
  const stageSeriesData = stages.map((s, index) => {
    const start = new Date(s.submissionTime).getTime();
    const end = s.completionTime ? new Date(s.completionTime).getTime() : maxTime;
    return {
      name: `Stage ${s.stageId}`,
      value: [index, start, end, s]
    };
  });

  // --- Layout Calculation ---
  const execHeight = Math.max(100, executors.length * 25);
  const stageHeight = Math.max(100, stages.length * 25);
  const totalHeight = execHeight + stageHeight + 120; // padding

  chartDom.value.style.height = `${totalHeight}px`;
  myChart.resize();

  // --- Options ---
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const v = params.value;
        if (params.seriesIndex === 0) {
          // Executor Event
          const e = v[3];
          const type = v[2] === 0 ? 'Added' : 'Removed';
          return `<div style="padding:3px;">
                    <b>Executor ${e.executorId} ${type}</b><br/>
                    Host: ${e.host}<br/>
                    Time: ${formatTime(v[1])}<br/>
                    ${e.execLossReason ? 'Reason: ' + e.execLossReason : ''}
                  </div>`;
        } else {
          // Stage
          const s = v[3];
          return `<div style="padding:3px;">
                    <b>Stage ${s.stageId}</b> (${s.attemptId})<br/>
                    Name: ${s.stageName}<br/>
                    Start: ${formatTime(v[1])}<br/>
                    End: ${s.completionTime ? formatTime(v[2]) : 'Running'}<br/>
                    Duration: ${s.duration ? (s.duration / 1000).toFixed(1) + 's' : '-'}
                  </div>`;
        }
      }
    },
    legend: {
      data: ['Executor Events', 'Stages'],
      top: 0,
      right: 10
    },
    grid: [
      {left: 120, right: 40, top: 40, height: execHeight}, // Top: Executors
      {left: 120, right: 40, top: 40 + execHeight + 40, height: stageHeight}  // Bottom: Stages
    ],
    xAxis: [
      {
        type: 'time',
        gridIndex: 0,
        min: minTime,
        max: maxTime,
        position: 'top',
        axisLabel: {show: true, fontSize: 10},
        splitLine: {show: true, lineStyle: {type: 'dashed', color: '#eee'}}
      },
      {
        type: 'time',
        gridIndex: 1,
        min: minTime,
        max: maxTime,
        position: 'bottom',
        axisLabel: {show: true, fontSize: 10},
        splitLine: {show: true, lineStyle: {type: 'dashed', color: '#eee'}}
      }
    ],
    yAxis: [
      {
        type: 'category',
        gridIndex: 0,
        data: executorYData,
        inverse: true,
        name: 'Executors',
        nameLocation: 'middle',
        nameGap: 80,
        nameTextStyle: {fontWeight: 'bold', color: '#73c0de'},
        axisTick: {show: false},
        axisLine: {show: false},
        axisLabel: {fontSize: 10}
      },
      {
        type: 'category',
        gridIndex: 1,
        data: stageYData,
        inverse: true,
        name: 'Stages',
        nameLocation: 'middle',
        nameGap: 80,
        nameTextStyle: {fontWeight: 'bold', color: '#fac858'},
        axisTick: {show: false},
        axisLine: {show: false},
        axisLabel: {fontSize: 10}
      }
    ],
    dataZoom: [
      {
        type: 'slider',
        xAxisIndex: [0, 1],
        bottom: 5,
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
        name: 'Executor Events',
        type: 'custom',
        xAxisIndex: 0,
        yAxisIndex: 0,
        renderItem: (params, api) => renderExecutorEvent(params, api),
        encode: {
          x: 1,
          y: 0
        },
        data: executorSeriesData
      },
      {
        name: 'Stages',
        type: 'custom',
        xAxisIndex: 1,
        yAxisIndex: 1,
        renderItem: (params, api) => renderGanttItem(params, api),
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

function renderExecutorEvent(params, api) {
  const categoryIndex = api.value(0);
  const time = api.value(1);
  const type = api.value(2); // 0: Added, 1: Removed

  const point = api.coord([time, categoryIndex]);
  const barHeight = api.size([0, 1])[1] * 0.8;
  const color = type === 0 ? '#3498db' : '#e74c3c';
  const label = type === 0 ? 'Added' : 'Removed';

  return {
    type: 'group',
    children: [
      {
        type: 'line',
        shape: {
          x1: point[0], y1: point[1] - barHeight / 2,
          x2: point[0], y2: point[1] + barHeight / 2
        },
        style: { stroke: color, lineWidth: 2 }
      },
      {
        type: 'text',
        style: {
          text: label,
          x: point[0] + 4,
          y: point[1] - 5,
          fill: color,
          fontSize: 10,
          fontWeight: 'bold',
          backgroundColor: 'rgba(255,255,255,0.7)'
        }
      }
    ]
  };
}

function renderGanttItem(params, api) {
  const categoryIndex = api.value(0);
  const start = api.value(1);
  const end = api.value(2);

  const startCoord = api.coord([start, categoryIndex]);
  const endCoord = api.coord([end, categoryIndex]);

  const height = api.size([0, 1])[1] * 0.6;
  const width = Math.max(2, endCoord[0] - startCoord[0]);

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
  position: relative;
  overflow: hidden;
  padding: 5px;
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
