<template>
  <div class="task-timeline-container">
    <!-- Timeline Legend -->
    <div class="legend-bar">
      <div v-for="item in METRIC_CONFIG" :key="item.label" class="legend-item">
        <span class="color-box" :style="{ backgroundColor: item.color }"></span>
        <span class="label">{{ item.label }}</span>
      </div>
      <div class="legend-item">
        <span class="color-box" style="background-color: #3498db; width: 2px; border-radius: 0;"></span>
        <span class="label">Executor Added</span>
      </div>
      <div class="legend-item">
        <span class="color-box" style="background-color: #e74c3c; width: 2px; border-radius: 0;"></span>
        <span class="label">Executor Removed</span>
      </div>
    </div>
    <div ref="chartDom" class="timeline-chart"></div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, onBeforeUnmount, nextTick} from 'vue';
import * as echarts from 'echarts';
import {getStageTimeline, getAppExecutors} from '../../api';
import {formatTime} from '../../utils/format';

const props = defineProps({
  appId: {type: String, required: true},
  stageId: {type: Number, required: true},
  attemptId: {type: Number, default: null}
});

const chartDom = ref(null);
const isZoomLocked = ref(true);
let mainChart = null;
let resizeObserver = null;

const METRIC_CONFIG = [
  {key: 'schedulerDelay', label: 'Scheduler Delay', color: '#80B1D3'},
  {key: 'deserialization', label: 'Deserialization', color: '#FB8072'},
  {key: 'shuffleRead', label: 'Shuffle Read', color: '#FDB462'},
  {key: 'gcTime', label: 'GC Time', color: '#EF4444'},
  {key: 'computing', label: 'Computing', color: '#B3DE69'},
  {key: 'shuffleWrite', label: 'Shuffle Write', color: '#FFED6F'},
  {key: 'serialization', label: 'Serialization', color: '#BC80BD'},
  {key: 'gettingResult', label: 'Getting Result', color: '#8DD3C7'}
];

const SHARED_GRID = {
  left: 100,
  right: 40,
  containLabel: false
};

const SHARED_TOOLTIP_STYLE = {
  backgroundColor: '#ffffff',
  borderColor: '#eee',
  borderWidth: 1,
  padding: 10,
  textStyle: {color: '#333', fontSize: 12},
  confine: true,
  extraCssText: 'box-shadow: 0 3px 12px rgba(0,0,0,0.1); border-radius: 4px;'
};

const fetchAndRender = async () => {
  if (!props.appId || !props.stageId) return;
  if (mainChart) mainChart.showLoading();

  try {
    const [tasksRes, execRes] = await Promise.all([
      getStageTimeline(props.appId, props.stageId, props.attemptId),
      getAppExecutors(props.appId)
    ]);
    const tasks = tasksRes.data || [];
    const executors = execRes.data || [];
    if (tasks.length === 0) return;

    await nextTick();
    processAndRenderMain(tasks, executors);
    updateZoomLockState();
  } catch (err) {
    console.error("Failed to load timeline data", err);
  } finally {
    if (mainChart) mainChart.hideLoading();
  }
};

