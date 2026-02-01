<template>
  <div class="stage-table-view" :class="{ 'plain-mode': plain }">
    <div class="table-header">
      <div class="header-left">
        <h4 v-if="!hideTitle">Stages List <small>(Total: {{ totalStages }})</small></h4>
        <span v-else class="total-count-text">Total: {{ totalStages }} stages</span>
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
      <small class="sort-hint">(Hold <b>Shift</b> + Click headers to sort by multiple columns)</small>
    </div>

    <table class="styled-table">
      <thead>
        <tr>
          <th v-for="col in columns" 
              :key="col.field"
              @click="handleSort(col.field, $event)" 
              class="sortable" 
              :style="{ width: col.width }">
            {{ col.label }} {{ getSortIcon(col.field) }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="stage in stages" :key="stage.stageId">
          <td>{{ stage.stageId }}</td>
          <td>
            <a href="javascript:void(0)" @click="$emit('view-stage-detail', stage.stageId)" class="stage-link">
              {{ stage.stageName }}
            </a>
          </td>
          <td>{{ formatDateTime(stage.submissionTime) }}</td>
          <td>{{ formatDuration(stage.submissionTime, stage.completionTime) }}</td>
          <td>
            <div class="task-progress-wrapper">
              <div class="progress-bar-container">
                <div class="progress-bar-success" 
                     :style="{ width: getProgressWidth(stage.numCompletedTasks, stage.numTasks) + '%' }">
                </div>
                <div class="progress-bar-failed" 
                     :style="{ 
                        width: getProgressWidth(stage.numFailedTasks, stage.numTasks) + '%',
                        left: getProgressWidth(stage.numCompletedTasks, stage.numTasks) + '%' 
                     }">
                </div>
              </div>
              <span class="task-count-text">
                {{ stage.numCompletedTasks }}/{{ stage.numTasks }}
                <span v-if="stage.numFailedTasks > 0" class="failed-count"> ({{ stage.numFailedTasks }} failed)</span>
              </span>
            </div>
          </td>
          <td>{{ formatBytes(stage.inputBytes) }}</td>
          <td>{{ formatBytes(stage.outputBytes) }}</td>
          <td>{{ formatBytes(stage.shuffleReadBytes) }}</td>
          <td>{{ formatBytes(stage.shuffleWriteBytes) }}</td>
        </tr>
        <tr v-if="stages.length === 0">
          <td :colspan="columns.length" style="text-align: center; padding: 40px;">No stages found.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getAppStages } from '../../api';
import { formatTime, formatBytes, formatDateTime } from '../../utils/format';

const props = defineProps({
  appId: String,
  jobId: Number, // Optional filter
  hideTitle: { type: Boolean, default: false },
  plain: { type: Boolean, default: false }
});

const emit = defineEmits(['view-stage-detail']);

const stages = ref([]);
const totalStages = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const sorts = ref([{ field: 'stageId', dir: 'desc' }]); // Default sort by Stage Id DESC

const columns = [
  { field: 'stageId', label: 'Stage Id', width: '80px' },
  { field: 'stageName', label: 'Name' },
  { field: 'submissionTime', label: 'Submitted', width: '160px' },
  { field: 'duration', label: 'Duration', width: '100px' },
  { field: 'numTasks', label: 'Tasks: Succeeded/Total', width: '180px' },
  { field: 'inputBytes', label: 'Input', width: '100px' },
  { field: 'outputBytes', label: 'Output', width: '100px' },
  { field: 'shuffleReadBytes', label: 'Shuffle Read', width: '120px' },
  { field: 'shuffleWriteBytes', label: 'Shuffle Write', width: '120px' }
];

const formatDuration = (start, end) => {
  if (!start || !end) return '-';
  const diff = new Date(end).getTime() - new Date(start).getTime();
  return formatTime(diff);
};

const getProgressWidth = (current, total) => {
  if (!total || total === 0) return 0;
  return Math.min(100, (current / total) * 100);
};

