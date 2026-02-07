<template>
  <div class="stage-table-view" :class="{ 'plain-mode': plain }">
    <div class="table-header-toolbar">
      <div class="header-left">
        <h4 v-if="!hideTitle">Stages List <small>(Total: {{ totalStages }})</small></h4>
        <span v-else class="total-count-text">Total: {{ totalStages }} stages</span>
      </div>

      <div class="header-right">
        <div class="search-box">
          <input type="number" v-model.number="searchStageId" placeholder="Stage ID"
                 @keyup.enter="handleSearch" class="search-input" style="width: 100px;">
          <input type="number" v-model.number="searchJobId" placeholder="Job ID"
                 @keyup.enter="handleSearch" class="search-input" style="width: 100px;">
          <button @click="handleSearch" class="search-btn">
            <span class="material-symbols-outlined" style="font-size: 18px; vertical-align: middle; margin-right: 4px;">search</span>
            Search
          </button>
        </div>

        <div class="pagination-controls">
          <div class="page-size-selector">
            <span>Rows:</span>
            <select v-model="pageSize" @change="handleSizeChange">
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
            </select>
          </div>

          <div class="page-nav">
            <button @click="jumpToPage(1)" :disabled="currentPage === 1" title="First Page">
              <span class="material-symbols-outlined" style="font-size: 18px;">first_page</span>
            </button>
            <button @click="changePage(-1)" :disabled="currentPage === 1" title="Previous Page">
              <span class="material-symbols-outlined" style="font-size: 18px;">chevron_left</span>
            </button>

            <div class="page-jump">
              <input type="number"
                     v-model.number="jumpPageInput"
                     @keyup.enter="handleJump"
                     class="jump-input"
                     min="1"
                     :max="totalPages"/>
              <span class="total-pages">/ {{ totalPages }}</span>
            </div>

            <button @click="changePage(1)" :disabled="currentPage === totalPages" title="Next Page">
              <span class="material-symbols-outlined" style="font-size: 18px;">chevron_right</span>
            </button>
            <button @click="jumpToPage(totalPages)" :disabled="currentPage === totalPages" title="Last Page">
              <span class="material-symbols-outlined" style="font-size: 18px;">last_page</span>
            </button>
          </div>
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
          <span @click="removeSort(index)" class="remove-sort" title="Remove sort">
            <span class="material-symbols-outlined" style="font-size: 14px;">close</span>
          </span>
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
          <div class="header-container">
            {{ col.label }}
            <div class="sort-indicator">
              <span class="material-symbols-outlined sort-icon" :class="{ active: isFieldSorted(col.field) }">
                {{ getSortIcon(col.field) }}
              </span>
              <span v-if="getSortOrder(col.field)" class="sort-order">{{ getSortOrder(col.field) }}</span>
            </div>
          </div>
        </th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="stage in stages" :key="stage.id">
        <td v-for="col in columns" :key="col.field">
          <!-- 1. Stage ID & Attempts -->
          <template v-if="col.field === 'stageId'">
            {{ stage.stageId }}
            <span v-if="hasRetries(stage.stageId)" class="attempt-badge">(Attempt {{ stage.attemptId }})</span>
            <span v-if="isExpired(stage)" class="expired-badge">Expired</span>
          </template>

          <!-- 1.5 Performance Score -->
          <template v-else-if="col.field === 'performanceScore'">
            <div class="score-cell-wrapper">
              <span class="score-badge" :class="getScoreClass(stage.performanceScore)">
                {{ Math.round(stage.performanceScore || 0) }}
              </span>
            </div>
          </template>

          <!-- 2. Job ID Link -->
          <template v-else-if="col.field === 'jobId'">
            <router-link :to="'/app/' + appId + '/job/' + stage.jobId" class="stage-link">
              {{ stage.jobId }}
            </router-link>
          </template>

          <!-- 3. Stage Name Link -->
          <template v-else-if="col.field === 'stageName'">
            <router-link :to="'/app/' + appId + '/stage/' + stage.stageId + (stage.attemptId > 0 ? '?attemptId=' + stage.attemptId : '')"
               class="stage-link">
              {{ stage.stageName }}
            </router-link>
          </template>

          <!-- 4. Submission Time -->
          <template v-else-if="col.field === 'submissionTime'">
            {{ formatDateTime(stage.submissionTime) }}
          </template>

          <!-- 5. Duration -->
          <template v-else-if="col.field === 'duration'">
            {{
              stage.duration ? formatTime(stage.duration) : formatDuration(stage.submissionTime, stage.completionTime)
            }}
          </template>

          <!-- 6. Tasks Progress Bar -->
          <template v-else-if="col.field === 'numTasks'">
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
                <div class="task-count-overlay">
                  {{ stage.numCompletedTasks }}/{{ stage.numTasks }}
                  <span v-if="stage.numFailedTasks > 0" class="failed-count"> ({{ stage.numFailedTasks }} failed)</span>
                </div>
              </div>
            </div>
          </template>

          <!-- 7. Dynamic Metric Columns (Bytes) -->
          <template v-else-if="col.type === 'bytes'">
            {{ formatBytes(stage[col.field]) }}
          </template>

          <!-- Fallback -->
          <template v-else>
            {{ stage[col.field] }}
          </template>
        </td>
      </tr>
      <tr v-if="stages.length === 0">
        <td :colspan="columns.length" style="text-align: center; padding: 40px;">No stages found.</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, computed} from 'vue';
