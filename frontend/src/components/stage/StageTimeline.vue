<template>
  <div class="timeline-container">
    <!-- Active Tasks Trend (Top Small Chart) -->
    <div class="chart-card">
      <div class="card-header">
        Active Tasks Over Time (Stacked by Executor)
      </div>
      
      <!-- Trend Legend (Custom) -->
      <div class="legend-bar" v-if="trendExecutors.length > 0">
        <div v-for="exec in trendExecutors" :key="exec.name" 
             class="legend-item clickable" 
             :class="{ disabled: !exec.visible }"
             @click="toggleTrendSeries(exec.name)">
          <span class="color-box" :style="{ backgroundColor: exec.color }"></span>
          <span class="label">{{ exec.name }}</span>
        </div>
      </div>

      <div ref="trendChartDom" class="trend-chart"></div>
    </div>

    <!-- Main Gantt Chart -->
    <div class="chart-card main-chart-card">
      <div class="card-header">Task Execution Timeline</div>
      
      <!-- Timeline Legend -->
      <div class="legend-bar">
        <div v-for="item in METRIC_CONFIG" :key="item.label" class="legend-item">
          <span class="color-box" :style="{ backgroundColor: item.color }"></span>
          <span class="label">{{ item.label }}</span>
        </div>
      </div>

      <div ref="chartDom" class="timeline-chart"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getStageTimeline } from '../../api';
import { formatTime } from '../../utils/format';

const props = defineProps({
  appId: { type: String, required: true },
  stageId: { type: Number, required: true }
});

const chartDom = ref(null);
const trendChartDom = ref(null);
const trendExecutors = ref([]);
const hoveredTask = ref(null);
const isZoomLocked = ref(true); // Default locked
let mainChart = null;
let trendChart = null;
let resizeObserver = null;

const METRIC_CONFIG = [
  { key: 'schedulerDelay', label: 'Scheduler Delay', color: '#80B1D3' },
  { key: 'deserialization', label: 'Deserialization', color: '#FB8072' },
  { key: 'shuffleRead', label: 'Shuffle Read', color: '#FDB462' },
  { key: 'gcTime', label: 'GC Time', color: '#EF4444' },
  { key: 'computing', label: 'Computing', color: '#B3DE69' },
  { key: 'shuffleWrite', label: 'Shuffle Write', color: '#FFED6F' },
  { key: 'serialization', label: 'Serialization', color: '#BC80BD' },
  { key: 'gettingResult', label: 'Getting Result', color: '#8DD3C7' }
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
  textStyle: { color: '#333', fontSize: 12 },
  confine: true,
  extraCssText: 'box-shadow: 0 3px 12px rgba(0,0,0,0.1); border-radius: 4px;'
};

const fetchAndRender = async () => {
  if (!props.appId || !props.stageId) return;
  
  if (mainChart) mainChart.showLoading();
  if (trendChart) trendChart.showLoading();

  try {
    const res = await getStageTimeline(props.appId, props.stageId);
    const tasks = res.data || [];
    if (tasks.length === 0) return;

    await nextTick();
    processAndRenderMain(tasks);
    processAndRenderTrend(tasks);
    updateZoomLockState();
  } catch (err) {
    console.error("Failed to load timeline data", err);
  } finally {
    if (mainChart) mainChart.hideLoading();
    if (trendChart) trendChart.hideLoading();
  }
};

const updateZoomLockState = () => {
  const zoomConfig = [
    {
      type: 'slider',
      xAxisIndex: 0,
      zoomLock: isZoomLocked.value,
      brushSelect: !isZoomLocked.value
    },
    {
      type: 'inside',
      xAxisIndex: 0,
      disabled: isZoomLocked.value
    }
  ];

  if (mainChart) mainChart.setOption({ dataZoom: zoomConfig });
  if (trendChart) trendChart.setOption({ dataZoom: zoomConfig });
};

const toggleZoomLock = () => {
  isZoomLocked.value = !isZoomLocked.value;
  updateZoomLockState();
};

