<template>
  <div class="table-wrapper">
    <table class="styled-table">
      <thead>
        <tr>
          <th style="min-width: 100px;">Executor</th>
          <th style="min-width: 80px;">Tasks</th>
          <th v-for="col in visibleCols" :key="col.key" :style="{ minWidth: col.width }">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in summary" :key="row.executorId">
          <td><strong>{{ row.executorId }}</strong></td>
          <td>{{ row.taskCount }}</td>
          <td v-for="col in visibleCols" :key="col.key">
            <template v-if="col.type === 'time'">
              {{ formatTime(row[col.field]) }}
            </template>
            <template v-else-if="col.type === 'bytes'">
              {{ formatBytes(row[col.field]) }}
            </template>
            <template v-else-if="col.type === 'nanos'">
              {{ formatTime(row[col.field] / 1000000) }}
            </template>
            <template v-else-if="col.type === 'composite'">
              {{ formatBytes(row[col.subFields[0]]) }} / {{ formatNum(row[col.subFields[1]]) }}
            </template>
            <template v-else>
              {{ row[col.field] }}
            </template>
          </td>
        </tr>
        <tr v-if="summary.length === 0">
          <td :colspan="visibleCols.length + 2" style="text-align: center; padding: 20px;">No executor summary available.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { formatTime, formatBytes, formatNum } from '../../utils/format';

const props = defineProps({
  summary: { type: Array, default: () => [] },
  visibleMetrics: { type: Array, default: () => [] }
});

const columnDefs = [
  { key: 'task_deserialization_time', field: 'executorDeserializeTime', label: 'Deserialization', type: 'time', width: '100px' },
  { key: 'duration', field: 'duration', label: 'Duration', type: 'time', width: '100px' },
  { key: 'gc_time', field: 'gcTime', label: 'GC Time', type: 'time', width: '90px' },
  { key: 'result_serialization_time', field: 'resultSerializationTime', label: 'Result Ser', type: 'time', width: '100px' },
  { key: 'getting_result_time', field: 'getting_result_time', label: 'Getting Result', type: 'time', width: '110px' },
  { key: 'scheduler_delay', field: 'schedulerDelay', label: 'Scheduler Delay', type: 'time', width: '110px' },
  { key: 'peak_execution_memory', field: 'peakExecutionMemory', label: 'Peak Memory', type: 'bytes', width: '100px' },
  { key: 'memory_spill', field: 'memoryBytesSpilled', label: 'Spill (mem)', type: 'bytes', width: '90px' },
  { key: 'disk_spill', field: 'diskBytesSpilled', label: 'Spill (disk)', type: 'bytes', width: '90px' },
  { key: 'input', label: 'Input Size / Records', type: 'composite', subFields: ['inputBytes', 'inputRecords'], width: '200px' },
  { key: 'shuffle_write', label: 'Shuffle Write Size / Records', type: 'composite', subFields: ['shuffleWriteBytes', 'shuffleWriteRecords'], width: '220px' },
  { key: 'shuffle_write_time', field: 'shuffleWriteTime', label: 'Shuffle Write Time', type: 'nanos', width: '120px' }
];

const visibleCols = computed(() => {
  return columnDefs.filter(col => props.visibleMetrics.includes(col.key));
});
</script>

<style scoped>
.table-wrapper { overflow-x: auto; width: 100%; }
.styled-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.styled-table th, .styled-table td { padding: 12px 8px; text-align: left; border-bottom: 1px solid #eee; white-space: nowrap; }
.styled-table th { background: #f8f9fa; color: #333; font-weight: 600; }
.styled-table tbody tr:hover { background-color: #f7fbff; }
</style>
