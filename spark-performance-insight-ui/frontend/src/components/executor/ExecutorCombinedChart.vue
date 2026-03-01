<template>
  <div class="combined-chart-container">
    <div class="chart-controls">
      <div class="metric-tabs">
        <button 
          v-for="m in metrics" 
          :key="m.value"
          class="metric-tab"
          :class="{ active: currentMetric === m.value, loading: loadingMetrics.has(m.value) }"
          @click="selectMetric(m.value)"
        >
          <span v-if="loadingMetrics.has(m.value)" class="mini-spinner"></span>
          {{ m.label }}
        </button>
      </div>
    </div>
    <div ref="chartRef" class="chart-box"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed, reactive } from 'vue';
import { useRoute } from 'vue-router';
import * as echarts from 'echarts';
import { getExecutorMetricTimeSeries } from '../../api';
import { formatBytes, formatNum, formatDateTime } from '../../utils/format';

const props = defineProps({
  executors: { type: Array, default: () => [] }
});

const route = useRoute();
const chartRef = ref(null);
let myChart = null;

const metrics = [
  { label: 'Task Count', value: 'task_count' },
  { label: 'HDFS In', value: 'hdfs_in' },
  { label: 'HDFS Out', value: 'hdfs_out' },
  { label: 'HDFS I/O', value: 'hdfs_io' },
  { label: 'Shuffle Read', value: 'shuffle_read' },
  { label: 'Shuffle Write', value: 'shuffle_write' }
];

const currentMetric = ref('task_count');
const metricsDataMap = reactive(new Map());
const loadingMetrics = ref(new Set());
const isZoomLocked = ref(true);

const fetchMetricData = async (metricKey) => {
  const appId = route.params.id;
  if (!appId || metricsDataMap.has(metricKey)) {
    updateChart(); // Already cached, just update view
    return;
  }

  loadingMetrics.value.add(metricKey);
  try {
    const res = await getExecutorMetricTimeSeries(appId, metricKey);
    metricsDataMap.set(metricKey, res.data || []);
    updateChart();
  } catch (err) {
    console.error(`Failed to fetch metric ${metricKey}`, err);
  } finally {
    loadingMetrics.value.delete(metricKey);
  }
};

const selectMetric = (val) => {
  currentMetric.value = val;
  fetchMetricData(val);
};

// Dynamic time range calculation
const timeRange = computed(() => {
  let min = Infinity;
  let max = -Infinity;

  const processVal = (val) => {
    if (val === null || val === undefined) return;
    const n = Number(val);
    if (!isNaN(n) && n > 0) {
      if (n < min) min = n;
      if (n > max) max = n;
    }
  };

  props.executors.forEach(e => {
    processVal(e.addTime);
    processVal(e.removeTime);
  });
  
  const data = metricsDataMap.get(currentMetric.value) || [];
  data.forEach(d => processVal(d.time_window));

  if (min === Infinity || max === -Infinity) {
    const now = Date.now();
    return { min: now - 3600000, max: now };
  }

  const duration = max - min;
  const padding = duration > 0 ? Math.max(duration * 0.05, 5000) : 10000;
  return { min: min - padding, max: max + padding };
});

const initChart = () => {
  if (!chartRef.value) return;
  myChart = echarts.init(chartRef.value);
  updateChart();
  window.addEventListener('resize', () => myChart && myChart.resize());
};

