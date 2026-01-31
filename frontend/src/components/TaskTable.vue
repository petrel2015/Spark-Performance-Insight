<template>
  <div class="task-table-container">
    <div class="table-header">
      <div class="header-left">
        <h4>Tasks List <small>(Total: {{ totalTasks }})</small></h4>
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

    <table class="styled-table">
      <thead>
        <tr>
          <th @click="handleSort('taskId', $event)" class="sortable" style="width: 80px;">
            ID {{ getSortIcon('taskId') }}
          </th>
          <th class="sortable" @click="handleSort('taskIndex', $event)" style="width: 80px;">
            Index {{ getSortIcon('taskIndex') }}
          </th>
          <th @click="handleSort('host', $event)" class="sortable">
            Host {{ getSortIcon('host') }}
          </th>
          <th @click="handleSort('executorId', $event)" class="sortable" style="width: 100px;">
            Executor {{ getSortIcon('executorId') }}
          </th>
          <th @click="handleSort('duration', $event)" class="sortable" style="width: 120px;">
            Duration {{ getSortIcon('duration') }}
          </th>
          <th @click="handleSort('gcTime', $event)" class="sortable" style="width: 120px;">
            GC Time {{ getSortIcon('gcTime') }}
          </th>
          <th @click="handleSort('inputBytes', $event)" class="sortable" style="width: 120px;">
            Input {{ getSortIcon('inputBytes') }}
          </th>
          <th @click="handleSort('shuffleReadBytes', $event)" class="sortable" style="width: 120px;">
            Shuffle Read {{ getSortIcon('shuffleReadBytes') }}
          </th>
          <th @click="handleSort('status', $event)" class="sortable" style="width: 100px;">
            Status {{ getSortIcon('status') }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.taskId">
          <td>{{ task.taskId }}</td>
          <td>{{ task.taskIndex }}</td>
          <td>{{ task.host }}</td>
          <td>{{ task.executorId }}</td>
          <td>{{ formatDuration(task.duration) }}</td>
          <td :class="{ 'high-gc': task.gcTime > task.duration * 0.1 }">{{ formatDuration(task.gcTime) }}</td>
          <td>{{ formatBytes(task.inputBytes) }}</td>
          <td>{{ formatBytes(task.shuffleReadBytes) }}</td>
          <td>
            <span :class="'status-badge status-' + (task.status || 'UNKNOWN').toLowerCase()">
              {{ task.status || 'UNKNOWN' }}
            </span>
          </td>
        </tr>
        <tr v-if="tasks.length === 0">
          <td colspan="9" style="text-align: center; padding: 40px;">No tasks found for this stage.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getStageTasks } from '../api';

const props = defineProps({
  appId: String,
  stageId: Number
});

const tasks = ref([]);
const totalTasks = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const sorts = ref([]); // [{ field, dir }]

const fetchTasks = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getStageTasks(props.appId, props.stageId, currentPage.value, pageSize.value, sortStr);
    // 增加兼容性处理：判断返回的是 PageResponse 对象还是旧版的 List 数组
    if (res.data && res.data.items) {
      tasks.value = res.data.items;
      totalTasks.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else if (Array.isArray(res.data)) {
      // 兼容旧版后端接口
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

const getSortIcon = (field) => {
  const s = sorts.value.find(x => x.field === field);
  if (!s) return '↕';
  return s.dir === 'asc' ? '↑' : '↓';
};

const formatDuration = (ms) => {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1000) return ms + ' ms';
  return (ms / 1000).toFixed(2) + ' s';
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

onMounted(fetchTasks);

// Re-fetch when stage changes
watch(() => props.stageId, () => {
  currentPage.value = 1;
  fetchTasks();
});
</script>

<style scoped>
.task-table-container { 
  margin-top: 1rem; 
  background: white; 
  border-radius: 8px; 
  padding: 1.5rem; 
  box-shadow: 0 2px 8px rgba(0,0,0,0.05); 
}

.table-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  margin-bottom: 1.5rem; 
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.header-left h4 { margin: 0; color: #2c3e50; }
.header-left small { color: #7f8c8d; font-weight: normal; margin-left: 8px; }

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

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.styled-table th, .styled-table td { padding: 12px 8px; text-align: left; border-bottom: 1px solid #eee; }
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