const processAndRenderMain = (tasks) => {
  if (!mainChart) mainChart = echarts.init(chartDom.value);

  const execMap = new Map();
  tasks.forEach(t => {
    if (!execMap.has(t.executorId)) execMap.set(t.executorId, []);
    execMap.get(t.executorId).push(t);
  });

  const executors = Array.from(execMap.keys()).sort((a,b) => a.localeCompare(b, undefined, {numeric:true}));
  
  const data = [];
  executors.forEach((execId, execIdx) => {
    const execTasks = execMap.get(execId).sort((a,b) => a.launchTime - b.launchTime);
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

      data.push({
        name: `Task ${t.taskId}`,
        value: [
          execIdx, t.launchTime, t.finishTime, t.duration,
          delay, deser, sRead, gc, computing, sWrite, ser, getResult,
          t.executorId, t.taskId
        ]
      });
    });
  });

      const minTime = Math.min(...tasks.map(t => t.launchTime));

      const maxTime = Math.max(...tasks.map(t => t.finishTime));

    

      const mainHeight = executors.length * 50 + 120; // 50px per row + padding for axis and datazoom

      chartDom.value.style.height = `${mainHeight}px`;

      

      mainChart.resize();

  mainChart.off('mouseover');
  mainChart.off('mouseout');
  mainChart.on('mouseover', (params) => {
    if (params.seriesIndex === 0) hoveredTask.value = params.value;
  });
  mainChart.on('mouseout', () => {
    hoveredTask.value = null;
  });

  const option = {
    tooltip: {
      ...SHARED_TOOLTIP_STYLE,
      trigger: 'item',
      axisPointer: {
        type: 'cross',
        label: { backgroundColor: '#6a7985' },
        lineStyle: { type: 'dashed', color: '#999' }
      },
      formatter: (params) => {
        const v = params.value;
        const metricsHtml = METRIC_CONFIG.map((cfg, i) => {
          return `<div style="display:flex; justify-content:space-between; gap:20px; font-size:11px; line-height:1.8;">
                    <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${cfg.color};margin-right:5px;"></span>${cfg.label}:</span>
                    <b>${formatTime(v[4+i])}</b>
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
      }
    },
    grid: { top: 40, left: SHARED_GRID.left, right: SHARED_GRID.right, bottom: 60, containLabel: false },
    xAxis: {
      type: 'time',
      position: 'top',
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#f0f0f0' } },
      axisLine: { show: false },
      axisTick: { show: false },
      min: minTime,
      max: maxTime
    },
    yAxis: {
      type: 'category',
      data: executors,
      inverse: true,
      axisTick: { show: false },
      axisLine: { show: false },
      splitLine: { show: true, lineStyle: { color: '#f5f5f5' } },
      axisLabel: { fontWeight: 'bold', color: '#666' }
    },
    dataZoom: [
      { 
        type: 'slider', 
        xAxisIndex: 0, 
        filterMode: 'weakFilter', 
        bottom: 10, 
        height: 24,
        left: SHARED_GRID.left,
        right: SHARED_GRID.right,
        zoomLock: isZoomLocked.value
      },
      { type: 'inside', xAxisIndex: 0, filterMode: 'weakFilter', disabled: isZoomLocked.value }
    ],
    series: [{
      type: 'custom',
      renderItem: renderGanttItem,
      encode: { x: [1, 2], y: 0 },
      data: data,
      clip: true
    }]
  };

  mainChart.setOption(option);
};

const processAndRenderTrend = (tasks) => {
  if (!trendChart) trendChart = echarts.init(trendChartDom.value);

  const execIds = [...new Set(tasks.map(t => t.executorId))].sort((a,b) => a.localeCompare(b, undefined, {numeric:true}));
  const timePointsSet = new Set();
  const execEvents = {};

  tasks.forEach(t => {
    timePointsSet.add(t.launchTime);
    timePointsSet.add(t.finishTime);
    if (!execEvents[t.executorId]) execEvents[t.executorId] = [];
    execEvents[t.executorId].push({ time: t.launchTime, type: 1 });
    execEvents[t.executorId].push({ time: t.finishTime, type: -1 });
  });

  const sortedTimePoints = Array.from(timePointsSet).sort((a, b) => a - b);
  const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'];
  trendExecutors.value = [];

  const series = execIds.map((execId, idx) => {
    const color = colors[idx % colors.length];
    const seriesName = `Executor ${execId}`;
    trendExecutors.value.push({ name: seriesName, color: color, visible: true });

    const events = execEvents[execId].sort((a,b) => a.time - b.time);
    const data = [];
    let currentActive = 0;
    let eventIdx = 0;

    sortedTimePoints.forEach(tp => {
      while (eventIdx < events.length && events[eventIdx].time <= tp) {
        currentActive += events[eventIdx].type;
        eventIdx++;
      }
      data.push([tp, currentActive]);
    });

    return {
      name: seriesName,
      type: 'line',
      stack: 'total',
      step: 'end',
      symbol: 'none',
      itemStyle: { color: color },
      areaStyle: { opacity: 0.6, color: color },
      lineStyle: { width: 0.5 },
      emphasis: { focus: 'series' },
      data: data
    };
  });

  const option = {
    grid: { top: 40, left: SHARED_GRID.left, right: SHARED_GRID.right, bottom: 60, containLabel: false },
    tooltip: { 
      ...SHARED_TOOLTIP_STYLE,
      trigger: 'axis', 
      axisPointer: { type: 'cross' } 
    },
    legend: { show: false },
    xAxis: { 
      type: 'time', 
      show: true, 
      position: 'top',
      axisLabel: { show: true, fontSize: 10, color: '#999', margin: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
      min: 'dataMin', 
      max: 'dataMax',
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#f0f0f0' } }
    },
    yAxis: { 
      type: 'value', 
      name: 'Running Tasks',
      minInterval: 1,
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#f0f0f0' } }, 
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, color: '#666' },
      nameTextStyle: { color: '#999', padding: [0, 0, 0, 20] }
    },
    series: series,
    dataZoom: [
      { 
        type: 'slider', 
        xAxisIndex: 0, 
        filterMode: 'weakFilter', 
        bottom: 10, 
        height: 24,
        left: SHARED_GRID.left,
        right: SHARED_GRID.right,
        zoomLock: isZoomLocked.value
      },
      { type: 'inside', xAxisIndex: 0, disabled: isZoomLocked.value }
    ]
  };

  trendChart.setOption(option, true);
  trendChart.resize();
};

const toggleTrendSeries = (name) => {
  const exec = trendExecutors.value.find(e => e.name === name);
  if (exec && trendChart) {
    exec.visible = !exec.visible;
    trendChart.dispatchAction({ type: 'legendToggleSelect', name: name });
  }
};

function renderGanttItem(params, api) {

  const categoryIndex = api.value(0);

  const start = api.value(1);

  const end = api.value(2);

  const startCoord = api.coord([start, categoryIndex]);

  const endCoord = api.coord([end, categoryIndex]);

  

  const barHeight = api.size([0, 1])[1] * 0.8;

  const x = startCoord[0];
  const y = startCoord[1] - barHeight / 2;
  const totalWidth = endCoord[0] - startCoord[0];
  if (totalWidth < 0.1) return;
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
        shape: { x: currentX, y: y, width: w, height: barHeight },
        style: { fill: cfg.color }
      });
      currentX += w;
    }
  });
  return { type: 'group', children };
}

defineExpose({
  isZoomLocked,
  toggleZoomLock
});

onMounted(() => {
  fetchAndRender();
  resizeObserver = new ResizeObserver(() => {
    mainChart?.resize();
    trendChart?.resize();
  });
  if (chartDom.value) resizeObserver.observe(chartDom.value.parentElement);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  mainChart?.dispose();
  trendChart?.dispose();
});

watch(() => props.stageId, fetchAndRender);
</script>

<style scoped>
.timeline-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: white;
  padding: 15px;
  border-radius: 8px;
  position: relative;
}

.chart-card {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: white;
  overflow: hidden;
}

.card-header {
  font-size: 0.75rem;
  font-weight: bold;
  color: #666;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.lock-btn {
  background: none;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.7rem;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
}

.lock-btn:hover { background: #f0f0f0; color: #333; }

.trend-chart { width: 100%; height: 200px; }

.legend-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  padding: 8px 12px;
  justify-content: flex-start;
  background: white;
  border-bottom: 1px solid #f0f0f0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: default;
  transition: opacity 0.2s;
}

.legend-item.clickable { cursor: pointer; user-select: none; }
.legend-item.clickable:hover { opacity: 0.8; }
.legend-item.disabled { opacity: 0.4; filter: grayscale(100%); }

.color-box { width: 12px; height: 12px; border-radius: 2px; }
.label { font-size: 0.75rem; color: #444; font-weight: 500; }
.timeline-chart { width: 100%; }
</style>