const updateChart = () => {
  if (!myChart) return;

  const { min, max } = timeRange.value;
  if (!min || !max || isNaN(min) || isNaN(max) || min === max || min === Infinity) return;

  const sortedExecutors = [...props.executors].sort((a, b) => a.executorId.localeCompare(b.executorId, undefined, {numeric: true}));
  const execIds = sortedExecutors.map(e => e.executorId);

  // 1. Lifecycle Series (Top Grid)
  const lifecycleData = sortedExecutors.map((e, index) => {
    const start = Number(e.addTime);
    const end = e.removeTime ? Number(e.removeTime) : max;
    return {
      name: `Executor ${e.executorId}`,
      value: [index, start, end, e.executorId, e.host, e.execLossReason],
      itemStyle: { color: e.removeTime ? '#95a5a6' : '#2ecc71' }
    };
  });

  // 2. Performance Series (Bottom Grid)
  const metricKey = currentMetric.value;
  const metricInfo = metrics.find(m => m.value === metricKey);
  const data = metricsDataMap.get(metricKey) || [];
  
  const perfExecIds = [...new Set(data.map(d => d.executor_id))].sort((a, b) => a.localeCompare(b, undefined, {numeric: true}));
  const perfSeries = perfExecIds.map(execId => {
    const seriesName = `Executor ${execId}`;
    const points = data
      .filter(d => d.executor_id === execId)
      .map(d => [Number(d.time_window), d.value])
      .filter(p => p[1] !== undefined && p[1] !== null)
      .sort((a, b) => a[0] - b[0]);

    // Added/Removed markers only if this is the current series
    const execInfo = props.executors.find(e => e.executorId === execId);
    const markPoints = [];
    if (execInfo) {
      markPoints.push({
        name: 'Added',
        coord: [Number(execInfo.addTime), 0],
        symbol: 'path://M12 2L4.5 20.29L5.21 21L12 18L18.79 21L19.5 20.29L12 2Z',
        symbolSize: 12,
        itemStyle: { color: '#27ae60' },
        label: { show: false },
        tooltip: {
          formatter: `<b>Executor ${execId}</b>: Added<br/>Time: ${formatDateTime(execInfo.addTime)}<br/>Host: ${execInfo.host}`
        }
      });
      if (execInfo.removeTime) {
        markPoints.push({
          name: 'Removed',
          coord: [Number(execInfo.removeTime), 0],
          symbol: 'path://M12 22L19.5 3.71L18.79 3L12 6L5.21 3L4.5 3.71L12 22Z',
          symbolSize: 12,
          itemStyle: { color: '#e74c3c' },
          label: { show: false },
          tooltip: {
            formatter: `<b>Executor ${execId}</b>: Removed<br/>Time: ${formatDateTime(execInfo.removeTime)}<br/>Reason: ${execInfo.execLossReason || 'N/A'}`
          }
        });
      }
    }

    return {
      name: seriesName,
      type: 'line',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: points,
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      emphasis: { focus: 'series' },
      markPoint: { data: markPoints }
    };
  });

  const option = {
    title: [
      { text: 'Executor Lifecycle Status', left: 'center', top: 0, textStyle: { fontSize: 12, color: '#666' } },
      { text: `Executor ${metricInfo.label} Distribution`, left: 'center', top: '30%', textStyle: { fontSize: 12, color: '#666' } }
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { 
        type: 'line',
        link: { xAxisIndex: 'all' },
        label: { show: false },
        lineStyle: { color: '#aaa', width: 1, type: 'dashed' }
      },
      formatter: (params) => {
        if (!params || params.length === 0) return '';
        
        let timestamp = params[0].value[0];
        if (params[0].seriesName === 'Lifecycle Status') {
          timestamp = params[0].value[1];
        }
        
        let res = `<div style="font-weight:bold;margin-bottom:5px;border-bottom:1px solid #eee;padding-bottom:3px;">${formatDateTime(timestamp)}</div>`;
        
        const events = [];
        props.executors.forEach(e => {
          const windowMs = 30000;
          if (Math.abs(Number(e.addTime) - timestamp) < windowMs) {
            events.push(`<div style="color:#27ae60;font-size:0.8rem;margin-bottom:2px;"><b>Executor ${e.executorId}</b>: Added</div>`);
          }
          if (e.removeTime && Math.abs(Number(e.removeTime) - timestamp) < windowMs) {
            events.push(`<div style="color:#e74c3c;font-size:0.8rem;margin-bottom:2px;"><b>Executor ${e.executorId}</b>: Removed${e.execLossReason ? ' (' + e.execLossReason + ')' : ''}</div>`);
          }
        });

        if (events.length > 0) {
          res += `<div style="margin-bottom:8px;background:#f8f9fa;padding:4px;border-radius:4px;">${events.join('')}</div>`;
        }

        const perfParams = params.filter(p => p.axisIndex === 1);
        if (perfParams.length > 0) {
          const isBytes = metricKey.includes('hdfs') || metricKey.includes('shuffle');
          perfParams.forEach(p => {
            const val = p.value[1];
            const formattedVal = (val === undefined || val === null) ? '-' : (isBytes ? formatBytes(val) : formatNum(val));
            res += `<div style="display:flex;justify-content:space-between;gap:20px;font-size:0.85rem;">
                      <span>${p.marker} ${p.seriesName}:</span>
                      <span style="font-weight:600;">${formattedVal}</span>
                    </div>`;
          });
        }
        return res;
      }
    },
    legend: {
      type: 'scroll',
      bottom: 45,
      data: perfSeries.map(s => s.name)
    },
    axisPointer: { link: { xAxisIndex: 'all' } },
    grid: [
      { left: 80, right: 40, top: '8%', height: '18%' },
      { left: 80, right: 40, top: '38%', height: '42%' }
    ],
    xAxis: [
      {
        type: 'time', gridIndex: 0, min, max, axisLabel: { show: false }, axisTick: { show: false }, axisLine: { show: false },
        splitLine: { show: true, lineStyle: { type: 'dashed', color: '#eee' } },
        axisPointer: { show: true, label: { show: false }, lineStyle: { type: 'dashed', color: '#aaa' } }
      },
      {
        type: 'time', gridIndex: 1, min, max, 
        axisLabel: { 
          fontSize: 10, color: '#999',
          formatter: (value) => {
            const date = new Date(value);
            return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
          }
        },
        splitLine: { show: true, lineStyle: { type: 'dashed', color: '#eee' } },
        axisPointer: { show: true, label: { show: false }, lineStyle: { type: 'dashed', color: '#aaa' } }
      }
    ],
    yAxis: [
      {
        type: 'category', gridIndex: 0, data: execIds, 
        axisLabel: { fontSize: 10, margin: 12, color: '#999' }, axisTick: { show: false }, splitLine: { show: true },
        axisLine: { show: true, lineStyle: { type: 'dashed', color: '#eee' } }
      },
      {
        type: 'value', gridIndex: 1,
        axisLabel: {
          formatter: (val) => {
            const isBytes = metricKey.includes('hdfs') || metricKey.includes('shuffle');
            return isBytes && val > 1000 ? formatBytes(val) : formatNum(val);
          },
          fontSize: 10, margin: 12, color: '#999'
        },
        axisLine: { show: true, lineStyle: { type: 'dashed', color: '#eee' } }
      }
    ],
    dataZoom: [
      {
        type: 'slider', xAxisIndex: [0, 1], start: 0, end: 100, bottom: 5, height: 20,
        handleStyle: { color: '#3498db' }, zoomLock: isZoomLocked.value
      },
      { type: 'inside', xAxisIndex: [0, 1], disabled: isZoomLocked.value }
    ],
    series: [
      {
        name: 'Lifecycle Status',
        type: 'custom',
        renderItem: (params, api) => {
          const categoryIndex = api.value(0);
          const start = api.coord([api.value(1), categoryIndex]);
          const end = api.coord([api.value(2), categoryIndex]);
          const height = api.size([0, 1])[1] * 0.6;
          const x = start[0];
          const y = start[1] - height / 2;
          const width = end[0] - start[0];
          return {
            type: 'rect',
            shape: echarts.graphic.clipRectByRect({ 
              x: x, 
              y: y, 
              width: Math.max(1, width), 
              height: height 
            }, params.coordSys),
            style: api.style()
          };
        },
        encode: { x: [1, 2], y: 0 },
        data: lifecycleData
      },
      ...perfSeries
    ]
  };

  myChart.setOption(option, true);
};

