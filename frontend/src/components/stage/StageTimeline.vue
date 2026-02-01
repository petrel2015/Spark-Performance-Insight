<template>
  <div class="timeline-card full-width">
    <div class="chart-header">
      <h4>Event Timeline</h4>
      <div class="legend">
        <span class="legend-item"><i style="background: #007bff"></i>Scheduler Delay</span>
        <span class="legend-item"><i style="background: #fd7e14"></i>Task Deserialization</span>
        <span class="legend-item"><i style="background: #ffc107"></i>Shuffle Read</span>
        <span class="legend-item"><i style="background: #28a745"></i>Executor Computing</span>
        <span class="legend-item"><i style="background: #ffc107"></i>Shuffle Write</span>
        <span class="legend-item"><i style="background: #fd7e14"></i>Result Serialization</span>
        <span class="legend-item"><i style="background: #17a2b8"></i>Getting Result Time</span>
      </div>
    </div>
    <div ref="chartDom" class="timeline-chart"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getStageTimeline } from '../../api';

const props = defineProps({
  appId: { type: String, required: true },
  stageId: { type: Number, required: true }
});

const chartDom = ref(null);
let chartInstance = null;

const fetchAndRender = async () => {
  if (!chartDom.value) return;
  
  // Show loading
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

  // 1. Prepare Data
  // Group by Executor
  const executorSet = new Set();
  tasks.forEach(t => executorSet.add(t.executorId));
  const executors = Array.from(executorSet).sort(); // Sort by ID
  const executorIndexMap = {};
  executors.forEach((e, i) => executorIndexMap[e] = i);

  // Global Min Time for offset (optional, but good for X-axis zoom)
  const minTime = Math.min(...tasks.map(t => t.launchTime));
  
  // Prepare Custom Series Data
  // Item format: [executorIndex, startTime, endTime, ...durations]
  // We need to pass all metrics to renderItem
  const data = tasks.map(t => {
    // Calculate durations
    const totalDuration = t.duration || 0;
    const deser = t.executorDeserializeTime || 0;
    const resultSer = t.resultSerializationTime || 0;
    const runTime = t.executorRunTime || 0;
    const getResult = t.gettingResultTime || 0;
    const shuffleRead = t.shuffleFetchWaitTime || 0; // Approximate Shuffle Read as Fetch Wait
    const shuffleWrite = (t.shuffleWriteTime || 0) / 1000000; // nanos to ms
    
    // Compute Time: RunTime - ShuffleRead - ShuffleWrite
    // Ensure non-negative
    const compute = Math.max(0, runTime - shuffleRead - shuffleWrite);
    
    // Scheduler Delay
    // Spark Logic: Duration - RunTime - Deser - Ser - GetResult
    let schedulerDelay = totalDuration - runTime - deser - resultSer - getResult;
    if (schedulerDelay < 0) schedulerDelay = 0;

    return {
      value: [
        executorIndexMap[t.executorId],
        t.launchTime,
        t.finishTime,
        t.duration, // 3
        schedulerDelay, // 4
        deser,          // 5
        shuffleRead,    // 6
        compute,        // 7
        shuffleWrite,   // 8
        resultSer,      // 9
        getResult,      // 10
        t.taskId        // 11 (Task ID for tooltip)
      ]
    };
  });

  const categories = executors;

  // 2. Configure ECharts
  const option = {
    tooltip: {
      formatter: (params) => {
        const v = params.value;
        return `
          <b>Task ${v[11]}</b><br/>
          Duration: ${v[3]} ms<br/>
          <span style="color:#007bff">Scheduler Delay: ${v[4]} ms</span><br/>
          <span style="color:#fd7e14">Deserialization: ${v[5]} ms</span><br/>
          <span style="color:#ffc107">Shuffle Read: ${v[6]} ms</span><br/>
          <span style="color:#28a745">Computing: ${v[7]} ms</span><br/>
          <span style="color:#ffc107">Shuffle Write: ${v[8].toFixed(1)} ms</span><br/>
          <span style="color:#fd7e14">Result Ser: ${v[9]} ms</span><br/>
          <span style="color:#17a2b8">Get Result: ${v[10]} ms</span>
        `;
      }
    },
    grid: {
      left: '100px',
      right: '20px',
      top: '30px',
      bottom: '30px',
      height: 'auto'
    },
    dataZoom: [
      {
        type: 'slider',
        filterMode: 'weakFilter',
        showDataShadow: false,
        top: 0,
        height: 20,
        borderColor: 'transparent',
        backgroundColor: '#e2e2e2',
        handleIcon: 'path://M10.7,11.9H9.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4h1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7v-1.2h6.6z M13.3,22H6.7v-1.2h6.6z M13.3,19.6H6.7v-1.2h6.6z', // Simple handle
        handleSize: 20,
        handleStyle: {
          shadowBlur: 6,
          shadowOffsetX: 1,
          shadowOffsetY: 2,
          shadowColor: '#aaa'
        },
        labelFormatter: ''
      },
      {
        type: 'inside',
        filterMode: 'weakFilter'
      }
    ],
    xAxis: {
      min: minTime,
      scale: true,
      type: 'time',
      axisLabel: {
        formatter: (val) => {
          return new Date(val).toLocaleTimeString();
        }
      }
    },
    yAxis: {
      type: 'category',
      data: categories,
      splitLine: { show: true }
    },
    series: [
      {
        type: 'custom',
        renderItem: renderGanttItem,
        itemStyle: {
          opacity: 0.8
        },
        encode: {
          x: [1, 2],
          y: 0,
          tooltip: [3, 4, 5, 6, 7, 8, 9, 10]
        },
        data: data,
        // Optimization for large data
        large: true, 
        progressive: 0 // Render immediately
      }
    ]
  };

  // Adjust Grid Height based on number of executors
  const height = Math.max(300, executors.length * 30 + 60);
  chartDom.value.style.height = `${height}px`;
  if (chartInstance) {
    chartInstance.resize();
    chartInstance.setOption(option);
  }
};

