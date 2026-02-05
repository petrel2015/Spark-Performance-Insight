<template>
  <div class="trend-chart-container">
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
</template>

<script setup>
import {ref, onMounted, watch, onBeforeUnmount, nextTick} from 'vue';
import * as echarts from 'echarts';
import {getStageTimeline} from '../../api';

const props = defineProps({
  appId: {type: String, required: true},
  stageId: {type: Number, required: true},
  attemptId: {type: Number, default: null}
});

const trendChartDom = ref(null);
const trendExecutors = ref([]);
const isZoomLocked = ref(true);
let trendChart = null;
let resizeObserver = null;

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
  if (trendChart) trendChart.showLoading();

  try {
    const res = await getStageTimeline(props.appId, props.stageId, props.attemptId);
    const tasks = res.data || [];
    if (tasks.length === 0) return;

    await nextTick();
    processAndRenderTrend(tasks);
    updateZoomLockState();
  } catch (err) {
    console.error("Failed to load trend data", err);
  } finally {
    if (trendChart) trendChart.hideLoading();
  }
};

const processAndRenderTrend = (tasks) => {
  if (!trendChartDom.value) return;
  if (!trendChart) trendChart = echarts.init(trendChartDom.value);

  const execIds = [...new Set(tasks.map(t => t.executorId))].sort((a, b) => a.localeCompare(b, undefined, {numeric: true}));
  const timePointsSet = new Set();
  const execEvents = {};

  tasks.forEach(t => {
    timePointsSet.add(t.launchTime);
    timePointsSet.add(t.finishTime);
    if (!execEvents[t.executorId]) execEvents[t.executorId] = [];
    execEvents[t.executorId].push({time: t.launchTime, type: 1});
    execEvents[t.executorId].push({time: t.finishTime, type: -1});
  });

  const sortedTimePoints = Array.from(timePointsSet).sort((a, b) => a - b);
  const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'];
  trendExecutors.value = [];

  const series = execIds.map((execId, idx) => {
    const color = colors[idx % colors.length];
    const seriesName = `Executor ${execId}`;
    trendExecutors.value.push({name: seriesName, color: color, visible: true});

    const events = execEvents[execId].sort((a, b) => a.time - b.time);
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
      itemStyle: {color: color},
      areaStyle: {opacity: 0.6, color: color},
      lineStyle: {width: 0.5},
      emphasis: {focus: 'series'},
      data: data
    };
  });

  const option = {
    grid: {top: 20, left: SHARED_GRID.left, right: SHARED_GRID.right, bottom: 40, containLabel: false},
    tooltip: {
      ...SHARED_TOOLTIP_STYLE,
      trigger: 'axis',
      axisPointer: {type: 'cross'}
    },
    xAxis: {
      type: 'time',
      axisLabel: {show: true, fontSize: 10, color: '#999'},
      axisLine: {show: false},
      axisTick: {show: false},
      splitLine: {show: true, lineStyle: {type: 'dashed', color: '#f0f0f0'}}
    },
    yAxis: {
      type: 'value',
      name: 'Active Tasks',
      minInterval: 1,
      splitLine: {show: true, lineStyle: {type: 'dashed', color: '#f0f0f0'}},
      axisLine: {show: false},
      axisTick: {show: false},
      axisLabel: {fontSize: 10, color: '#666'}
    },
    series: series,
    dataZoom: [
      {type: 'slider', xAxisIndex: 0, bottom: 0, height: 20, zoomLock: isZoomLocked.value},
      {type: 'inside', xAxisIndex: 0, disabled: isZoomLocked.value}
    ]
  };

  trendChart.setOption(option, true);
};

const updateZoomLockState = () => {
  if (!trendChart) return;
  trendChart.setOption({
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

const toggleTrendSeries = (name) => {
  const exec = trendExecutors.value.find(e => e.name === name);
  if (exec && trendChart) {
    exec.visible = !exec.visible;
    trendChart.dispatchAction({type: 'legendToggleSelect', name: name});
  }
};

defineExpose({ isZoomLocked, toggleZoomLock });

onMounted(() => {
  fetchAndRender();
  resizeObserver = new ResizeObserver(() => trendChart?.resize());
  if (trendChartDom.value) resizeObserver.observe(trendChartDom.value.parentElement);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  trendChart?.dispose();
});

watch(() => props.stageId, fetchAndRender);
</script>

<style scoped>
.trend-chart-container {
  width: 100%;
}
.trend-chart {
  width: 100%;
  height: 200px;
}
.legend-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  padding: 0 0 10px 0;
  justify-content: flex-start;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: default;
}
.legend-item.clickable {
  cursor: pointer;
  user-select: none;
}
.legend-item.disabled {
  opacity: 0.4;
  filter: grayscale(100%);
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
</style>
