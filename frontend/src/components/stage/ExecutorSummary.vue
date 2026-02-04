<template>
  <div class="executor-summary-container">
    <!-- Active Sorts Display -->
    <div v-if="sorts.length > 0" class="active-sorts-bar">
      <span class="sort-label">Sort by:</span>
      <div class="sort-tags">
        <span v-for="(sort, index) in sorts" :key="sort.field" class="sort-tag">
          {{ getColumnLabel(sort.field) }} 
          <span class="sort-dir">{{ sort.dir === 'asc' ? 'ASC' : 'DESC' }}</span>
          <span @click="removeSort(index)" class="remove-sort" title="Remove sort">×</span>
        </span>
      </div>
      <button @click="clearSorts" class="clear-sort-btn">Clear All</button>
    </div>

    <div class="table-wrapper">
      <table class="styled-table">
        <thead>
        <tr>
          <th @click="handleSort('executorId', $event)" class="sortable" style="min-width: 100px;">
            Executor {{ getSortIcon('executorId') }}
          </th>
          <th @click="handleSort('taskCount', $event)" class="sortable" style="min-width: 80px;">
            Tasks {{ getSortIcon('taskCount') }}
          </th>
          <th v-for="col in visibleCols"
              :key="col.key"
              @click="handleSort(col.field, $event)"
              class="sortable"
              :style="{ minWidth: col.width }">
            {{ col.label }} {{ getSortIcon(col.field) }}
          </th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="row in sortedSummary" :key="row.executorId">
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
          <td :colspan="visibleCols.length + 2" style="text-align: center; padding: 20px;">No executor summary
            available.
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import {ref, computed} from 'vue';
import {formatTime, formatBytes, formatNum} from '../../utils/format';

const props = defineProps({
  summary: {type: Array, default: () => []},
  visibleMetrics: {type: Array, default: () => []}
});

const sorts = ref([{field: 'executorId', dir: 'asc'}]);

const columnDefs = [
  {
    key: 'task_deserialization_time',
    field: 'executorDeserializeTime',
    label: 'Task Deserialization Time',
    type: 'time',
    width: '150px'
  },
  {key: 'duration', field: 'duration', label: 'Duration', type: 'time', width: '100px'},
  {key: 'gc_time', field: 'gcTime', label: 'GC Time', type: 'time', width: '90px'},
  {
    key: 'result_serialization_time',
    field: 'resultSerializationTime',
    label: 'Result Serialization Time',
    type: 'time',
    width: '160px'
  },
  {key: 'getting_result_time', field: 'gettingResultTime', label: 'Getting Result Time', type: 'time', width: '130px'},
  {key: 'scheduler_delay', field: 'schedulerDelay', label: 'Scheduler Delay', type: 'time', width: '130px'},
  {
    key: 'peak_execution_memory',
    field: 'peakExecutionMemory',
    label: 'Peak Execution Memory',
    type: 'bytes',
    width: '150px'
  },
  {key: 'memory_spill', field: 'memoryBytesSpilled', label: 'Spill (memory)', type: 'bytes', width: '110px'},
  {key: 'disk_spill', field: 'diskBytesSpilled', label: 'Spill (disk)', type: 'bytes', width: '110px'},
  {
    key: 'input',
    field: 'inputBytes',
    label: 'Input Size / Records',
    type: 'composite',
    subFields: ['inputBytes', 'inputRecords'],
    width: '200px'
  },
  {
    key: 'output',
    field: 'outputBytes',
    label: 'Output Size / Records',
    type: 'composite',
    subFields: ['outputBytes', 'outputRecords'],
    width: '200px'
  },
  {
    key: 'shuffle_read',
    field: 'shuffleReadBytes',
    label: 'Shuffle Read Size / Records',
    type: 'composite',
    subFields: ['shuffleReadBytes', 'shuffleReadRecords'],
    width: '220px'
  },
  {
    key: 'shuffle_write',
    field: 'shuffleWriteBytes',
    label: 'Shuffle Write Size / Records',
    type: 'composite',
    subFields: ['shuffleWriteBytes', 'shuffleWriteRecords'],
    width: '220px'
  },
  {key: 'shuffle_write_time', field: 'shuffleWriteTime', label: 'Shuffle Write Time', type: 'nanos', width: '130px'}
];

const visibleCols = computed(() => {
  return columnDefs.filter(col => props.visibleMetrics.includes(col.key));
});

const sortedSummary = computed(() => {
  if (sorts.value.length === 0) return props.summary;

  return [...props.summary].sort((a, b) => {
    for (const sort of sorts.value) {
      let valA = a[sort.field];
      let valB = b[sort.field];
      if (valA === valB) continue;
      if (valA == null) return 1;
      if (valB == null) return -1;

      const res = valA < valB ? -1 : 1;
      return sort.dir === 'asc' ? res : -res;
    }
    return 0;
  });
});

const handleSort = (field, event) => {
  const existingIndex = sorts.value.findIndex(s => s.field === field);
  const isShift = event.shiftKey;

  if (existingIndex > -1) {
    const existing = sorts.value[existingIndex];
    if (existing.dir === 'asc') {
      existing.dir = 'desc';
    } else {
      sorts.value.splice(existingIndex, 1);
    }
  } else {
    if (!isShift) {
      sorts.value = [];
    }
    sorts.value.push({field, dir: 'asc'});
  }
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
};

const clearSorts = () => {
  sorts.value = [];
};

const getSortIcon = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  if (index === -1) return '↕';
  const s = sorts.value[index];
  const icon = s.dir === 'asc' ? '↑' : '↓';
  return sorts.value.length > 1 ? `${icon}${index + 1}` : icon;
};

const getColumnLabel = (field) => {
  if (field === 'executorId') return 'Executor';
  if (field === 'taskCount') return 'Tasks';
  const col = columnDefs.find(c => c.field === field);
  return col ? col.label : field;
};
</script>

<style scoped>
.executor-summary-container {
  width: 100%;
}

.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.styled-table th, .styled-table td {
  padding: 12px 8px;
  text-align: left;
  border-bottom: 1px solid #eee;
  white-space: nowrap;
}

.styled-table th {
  background: #f8f9fa;
  color: #333;
  font-weight: 600;
}

.styled-table th.sortable {
  cursor: pointer;
  user-select: none;
}

.styled-table th.sortable:hover {
  background: #edf2f7;
  color: #3498db;
}

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

/* Active Sorts Bar (Sync from TaskTable) */
.active-sorts-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 1rem;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 6px;
  font-size: 0.85rem;
  border: 1px solid #eee;
}

.sort-label {
  font-weight: 600;
  color: #555;
}

.sort-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.sort-tag {
  display: inline-flex;
  align-items: center;
  background: #e3f2fd;
  color: #1565c0;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.8rem;
  border: 1px solid #bbdefb;
}

.sort-dir {
  margin-left: 4px;
  font-size: 0.7rem;
  opacity: 0.8;
  font-weight: bold;
}

.remove-sort {
  margin-left: 6px;
  cursor: pointer;
  font-weight: bold;
  opacity: 0.6;
}

.remove-sort:hover {
  opacity: 1;
  color: #c62828;
}

.clear-sort-btn {
  background: none;
  border: none;
  color: #666;
  text-decoration: underline;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 0 4px;
}

.clear-sort-btn:hover {
  color: #d32f2f;
}
</style>
