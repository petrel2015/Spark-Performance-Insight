<template>
  <div class="task-table-container">
    <div class="table-header-toolbar">
      <div class="header-left">
        <span>Total: {{ totalTasks }}</span>
      </div>
      
      <div class="pagination-controls">
        <div class="page-size-selector">
          <span>Rows per page:</span>
          <select v-model="pageSize" @change="handleSizeChange">
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
        </div>

        <div class="page-nav">
          <button @click="jumpToPage(1)" :disabled="currentPage === 1" title="First Page">«</button>
          <button @click="changePage(-1)" :disabled="currentPage === 1" title="Previous Page">‹</button>
          
          <div class="page-jump">
            <input type="number" 
                   v-model.number="jumpPageInput" 
                   @keyup.enter="handleJump"
                   class="jump-input" 
                   min="1" 
                   :max="totalPages" />
            <span class="total-pages">/ {{ totalPages }}</span>
          </div>

          <button @click="changePage(1)" :disabled="currentPage === totalPages" title="Next Page">›</button>
          <button @click="jumpToPage(totalPages)" :disabled="currentPage === totalPages" title="Last Page">»</button>
        </div>
      </div>
    </div>

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
            <th v-for="col in columns" 
                :key="col.field"
                @click="handleSort(col.field, $event)" 
                class="sortable" 
                :style="{ minWidth: col.width }">
              {{ col.label }} {{ getSortIcon(col.field) }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.taskId">
            <td v-for="col in columns" :key="col.field">
              <template v-if="col.field === 'status'">
                <span :class="'status-badge status-' + (task.status || 'UNKNOWN').toLowerCase()">
                  {{ task.status || 'UNKNOWN' }}
                </span>
              </template>
              <template v-else-if="col.type === 'time'">
                <span :class="{ 'high-gc': col.field === 'gcTime' && task.gcTime > task.duration * 0.1 }">
                  {{ formatTime(task[col.field]) }}
                </span>
              </template>
              <template v-else-if="col.type === 'bytes'">
                {{ formatBytes(task[col.field]) }}
              </template>
              <template v-else-if="col.type === 'nanos'">
                {{ formatTime(task[col.field] / 1000000) }}
              </template>
              <template v-else-if="col.field === 'input'">
                {{ formatBytes(task.inputBytes) }} / {{ formatNum(task.inputRecords) }}
              </template>
              <template v-else-if="col.field === 'output'">
                {{ formatBytes(task.outputBytes) }} / {{ formatNum(task.outputRecords) }}
              </template>
              <template v-else-if="col.field === 'shuffle_read'">
                {{ formatBytes(task.shuffleReadBytes) }} / {{ formatNum(task.shuffleReadRecords) }}
              </template>
              <template v-else-if="col.field === 'shuffle_write'">
                {{ formatBytes(task.shuffleWriteBytes) }} / {{ formatNum(task.shuffleWriteRecords) }}
              </template>
              <template v-else>
                {{ task[col.field] }}
              </template>
            </td>
          </tr>
          <tr v-if="tasks.length === 0">
            <td :colspan="columns.length" style="text-align: center; padding: 40px;">No tasks found for this stage.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getStageTasks } from '../../api';
import { formatTime, formatBytes, formatNum } from '../../utils/format';
import { computed } from 'vue';

const props = defineProps({
  appId: String,
  stageId: Number,
  attemptId: {
    type: Number,
    default: null
  },
  visibleMetrics: {
    type: Array,
    default: () => []
  }
});

const tasks = ref([]);
const totalTasks = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const sorts = ref([{ field: 'taskId', dir: 'asc' }]); // Default sort by ID

// 基础列（始终显示）
const baseColumns = [
  { field: 'taskId', label: 'ID', width: '60px' },
  { field: 'taskIndex', label: 'Index', width: '60px' },
  { field: 'host', label: 'Host', width: '120px' },
  { field: 'executorId', label: 'Executor', width: '80px' },
];

// 动态指标列映射
const metricColumnsMap = {
  'task_deserialization_time': { field: 'executorDeserializeTime', label: 'Task Deserialization Time', width: '150px', type: 'time' },
  'duration': { field: 'duration', label: 'Duration', width: '100px', type: 'time' },
  'gc_time': { field: 'gcTime', label: 'GC Time', width: '90px', type: 'time' },
  'result_serialization_time': { field: 'resultSerializationTime', label: 'Result Serialization Time', width: '160px', type: 'time' },
  'getting_result_time': { field: 'gettingResultTime', label: 'Getting Result Time', width: '130px', type: 'time' },
  'scheduler_delay': { field: 'schedulerDelay', label: 'Scheduler Delay', width: '130px', type: 'time' },
  'peak_execution_memory': { field: 'peakExecutionMemory', label: 'Peak Execution Memory', width: '150px', type: 'bytes' },
  'memory_spill': { field: 'memoryBytesSpilled', label: 'Spill (memory)', width: '110px', type: 'bytes' },
  'disk_spill': { field: 'diskBytesSpilled', label: 'Spill (disk)', width: '110px', type: 'bytes' },
  'input': { field: 'input', label: 'Input Size / Records', width: '200px', type: 'composite' },
  'output': { field: 'output', label: 'Output Size / Records', width: '200px', type: 'composite' },
  'shuffle_read': { field: 'shuffle_read', label: 'Shuffle Read Size / Records', width: '220px', type: 'composite' },
  'shuffle_write': { field: 'shuffle_write', label: 'Shuffle Write Size / Records', width: '220px', type: 'composite' },
  'shuffle_write_time': { field: 'shuffleWriteTime', label: 'Shuffle Write Time', width: '130px', type: 'nanos' }
};