function renderGanttItem(params, api) {
  const categoryIndex = api.value(0);
  const start = api.value(1);
  const end = api.value(2);
  
  // Metrics mapped by index
  const schedulerDelay = api.value(4);
  const deser = api.value(5);
  const shuffleRead = api.value(6);
  const compute = api.value(7);
  const shuffleWrite = api.value(8);
  const resultSer = api.value(9);
  const getResult = api.value(10);

  const startPt = api.coord([start, categoryIndex]);
  const endPt = api.coord([end, categoryIndex]);
  
  // Height of the bar
  const height = api.size([0, 1])[1] * 0.6;
  const y = startPt[1] - height / 2;
  
  // Total pixel width of the task
  const fullWidth = endPt[0] - startPt[0];
  if (fullWidth < 1) return; // Skip if too small

  // Calculate proportional widths for each segment based on time
  const totalTime = end - start;
  if (totalTime <= 0) return;

  const getWidth = (ms) => (ms / totalTime) * fullWidth;

  const wScheduler = getWidth(schedulerDelay);
  const wDeser = getWidth(deser);
  const wShuffleRead = getWidth(shuffleRead);
  const wCompute = getWidth(compute);
  const wShuffleWrite = getWidth(shuffleWrite);
  const wResultSer = getWidth(resultSer);
  const wGetResult = getWidth(getResult);

  let currentX = startPt[0];

  const rects = [];

  const addRect = (width, color) => {
    if (width > 0) {
      rects.push({
        type: 'rect',
        shape: {
          x: currentX,
          y: y,
          width: width,
          height: height
        },
        style: {
          fill: color
        }
      });
      currentX += width;
    }
  };

  // Stack Order matches Spark UI
  addRect(wScheduler, '#007bff'); // Scheduler Delay
  addRect(wDeser, '#fd7e14'); // Deserialization
  addRect(wShuffleRead, '#ffc107'); // Shuffle Read
  addRect(wCompute, '#28a745'); // Computing
  addRect(wShuffleWrite, '#ffc107'); // Shuffle Write
  addRect(wResultSer, '#fd7e14'); // Result Ser
  addRect(wGetResult, '#17a2b8'); // Get Result
  
  return {
    type: 'group',
    children: rects
  };
}

onMounted(() => {
  fetchAndRender();
  window.addEventListener('resize', () => chartInstance && chartInstance.resize());
});

watch(() => props.stageId, fetchAndRender);
</script>

<style scoped>
.timeline-card {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  margin-bottom: 1.5rem;
  position: relative;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.chart-header h4 { margin: 0; font-size: 1rem; color: #2c3e50; }

.legend { display: flex; gap: 10px; font-size: 0.8rem; flex-wrap: wrap;}
.legend-item { display: flex; align-items: center; gap: 4px; color: #666; }
.legend-item i { display: block; width: 10px; height: 10px; border-radius: 2px; }

.timeline-chart {
  width: 100%;
  height: 400px; /* Initial height */
}
</style>