const processAndRenderMain = (tasks, allExecutors) => {
  if (!chartDom.value) return;
  if (!mainChart) mainChart = echarts.init(chartDom.value);

  const execMap = new Map();
  tasks.forEach(t => {
    if (!execMap.has(t.executorId)) execMap.set(t.executorId, []);
    execMap.get(t.executorId).push(t);
  });

  const activeExecutors = Array.from(execMap.keys()).sort((a, b) => a.localeCompare(b, undefined, {numeric: true}));

  const taskData = [];
  const executorLanes = new Map();

  activeExecutors.forEach((execId, execIdx) => {
    const execTasks = execMap.get(execId).sort((a, b) => a.launchTime - b.launchTime);
    const lanes = [];
    
    execTasks.forEach(t => {
      const deser = t.executorDeserializeTime || 0;
      const ser = t.resultSerializationTime || 0;
      const runTime = t.executorRunTime || 0;
      const gc = t.gcTime || 0;
      const getResult = t.gettingResultTime || 0;
      const sRead = t.shuffleFetchWaitTime || 0;
      const sWrite = (t.shuffleWriteTime || 0) / 1000000;
      const computing = Math.max(0, runTime - gc - sRead - sWrite);
      let delay = t.duration - runTime - deser - ser - getResult;
      if (delay < 0) delay = 0;

      let laneIndex = -1;
      for (let i = 0; i < lanes.length; i++) {
        if (lanes[i] <= t.launchTime) {
          laneIndex = i;
          break;
        }
      }
      if (laneIndex === -1) {
        laneIndex = lanes.length;
        lanes.push(0);
      }
      lanes[laneIndex] = t.finishTime;

      taskData.push({
        name: `Task ${t.taskId}`,
        value: [
          execIdx, t.launchTime, t.finishTime, t.duration,
          delay, deser, sRead, gc, computing, sWrite, ser, getResult,
          t.executorId, t.taskId, laneIndex 
        ]
      });
    });
    executorLanes.set(execId, lanes.length);
  });

  taskData.forEach(d => {
    const execId = d.value[12];
    d.value.push(executorLanes.get(execId)); // totalLanes at 15
  });

  const minTime = Math.min(...tasks.map(t => t.launchTime));
  const maxTime = Math.max(...tasks.map(t => t.finishTime));

  // Executor Events Series
  const executorEventData = [];
  allExecutors.forEach(e => {
    const execIdx = activeExecutors.indexOf(e.executorId);
    if (execIdx === -1) return; 

    const addTime = new Date(e.addTime).getTime();
    if (addTime <= maxTime + 5000) {
      executorEventData.push({
        name: `Executor ${e.executorId} Added`,
        value: [execIdx, addTime, 0, e] // Type 0: Added
      });
    }
    if (e.removeTime) {
      const removeTime = new Date(e.removeTime).getTime();
      if (removeTime >= minTime - 5000) {
        executorEventData.push({
          name: `Executor ${e.executorId} Removed`,
          value: [execIdx, removeTime, 1, e] // Type 1: Removed
        });
      }
    }
  });

  const mainHeight = activeExecutors.length * 50 + 100;
  chartDom.value.style.height = `${mainHeight}px`;
  mainChart.resize();

  const option = {
    tooltip: {
      ...SHARED_TOOLTIP_STYLE,
      trigger: 'item',
      formatter: (params) => {
        const v = params.value;
        if (params.seriesIndex === 0) { // Tasks
          const metricsHtml = METRIC_CONFIG.map((cfg, i) => {
            return `<div style="display:flex; justify-content:space-between; gap:20px; font-size:11px; line-height:1.8;">
                      <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${cfg.color};margin-right:5px;"></span>${cfg.label}:</span>
                      <b>${formatTime(v[4 + i])}</b>
                    </div>`;
          }).join('');
          return `<div style="min-width:180px;">
                    <div style="margin-bottom:8px; border-bottom:1px solid #eee; padding-bottom:5px; font-size:12px;">
                      <b>Task ${v[13]}</b> (Exec: ${v[12]})
                    </div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:5px; font-size:11px;">
                      <span>Total Duration:</span><b>${formatTime(v[3])}</b>
                    </div>
                    ${metricsHtml}
                  </div>`;
        } else { // Executor Events
          const e = v[3];
          const type = v[2] === 0 ? 'Added' : 'Removed';
          const color = v[2] === 0 ? '#3498db' : '#e74c3c';
          return `<div style="padding: 5px;">
                    <b style="color: ${color}">Executor ${e.executorId} ${type}</b><br/>
                    Host: ${e.host}<br/>
                    Time: ${new Date(v[1]).toLocaleString()}
                  </div>`;
        }
      }
    },
    grid: {top: 40, left: SHARED_GRID.left, right: SHARED_GRID.right, bottom: 40, containLabel: false},
    xAxis: {
      type: 'time',
      position: 'top',
      splitLine: {show: true, lineStyle: {type: 'dashed', color: '#f0f0f0'}},
      axisLine: {show: false},
      axisTick: {show: false},
      min: minTime - 2000,
      max: maxTime + 2000
    },
    yAxis: {
      type: 'category',
      data: activeExecutors,
      inverse: true,
      axisTick: {show: false},
      axisLine: {show: false},
      splitLine: {show: true, lineStyle: {color: '#f5f5f5'}},
      axisLabel: {fontWeight: 'bold', color: '#666'}
    },
    dataZoom: [
      {type: 'slider', xAxisIndex: 0, bottom: 0, height: 20, zoomLock: isZoomLocked.value},
      {type: 'inside', xAxisIndex: 0, disabled: isZoomLocked.value}
    ],
    series: [
      {
        name: 'Tasks',
        type: 'custom',
        renderItem: renderGanttItem,
        encode: {x: [1, 2], y: 0},
        data: taskData,
        clip: true,
        zIndex: 10
      },
      {
        name: 'Executor Events',
        type: 'custom',
        renderItem: renderExecutorEvent,
        encode: {x: 1, y: 0},
        data: executorEventData,
        zIndex: 20
      }
    ]
  };

  mainChart.setOption(option);
};

