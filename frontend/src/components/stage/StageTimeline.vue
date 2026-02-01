<template>
  <div class="timeline-wrapper">
    <div class="legend">
      <span class="legend-item"><i style="background: #80B1D3"></i>Scheduler Delay</span>
      <span class="legend-item"><i style="background: #FB8072"></i>Task Deserialization</span>
      <span class="legend-item"><i style="background: #FDB462"></i>Shuffle Read</span>
      <span class="legend-item"><i style="background: #B3DE69"></i>Executor Computing</span>
      <span class="legend-item"><i style="background: #FFED6F"></i>Shuffle Write</span>
      <span class="legend-item"><i style="background: #BC80BD"></i>Result Serialization</span>
      <span class="legend-item"><i style="background: #8DD3C7"></i>Getting Result Time</span>
    </div>
    <div ref="chartDom" class="timeline-chart"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import * as echarts from 'echarts';
import { getStageTimeline } from '../../api';
import { formatTime } from '../../utils/format';

const props = defineProps({
  appId: { type: String, required: true },
  stageId: { type: Number, required: true }
});

const chartDom = ref(null);
let chartInstance = null;

const fetchAndRender = async () => {
  if (!chartDom.value) return;
  if (chartInstance) chartInstance.showLoading();
  else {
    chartInstance = echarts.init(chartDom.value);
    chartInstance.showLoading();
  }
  try {
    const res = await getStageTimeline(props.appId, props.stageId);
    const tasks = res.data || [];
    renderChart(tasks);
  } catch (err) {
    console.error("Timeline fetch error", err);
  } finally {
    if (chartInstance) chartInstance.hideLoading();
  }
};

const renderChart = (tasks) => {
  if (!tasks || tasks.length === 0) return;

  // 1. 数据排序：按 Executor 分组，组内按启动时间
  const sortedTasks = [...tasks].sort((a, b) => {
    if (a.executorId !== b.executorId) {
      return a.executorId.localeCompare(b.executorId, undefined, { numeric: true });
    }
    return a.launchTime - b.launchTime;
  });

  const categories = sortedTasks.map(t => `Task ${t.taskId} (${t.executorId})`);

  // 2. 计算背景交替色带 (MarkArea)
  const markAreaData = [];
  let currentGroupStart = 0;
  const bgColors = ['rgba(0,0,0,0)', 'rgba(240,240,240,0.6)']; 
  let groupCounter = 0;
  
  for (let i = 0; i < sortedTasks.length; i++) {
    const isNew = (i === 0) || (sortedTasks[i].executorId !== sortedTasks[i-1].executorId);
    if (isNew && i > 0) {
       markAreaData.push([
         { yAxis: currentGroupStart - 0.5, itemStyle: { color: bgColors[groupCounter % 2] } },
         { yAxis: i - 0.5 }
       ]);
       groupCounter++;
       currentGroupStart = i;
    }
  }
  if (sortedTasks.length > 0) {
     markAreaData.push([
       { yAxis: currentGroupStart - 0.5, itemStyle: { color: bgColors[groupCounter % 2] } },
       { yAxis: sortedTasks.length - 0.5 }
     ]);
  }

  const minTime = Math.min(...tasks.map(t => t.launchTime));
  const data = sortedTasks.map((t, index) => {
    const totalDuration = t.duration || 0;
    const deser = t.executorDeserializeTime || 0;
    const resultSer = t.resultSerializationTime || 0;
    const runTime = t.executorRunTime || 0;
    const getResult = t.gettingResultTime || 0;
    const shuffleRead = t.shuffleFetchWaitTime || 0; 
    const shuffleWrite = (t.shuffleWriteTime || 0) / 1000000;
    const compute = Math.max(0, runTime - shuffleRead - shuffleWrite);
    let schedulerDelay = totalDuration - runTime - deser - resultSer - getResult;
    if (schedulerDelay < 0) schedulerDelay = 0;

    return {
      value: [index, t.launchTime, t.finishTime, t.duration, schedulerDelay, deser, shuffleRead, compute, shuffleWrite, resultSer, getResult, t.taskId]
    };
  });

  const height = Math.max(300, sortedTasks.length * 25 + 80);
  chartDom.value.style.height = `${height}px`;
  
  const option = {
    grid: { left: '150px', right: '30px', top: '40px', bottom: '30px', height: 'auto' },
    tooltip: {
      formatter: (params) => {
        const v = params.value;
        return `<b>Task ${v[11]}</b><br/>Duration: ${formatTime(v[3])}<br/>` +
               `<span style="color:#80B1D3">Scheduler Delay: ${formatTime(v[4])}</span><br/>` +
               `<span style="color:#FB8072">Deserialization: ${formatTime(v[5])}</span><br/>` +
               `<span style="color:#FDB462">Shuffle Read: ${formatTime(v[6])}</span><br/>` +
               `<span style="color:#B3DE69">Computing: ${formatTime(v[7])}</span><br/>` +
               `<span style="color:#FFED6F">Shuffle Write: ${formatTime(v[8])}</span><br/>` +
               `<span style="color:#BC80BD">Result Ser: ${formatTime(v[9])}</span><br/>` +
               `<span style="color:#8DD3C7">Get Result: ${formatTime(v[10])}</span>`;
      }
    },
    dataZoom: [{ type: 'slider', top: 0, height: 20 }, { type: 'inside' }],
    xAxis: {
      min: minTime, scale: true, type: 'time',
      axisLabel: { formatter: (val) => new Date(val).toLocaleTimeString() }
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      splitLine: { show: true, lineStyle: { color: '#eee' } },
      axisLabel: { interval: 0, fontSize: 10 }
    },
    series: [{
      type: 'custom',
      renderItem: renderGanttItem,
      itemStyle: { opacity: 0.8 },
      encode: { x: [1, 2], y: 0 },
      data: data,
      large: true,
      progressive: 0,
      markArea: { silent: true, data: markAreaData }
    }]
  };

  if (chartInstance) {
    chartInstance.resize();
    chartInstance.setOption(option);
  }
};

