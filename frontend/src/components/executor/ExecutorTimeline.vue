<template>
  <div class="timeline-container">
    <div ref="chartDom" class="timeline-chart"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { formatTime, formatDateTime } from '../../utils/format';

const props = defineProps({
  executors: { type: Array, default: () => [] }
});

const chartDom = ref(null);
let chart = null;
let resizeObserver = null;

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
      const start = new Date(e.addTime).getTime();
      let end = e.removeTime ? new Date(e.removeTime).getTime() : now;
      
      if (start < minTime) minTime = start;
      if (end > maxTime) maxTime = end;

      data.push({
          name: e.executorId,
          value: [
              index, 
              start, 
              end, 
              e.isActive,
              e.executorId,
              e.host
          ],
          itemStyle: {
              color: e.isActive ? '#4caf50' : '#bdc3c7', // Green for active, Silver for dead
              borderWidth: 1,
              borderColor: e.isActive ? '#27ae60' : '#95a5a6'
          }
      });
  });
  
  // Pad the time range a bit for better view
  const duration = maxTime - minTime;
  const timePadding = duration > 0 ? duration * 0.05 : 10000; // 10s default padding if no duration
  minTime -= timePadding;
  maxTime += timePadding;

  // Set height based on number of executors
  const rowHeight = 40;
  const chartHeight = Math.max(250, executors.length * rowHeight + 100);
  chartDom.value.style.height = `${chartHeight}px`;
  
  if (chart) {
    chart.resize();
  }

  const option = {
      tooltip: {
          trigger: 'item',
          formatter: function (params) {
              const v = params.value;
              const start = formatDateTime(v[1]);
              const end = v[3] ? 'Active' : formatDateTime(v[2]);
              const durationMs = v[2] - v[1];
              const dStr = formatTime(durationMs);
              
              return `
                <div style="font-size:12px; padding: 4px;">
                  <b style="font-size:13px;">Executor ${v[4]}</b><br/>
                  <hr style="margin: 5px 0; border: 0; border-top: 1px solid #eee;"/>
                  Host: ${v[5]}<br/>
                  Status: ${v[3] ? '<span style="color:#27ae60;font-weight:bold;">Active</span>' : '<span style="color:#7f8c8d;font-weight:bold;">Dead</span>'}<br/>
                  Start: ${start}<br/>
                  End: ${end}<br/>
                  Duration: ${dStr}
                </div>
              `;
          }
      },
      grid: {
          top: 60,
          bottom: 80,
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
          axisLine: { show: true, lineStyle: { color: '#eee' } },
          axisTick: { show: false },
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
              bottom: 20,
              height: 24,
              minValueSpan: 5 * 60 * 1000, // 5 minutes
              maxValueSpan: 60 * 60 * 1000, // 1 hour
              handleIcon: 'path://M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
              handleSize: '80%',
              handleStyle: {
                  color: '#fff',
                  shadowBlur: 3,
                  shadowColor: 'rgba(0, 0, 0, 0.6)',
                  shadowOffsetX: 2,
                  shadowOffsetY: 2
              }
          },
          {
              type: 'inside',
              xAxisIndex: [0],
              minValueSpan: 5 * 60 * 1000,
              maxValueSpan: 60 * 60 * 1000
          }
      ],
      series: [
          {
              type: 'custom',
              renderItem: function (params, api) {
                  const categoryIndex = api.value(0);
                  const start = api.coord([api.value(1), categoryIndex]);
                  const end = api.coord([api.value(2), categoryIndex]);
                  
                  // Coordinate might be null if out of range
                  if (!start || !end) return;

                  const barHeight = api.size([0, 1])[1] * 0.6;
                  const width = Math.max(end[0] - start[0], 1);
                  
                  return {
                      type: 'rect',
                      shape: {
                          x: start[0],
                          y: start[1] - barHeight / 2,
                          width: width,
                          height: barHeight,
                          r: 3
                      },
                      style: api.style({
                          fill: api.value(3) ? '#4caf50' : '#bdc3c7',
                          stroke: api.value(3) ? '#27ae60' : '#95a5a6',
                          lineWidth: 1
                      })
                  };
              },
              encode: {
                  x: [1, 2],
                  y: 0
              },
              data: data
          }
      ]
  };

  chart.setOption(option);
};

onMounted(() => {
  renderChart();
  resizeObserver = new ResizeObserver(() => {
    chart && chart.resize();
  });
  if (chartDom.value) resizeObserver.observe(chartDom.value.parentElement);
});

watch(() => props.executors, renderChart, { deep: true });

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