const fetchStages = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getAppStages(props.appId, currentPage.value, pageSize.value, sortStr, props.jobId);
    
    if (res.data && res.data.items) {
      stages.value = res.data.items;
      totalStages.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else {
      stages.value = [];
      totalStages.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch stages", err);
  }
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchStages();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchStages();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => {
  jumpToPage(jumpPageInput.value);
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchStages();
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
  fetchStages();
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
  currentPage.value = 1;
  fetchStages();
};

const clearSorts = () => {
  sorts.value = [];
  currentPage.value = 1;
  fetchStages();
};

const getColumnLabel = (field) => {
  const col = columns.find(c => c.field === field);
  return col ? col.label : field;
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

onMounted(fetchStages);

watch(() => props.appId, () => {
  currentPage.value = 1;
  fetchStages();
});
</script>

<style scoped>
.stage-table-view {
  background: white; 
  border-radius: 8px; 
  padding: 1.5rem; 
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.stage-table-view.plain-mode {
  background: transparent;
  padding: 0;
  box-shadow: none;
  border-radius: 0;
}

.table-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  margin-bottom: 1rem; 
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.header-left h4 { margin: 0; color: #2c3e50; }
.header-left small { color: #7f8c8d; font-weight: normal; margin-left: 8px; }
.total-count-text { color: #7f8c8d; font-size: 0.9rem; font-weight: 500; }

/* Pagination & Sort Styles */
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

.sort-label { font-weight: 600; color: #555; }
.sort-tags { display: flex; gap: 8px; flex-wrap: wrap; }
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
.sort-dir { margin-left: 4px; font-size: 0.7rem; opacity: 0.8; font-weight: bold; }
.remove-sort { margin-left: 6px; cursor: pointer; font-weight: bold; opacity: 0.6; }
.remove-sort:hover { opacity: 1; color: #c62828; }
.clear-sort-btn { background: none; border: none; color: #666; text-decoration: underline; cursor: pointer; font-size: 0.8rem; padding: 0 4px; }
.clear-sort-btn:hover { color: #d32f2f; }
.sort-hint { margin-left: auto; color: #888; font-style: italic; font-size: 0.8rem; }

.pagination-controls { display: flex; gap: 24px; align-items: center; }
.page-size-selector { display: flex; align-items: center; gap: 8px; font-size: 0.9rem; color: #666; }
.page-size-selector select { padding: 4px 8px; border-radius: 4px; border: 1px solid #ddd; }
.page-nav { display: flex; gap: 8px; align-items: center; }
.page-nav button { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; cursor: pointer; border: 1px solid #ddd; border-radius: 4px; background: #fff; color: #555; transition: all 0.2s; }
.page-nav button:hover:not(:disabled) { border-color: #3498db; color: #3498db; background: #f7fbff; }
.page-nav button:disabled { background: #f5f5f5; color: #ccc; cursor: not-allowed; }
.page-jump { display: flex; align-items: center; gap: 5px; margin: 0 8px; }
.jump-input { width: 45px; padding: 4px 6px; text-align: center; border: 1px solid #ddd; border-radius: 4px; }
.total-pages { color: #999; font-size: 0.9rem; }

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.styled-table th, .styled-table td { padding: 12px 8px; text-align: left; border-bottom: 1px solid #eee; }

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.styled-table th { background: #f1f3f5; color: #333; font-weight: 600; }
.styled-table th.sortable { cursor: pointer; user-select: none; }
.styled-table th.sortable:hover { background: #edf2f7; color: #3498db; }

.stage-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}
.stage-link:hover {
  text-decoration: underline;
  color: #2980b9;
}

/* Task Progress Bar */
.task-progress-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 150px;
}

.progress-bar-container {
  height: 8px;
  background: #eee;
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.progress-bar-success {
  height: 100%;
  background: #27ae60;
  position: absolute;
  left: 0;
  top: 0;
}

.progress-bar-failed {
  height: 100%;
  background: #e74c3c;
  position: absolute;
  top: 0;
}

.task-count-text {
  font-size: 0.75rem;
  color: #666;
}

.failed-count {
  color: #e74c3c;
  font-weight: bold;
}
</style>