function renderGanttItem(params, api) {
  const categoryIndex = api.value(0);
  const start = api.value(1);
  const end = api.value(2);
  const metrics = [api.value(4), api.value(5), api.value(6), api.value(7), api.value(8), api.value(9), api.value(10)];
  const colors = ['#80B1D3', '#FB8072', '#FDB462', '#B3DE69', '#FFED6F', '#BC80BD', '#8DD3C7'];

  const startPt = api.coord([start, categoryIndex]);
  const endPt = api.coord([end, categoryIndex]);
  const height = api.size([0, 1])[1] * 0.6;
  const y = startPt[1] - height / 2;
  const fullWidth = endPt[0] - startPt[0];
  if (fullWidth < 0.5) return; 

  const totalTime = end - start;
  if (totalTime <= 0) return;

  let currentX = startPt[0];
  const rects = metrics.map((val, i) => {
    const w = (val / totalTime) * fullWidth;
    if (w <= 0) return null;
    const r = {
      type: 'rect',
      shape: { x: currentX, y: y, width: w, height: height },
      style: { fill: colors[i] }
    };
    currentX += w;
    return r;
  }).filter(r => r !== null);
  
  return { type: 'group', children: rects };
}

onMounted(() => {
  fetchAndRender();
  window.addEventListener('resize', () => chartInstance && chartInstance.resize());
});
watch(() => props.stageId, fetchAndRender);
</script>

<style scoped>

.timeline-wrapper { width: 100%; }

.legend { display: flex; gap: 10px; font-size: 0.8rem; flex-wrap: wrap; margin-bottom: 15px; }

.legend-item { display: flex; align-items: center; gap: 4px; color: #666; }

.legend-item i { display: block; width: 10px; height: 10px; border-radius: 2px; }

.timeline-chart { width: 100%; min-height: 300px; }

</style>