function renderExecutorEvent(params, api) {
  const categoryIndex = api.value(0);
  const time = api.value(1);
  const type = api.value(2); // 0: Added, 1: Removed

  const point = api.coord([time, categoryIndex]);
  const categoryHeight = api.size([0, 1])[1];
  const barHeight = categoryHeight * 0.8;
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
          y: point[1] - 10,
          fill: color,
          fontSize: 9,
          fontWeight: 'bold',
          backgroundColor: 'rgba(255,255,255,0.6)'
        }
      }
    ]
  };
}

function renderGanttItem(params, api) {
  const categoryIndex = api.value(0);
  const start = api.value(1);
  const end = api.value(2);
  const laneIndex = api.value(14);
  const totalLanes = api.value(15);

  const startCoord = api.coord([start, categoryIndex]);
  const endCoord = api.coord([end, categoryIndex]);
  const categoryHeight = api.size([0, 1])[1];
  const availableHeight = categoryHeight * 0.8;
  const barHeight = availableHeight / totalLanes;
  const topY = startCoord[1] - availableHeight / 2;
  const y = topY + (laneIndex * barHeight);
  const x = startCoord[0];
  const totalWidth = Math.max(endCoord[0] - startCoord[0], 2);
  
  const totalTime = end - start;
  if (totalTime <= 0) return;
  
  let currentX = x;
  const children = [];
  METRIC_CONFIG.forEach((cfg, i) => {
    const val = api.value(4 + i);
    if (val > 0) {
      const w = (val / totalTime) * totalWidth;
      children.push({
        type: 'rect',
        shape: {x: currentX, y: y, width: w, height: barHeight},
        style: {fill: cfg.color}
      });
      currentX += w;
    }
  });
  return {type: 'group', children};
}

const updateZoomLockState = () => {
  if (!mainChart) return;
  mainChart.setOption({
    dataZoom: [
      {type: 'slider', zoomLock: isZoomLocked.value},
      {type: 'inside', disabled: isZoomLocked.value}
    ]
  });
};

const toggleZoomLock = () => {
  isZoomLocked.value = !isZoomLocked.value;
  updateZoomLockState();
};

defineExpose({ isZoomLocked, toggleZoomLock });

onMounted(() => {
  fetchAndRender();
  resizeObserver = new ResizeObserver(() => mainChart?.resize());
  if (chartDom.value) resizeObserver.observe(chartDom.value.parentElement);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  mainChart?.dispose();
});

watch(() => props.stageId, fetchAndRender);
</script>

<style scoped>
.task-timeline-container {
  width: 100%;
}
.legend-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  padding: 0 0 10px 0;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.color-box {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
.label {
  font-size: 0.75rem;
  color: #666;
}
.timeline-chart {
  width: 100%;
}
</style>