const toggleZoomLock = () => {
  isZoomLocked.value = !isZoomLocked.value;
  if (myChart) {
    myChart.setOption({
      dataZoom: [
        { type: 'slider', zoomLock: isZoomLocked.value },
        { type: 'inside', disabled: isZoomLocked.value }
      ]
    });
  }
};

defineExpose({ isZoomLocked, toggleZoomLock });

watch(() => props.executors, updateChart, { deep: true });

onMounted(() => {
  initChart();
  fetchMetricData(currentMetric.value);
});

onUnmounted(() => myChart && myChart.dispose());
</script>

<style scoped>
.combined-chart-container { display: flex; flex-direction: column; gap: 10px; }
.chart-controls { display: flex; justify-content: flex-end; padding: 0 10px; }
.metric-tabs { display: flex; background: #f1f3f5; padding: 3px; border-radius: 6px; gap: 4px; }
.metric-tab { 
  display: flex; align-items: center; gap: 6px;
  border: none; background: none; padding: 4px 12px; font-size: 0.75rem; font-weight: 600; color: #666; cursor: pointer; border-radius: 4px; 
}
.metric-tab.active { background: white; color: #3498db; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.chart-box { height: 550px; width: 100%; }

.mini-spinner {
  width: 10px; height: 10px; border: 2px solid #3498db; border-top: 2px solid transparent; border-radius: 50%; animation: spin 1s linear infinite;
}
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>