const columns = computed(() => {
  const cols = [...baseColumns];
  props.visibleMetrics.forEach(key => {
    if (metricColumnsMap[key]) {
      cols.push(metricColumnsMap[key]);
    }
  });
  cols.push({ field: 'status', label: 'Status', width: '80px' });
  return cols;
});

const isColumnVisible = (field) => columns.value.some(c => c.field === field);
const getMetricType = (field) => columns.value.find(c => c.field === field)?.type;

const fetchTasks = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getStageTasks(props.appId, props.stageId, currentPage.value, pageSize.value, sortStr, props.attemptId);
    if (res.data && res.data.items) {
      tasks.value = res.data.items;
      totalTasks.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else if (Array.isArray(res.data)) {
      tasks.value = res.data;
      totalTasks.value = res.data.length;
      totalPages.value = 1;
    } else {
      tasks.value = [];
      totalTasks.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch tasks", err);
  }
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchTasks();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchTasks();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => {
  jumpToPage(jumpPageInput.value);
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchTasks();
};

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
    sorts.value.push({ field, dir: 'asc' });
  }
  currentPage.value = 1;
  fetchTasks();
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
  currentPage.value = 1;
  fetchTasks();
};

const clearSorts = () => {
  sorts.value = [];
  currentPage.value = 1;
  fetchTasks();
};

const getColumnLabel = (field) => {
  const col = columns.value.find(c => c.field === field);
  if (col) return col.label;
  // Fallback to searching in the map
  const metricKey = Object.keys(metricColumnsMap).find(k => metricColumnsMap[k].field === field);
  return metricKey ? metricColumnsMap[metricKey].label : field;
};

const getSortIcon = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  if (index === -1) return '↕';
  const s = sorts.value[index];
  const icon = s.dir === 'asc' ? '↑' : '↓';
  if (sorts.value.length > 1) {
    return `${icon}${index + 1}`;
  }
  return icon;
};

onMounted(fetchTasks);

watch(() => props.stageId, () => {
  currentPage.value = 1;
  fetchTasks();
});
</script>

<style scoped>
.task-table-container { 
  width: 100%;
}

.table-header-toolbar { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  margin-bottom: 1rem; 
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.header-left span { color: #7f8c8d; font-size: 0.9rem; }

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

.sort-hint {
  margin-left: auto;
  color: #888;
  font-style: italic;
  font-size: 0.8rem;
}

.pagination-controls { 
  display: flex; 
  gap: 24px; 
  align-items: center; 
}

.page-size-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: #666;
}

.page-size-selector select {
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.page-nav { 
  display: flex; 
  gap: 8px; 
  align-items: center; 
}

.page-nav button { 
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer; 
  border: 1px solid #ddd; 
  border-radius: 4px; 
  background: #fff; 
  color: #555;
  transition: all 0.2s;
}

.page-nav button:hover:not(:disabled) {
  border-color: #3498db;
  color: #3498db;
  background: #f7fbff;
}

.page-nav button:disabled {
  background: #f5f5f5;
  color: #ccc;
  cursor: not-allowed;
}

.page-jump {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0 8px;
}

.jump-input {
  width: 45px;
  padding: 4px 6px;
  text-align: center;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.total-pages {
  color: #999;
  font-size: 0.9rem;
}

.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; min-width: 1200px; }
.styled-table th, .styled-table td { padding: 12px 8px; text-align: left; border-bottom: 1px solid #eee; white-space: nowrap; }

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.styled-table th { background: #f8f9fa; color: #333; font-weight: 600; }
.styled-table th.sortable { cursor: pointer; user-select: none; }
.styled-table th.sortable:hover { background: #edf2f7; color: #3498db; }

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  text-transform: uppercase;
  font-weight: bold;
}

.status-success { background: #e8f5e9; color: #2e7d32; }
.status-failed { background: #ffebee; color: #c62828; }
.status-running { background: #fff3e0; color: #ef6c00; }

.high-gc { color: #e67e22; font-weight: bold; }
</style>