import {getAppStages} from '../../api';
import {formatTime, formatBytes, formatDateTime} from '../../utils/format';

const props = defineProps({
  appId: String,
  jobId: Number, // Optional filter
  hideTitle: {type: Boolean, default: false},
  plain: {type: Boolean, default: false},
  visibleMetrics: {type: Array, default: null} // If null, show all default columns
});

const emit = defineEmits(['view-stage-detail', 'view-job-detail']);

const stages = ref([]);
const totalStages = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const searchStageId = ref(null);
const searchJobId = ref(null);
const sorts = ref([{field: 'stageId', dir: 'desc'}]); // Default sort by Stage Id DESC

// Compute max attempt ID per stage to detect retries and expiration
const stageAttemptsMap = computed(() => {
  const map = {};
  stages.value.forEach(s => {
    if (!map[s.stageId]) map[s.stageId] = [];
    map[s.stageId].push(s.attemptId);
  });
  return map;
});

const isExpired = (stage) => {
  const attempts = stageAttemptsMap.value[stage.stageId];
  if (!attempts) return false;
  return Math.max(...attempts) > stage.attemptId;
};

const hasRetries = (stageId) => {
  const attempts = stageAttemptsMap.value[stageId];
  // Show if multiple attempts exist OR if the only attempt is > 0 (implies previous attempts existed)
  if (attempts && attempts.length > 1) return true;
  if (attempts && attempts.length === 1 && attempts[0] > 0) return true;
  return false;
};

const baseColumns = [
  {field: 'stageId', label: 'Stage Id', width: '140px', sortable: true},
  {field: 'performanceScore', label: 'Score', width: '80px', sortable: true},
  {field: 'jobId', label: 'Job Id', width: '80px', sortable: true},
  {field: 'stageName', label: 'Name', sortable: true},
  {field: 'submissionTime', label: 'Submitted', width: '160px', sortable: true},
  {field: 'numTasks', label: 'Tasks: Succeeded/Total', width: '180px', sortable: true}
];

const metricColumnsMap = {
  'duration': {field: 'duration', label: 'Duration', width: '100px', sortable: true, type: 'time'},
  'gc_time': {field: 'gcTimeSum', label: 'GC Time', width: '100px', sortable: true, type: 'time'},
  'scheduler_delay': {
    field: 'schedulerDelaySum',
    label: 'Scheduler Delay',
    width: '130px',
    sortable: true,
    type: 'time'
  },
  'peak_execution_memory': {
    field: 'peakExecutionMemoryMax',
    label: 'Peak Exec Memory',
    width: '150px',
    sortable: true,
    type: 'bytes'
  },
  'memory_spill': {
    field: 'memoryBytesSpilledSum',
    label: 'Spill (memory)',
    width: '120px',
    sortable: true,
    type: 'bytes'
  },
  'disk_spill': {field: 'diskBytesSpilledSum', label: 'Spill (disk)', width: '120px', sortable: true, type: 'bytes'},
  'input': {field: 'inputBytes', label: 'Input', width: '100px', sortable: true, type: 'bytes'},
  'output': {field: 'outputBytes', label: 'Output', width: '100px', sortable: true, type: 'bytes'},
  'shuffle_read': {field: 'shuffleReadBytes', label: 'Shuffle Read', width: '120px', sortable: true, type: 'bytes'},
  'shuffle_write': {field: 'shuffleWriteBytes', label: 'Shuffle Write', width: '120px', sortable: true, type: 'bytes'},
  'task_deserialization_time': {
    field: 'executorDeserializeTimeSum',
    label: 'Deserialization Time',
    width: '150px',
    sortable: true,
    type: 'time'
  },
  'result_serialization_time': {
    field: 'resultSerializationTimeSum',
    label: 'Serialization Time',
    width: '150px',
    sortable: true,
    type: 'time'
  },
  'getting_result_time': {
    field: 'gettingResultTimeSum',
    label: 'Getting Result Time',
    width: '150px',
    sortable: true,
    type: 'time'
  }
};

