<template>
  <div class="timeline-container">
    <div ref="chartDom" class="timeline-chart"></div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, onBeforeUnmount, nextTick} from 'vue';
import * as echarts from 'echarts';
import {formatTime, formatDateTime} from '../../utils/format';

const props = defineProps({
  executors: {type: Array, default: () => []}
});

const chartDom = ref(null);
let chart = null;
let resizeObserver = null;
const isZoomLocked = ref(true);

const toggleZoomLock = () => {
  isZoomLocked.value = !isZoomLocked.value;
  updateZoomLockState();
};

const updateZoomLockState = () => {
  if (!chart) return;
  const zoomConfig = [
    {
      type: 'slider',
      xAxisIndex: [0],
      zoomLock: isZoomLocked.value
    },
    {
      type: 'inside',
      xAxisIndex: [0],
      disabled: isZoomLocked.value
    }
  ];
  chart.setOption({ dataZoom: zoomConfig });
};

defineExpose({
  isZoomLocked,
  toggleZoomLock
});

const renderChart = async () => {
  await nextTick();
  if (!chartDom.value) return;
  if (!chart) chart = echarts.init(chartDom.value);

  const executors = [...props.executors].sort((a, b) => {
    // Sort by addTime
    const tA = new Date(a.addTime).getTime();
    const tB = new Date(b.addTime).getTime();
    return tA - tB;
  });

  if (executors.length === 0) {
    chart.clear();
    return;
  }

  const executorIds = executors.map(e => e.executorId);
  const data = [];

  const now = new Date().getTime();
  let minTime = now;
  let maxTime = 0;

  executors.forEach((e, index) => {
    const addTime = new Date(e.addTime).getTime();
    if (addTime < minTime) minTime = addTime;
    if (addTime > maxTime) maxTime = addTime;

    data.push({
      name: `Executor ${e.executorId} Added`,
      value: [index, addTime, 0, e] // Type 0: Added
    });

    if (e.removeTime) {
      const removeTime = new Date(e.removeTime).getTime();
      if (removeTime > maxTime) maxTime = removeTime;
      data.push({
        name: `Executor ${e.executorId} Removed`,
        value: [index, removeTime, 1, e] // Type 1: Removed
      });
    }
  });

  // Pad the time range a bit for better view
  const duration = maxTime - minTime;
  const timePadding = duration > 0 ? Math.max(duration * 0.05, 5000) : 10000;
  minTime -= timePadding;
  maxTime += timePadding;

  // Set height based on number of executors
  const rowHeight = 30;
  const chartHeight = Math.max(250, executors.length * rowHeight + 120);
  chartDom.value.style.height = `${chartHeight}px`;

  if (chart) {
    chart.resize();
  }

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: function (params) {
        const v = params.value;
        const e = v[3];
        const type = v[2] === 0 ? 'Added' : 'Removed';
        const color = v[2] === 0 ? '#3498db' : '#e74c3c';

        return `
                <div style="font-size:12px; padding: 4px;">
                  <b style="font-size:13px; color: ${color}">Executor ${e.executorId} ${type}</b><br/>
                  <hr style="margin: 5px 0; border: 0; border-top: 1px solid #eee;"/>
                  Host: ${e.host}<br/>
                  Time: ${formatDateTime(v[1])}<br/>
                  ${e.execLossReason ? 'Reason: ' + e.execLossReason : ''}
                </div>
              `;
      }
    },
    grid: {
      top: 60,
      bottom: 60,
      left: 100,
      right: 50,
      containLabel: true
    },
    xAxis: {
      type: 'time',
      min: minTime,
      max: maxTime,
      position: 'top',
      axisLabel: {
        formatter: (val) => {
          const date = new Date(val);
          return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`;
        },
        fontSize: 10,
        color: '#999'
      },
      splitLine: {
        show: true,
        lineStyle: {
          type: 'dashed',
          color: '#f0f0f0'
        }
      }
    },
    yAxis: {
      type: 'category',
      data: executorIds,
      inverse: true,
      axisLabel: {
        interval: 0,
        fontSize: 11,
        color: '#2c3e50',
        fontWeight: 'bold'
      },
      axisLine: {show: true, lineStyle: {color: '#eee'}},
      axisTick: {show: false},
      splitLine: {
        show: true,
        lineStyle: {
          color: '#f9f9f9'
        }
      }
    },
    dataZoom: [
      {
        type: 'slider',
        show: true,
        xAxisIndex: [0],
        bottom: 10,
        height: 20,
        handleSize: '80%'
      },
      {
        type: 'inside',
        xAxisIndex: [0]
      }
    ],
    series: [
      {
        type: 'custom',
        renderItem: function (params, api) {
          const categoryIndex = api.value(0);
          const time = api.value(1);
          const type = api.value(2); // 0: Added, 1: Removed

          const point = api.coord([time, categoryIndex]);
          if (!point) return;

          const barHeight = api.size([0, 1])[1] * 0.7;
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
        },
        encode: {
          x: 1,
          y: 0
        },
        data: data
      }
    ]
  };

  chart.setOption(option);
  updateZoomLockState();
};

onMounted(() => {
  renderChart();
  resizeObserver = new ResizeObserver(() => {
    chart && chart.resize();
  });
  if (chartDom.value) resizeObserver.observe(chartDom.value.parentElement);
});

watch(() => props.executors, renderChart, {deep: true});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
});
</script>

<style scoped>
.timeline-container {
  width: 100%;
  background: white;
  padding: 10px;
  overflow: hidden;
}

.timeline-chart {
  width: 100%;
  min-height: 300px;
}
</style>