const columns = computed(() => {
  if (!props.visibleMetrics) {
    return [
      ...baseColumns,
      metricColumnsMap['duration'],
      metricColumnsMap['input'],
      metricColumnsMap['output'],
      metricColumnsMap['shuffle_read'],
      metricColumnsMap['shuffle_write']
    ];
  }

  const cols = [...baseColumns];
  // Ensure the order follows AVAILABLE_METRICS defined in constants
  props.visibleMetrics.forEach(key => {
    if (metricColumnsMap[key]) {
      cols.push(metricColumnsMap[key]);
    }
  });
  return cols;
});

const formatDuration = (start, end) => {
  if (!start || !end) return '-';
  const diff = new Date(end).getTime() - new Date(start).getTime();
  return formatTime(diff);
};

const getProgressWidth = (current, total) => {
  if (!total || total === 0) return 0;
  return Math.min(100, (current / total) * 100);
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchStages();
};

const fetchStages = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const effectiveJobId = searchJobId.value !== null && searchJobId.value !== '' ? searchJobId.value : props.jobId;
    const res = await getAppStages(props.appId, currentPage.value, pageSize.value, sortStr, effectiveJobId, searchStageId.value);

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
    sorts.value.push({field, dir: 'asc'});
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
  const col = columns.value.find(c => c.field === field);
  return col ? col.label : field;
};

const getSortIcon = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  if (index === -1) return 'unfold_more';
  const s = sorts.value[index];
  return s.dir === 'asc' ? 'arrow_upward' : 'arrow_downward';
};

const getSortOrder = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  return (index !== -1 && sorts.value.length > 1) ? index + 1 : null;
};

const isFieldSorted = (field) => {
  return sorts.value.some(x => x.field === field);
};

const getScoreClass = (score) => {
  if (score < 40) return 'critical';
  if (score < 80) return 'warning';
  return 'good';
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.stage-table-view.plain-mode {
  background: transparent;
  padding: 0;
  box-shadow: none;
  border-radius: 0;
}

.table-header-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #f0f0f0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-left h4 {
  margin: 0;
  color: #2c3e50;
}

.header-left small {
  color: #7f8c8d;
  font-weight: normal;
  margin-left: 8px;
}

.total-count-text {
  color: #7f8c8d;
  font-size: 0.9rem;
  font-weight: 500;
}

.search-box {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-input {
  padding: 6px 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #3498db;
}

.search-btn {
  padding: 6px 14px;
  background: #3498db;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  transition: background 0.2s;
}

.search-btn:hover {
  background: #2980b9;
}

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

.styled-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.styled-table th, .styled-table td {
  padding: 12px 8px;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.styled-table th {
  background: #f1f3f5;
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

.header-container {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sort-indicator {
  display: inline-flex;
  align-items: center;
  position: relative;
}

.sort-icon {
  font-size: 16px !important;
  color: #ccc;
  transition: color 0.2s;
}

.sort-icon.active {
  color: #3498db;
}

.sort-order {
  font-size: 10px;
  background: #3498db;
  color: white;
  border-radius: 50%;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  right: -8px;
  top: -4px;
}

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
  height: 16px;
  background: #eee;
  border-radius: 4px;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.progress-bar-success {
  height: 100%;
  background: #27ae60;
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1;
}

.progress-bar-failed {
  height: 100%;
  background: #e74c3c;
  position: absolute;
  top: 0;
  z-index: 1;
}

.task-count-overlay {
  position: relative;
  z-index: 2;
  font-size: 0.7rem;
  font-weight: bold;
  color: #333;
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.8);
  white-space: nowrap;
}

.failed-count {
  color: #8b0000;
}

.expired-badge {
  font-size: 0.75rem;
  background-color: #999;
  color: white;
  padding: 1px 4px;
  border-radius: 3px;
  margin-left: 4px;
  vertical-align: middle;
}

.attempt-badge {
  font-size: 0.8rem;
  color: #666;
  margin-left: 4px;
}

/* Score Badge Styles */
.score-cell-wrapper {
  display: flex;
  align-items: center;
}

.score-badge {
  display: inline-block;
  min-width: 32px;
  padding: 2px 8px;
  border-radius: 12px;
  text-align: center;
  font-weight: bold;
  font-size: 0.8rem;
}

.score-badge.good {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.score-badge.warning {
  background-color: #fff3e0;
  color: #ef6c00;
}

.score-badge.critical {
  background-color: #ffebee;
  color: #c62828;
}
</style>