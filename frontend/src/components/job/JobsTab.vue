<template>
  <div class="jobs-view-container">
    <!-- Metric Visibility Selector (Separate Card) -->
    <div class="metric-selector-card" v-if="!hideToolbar">
      <div class="selector-header">
        <strong>Select Columns to Display:</strong>
        <div class="selector-actions">
          <button @click="selectAllMetrics">Select All</button>
          <button @click="clearAllMetrics">Clear All</button>
        </div>
      </div>
      <div class="checkbox-group">
        <label v-for="m in AVAILABLE_JOB_COLUMNS" :key="m.key" class="checkbox-item">
          <input type="checkbox" :value="m.key" v-model="selectedMetrics">
          {{ m.label }}
        </label>
      </div>
    </div>

    <!-- Main Jobs Table Card -->
    <div class="jobs-table-card" :class="{ 'plain-mode': hideToolbar }">
      <div class="table-header-toolbar" v-if="!hideToolbar">
        <div class="header-left">
          <h4>Jobs List <small>(Total: {{ totalJobs }})</small></h4>
        </div>
        
        <div class="header-right">
          <div class="search-box">
            <input type="number" v-model.number="searchJobId" placeholder="Job ID" @keyup.enter="handleSearch"
                   min="0" class="search-input" style="width: 100px;">
            <input type="text" v-model="searchJobGroup" placeholder="Job Group" @keyup.enter="handleSearch"
                   class="search-input" style="width: 180px;">
            <button @click="handleSearch" class="search-btn">
              <span class="material-symbols-outlined" style="font-size: 18px; vertical-align: middle; margin-right: 4px;">search</span>
              Search
            </button>
          </div>

          <div class="modern-pagination">
            <div class="page-size-picker">
              <span>Rows per page:</span>
              <select v-model="pageSize" @change="handleSizeChange" class="modern-select">
                <option :value="20">20</option>
                <option :value="50">50</option>
                <option :value="100">100</option>
              </select>
            </div>

            <div class="pager-actions">
              <button class="pager-btn" @click="jumpToPage(1)" :disabled="currentPage === 1" title="First Page">
                <span class="material-symbols-outlined">first_page</span>
              </button>
              <button class="pager-btn" @click="changePage(-1)" :disabled="currentPage === 1" title="Previous Page">
                <span class="material-symbols-outlined">chevron_left</span>
              </button>

              <div class="pager-info">
                <input type="number"
                       v-model.number="jumpPageInput"
                       @keyup.enter="handleJump"
                       class="pager-input"
                       min="1"
                       :max="totalPages"/>
                <span class="pager-total">/ {{ totalPages }}</span>
              </div>

              <button class="pager-btn" @click="changePage(1)" :disabled="currentPage === totalPages" title="Next Page">
                <span class="material-symbols-outlined">chevron_right</span>
              </button>
              <button class="pager-btn" @click="jumpToPage(totalPages)" :disabled="currentPage === totalPages" title="Last Page">
                <span class="material-symbols-outlined">last_page</span>
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

      <div class="table-wrapper">
        <table class="styled-table">
          <thead>
          <tr>
            <!-- Comparison Selection Column -->
            <th v-if="compareStore.isCompareMode" style="width: 50px; text-align: center;">
              <span class="material-symbols-outlined" style="font-size: 16px; color: #666;">compare_arrows</span>
            </th>
            <th v-for="col in columns"
                :key="col.field"
                @click="handleSort(col.field, $event)"
                :class="{ sortable: col.sortable }"
                :style="{ width: col.width }">
              <div class="header-container">
                {{ col.label }}
                <div class="sort-indicator" v-if="col.sortable">
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
          <tr v-for="job in jobs" :key="job.jobId">
            <!-- Comparison Checkbox -->
            <td v-if="compareStore.isCompareMode" style="text-align: center;">
              <button class="select-btn" 
                      :class="{ selected: compareStore.hasItem('job', appId, job.jobId) }"
                      @click="toggleSelection(job)">
                <span class="material-symbols-outlined">
                  {{ compareStore.hasItem('job', appId, job.jobId) ? 'check_box' : 'check_box_outline_blank' }}
                </span>
              </button>
            </td>
            <td v-for="col in columns" :key="col.field">
              <!-- 1. Job ID -->
              <template v-if="col.field === 'jobId'">
                {{ job.jobId }}
              </template>

              <!-- 1.5 Performance Score -->
              <template v-else-if="col.field === 'performanceScore'">
                <div class="score-cell-wrapper">
                  <span class="score-badge" :class="getScoreClass(job.performanceScore)">
                    {{ Math.round(job.performanceScore || 0) }}
                  </span>
                </div>
              </template>

              <!-- 2. Job Group -->
              <template v-else-if="col.field === 'jobGroup'">
                <span v-if="job.jobGroup" class="job-group-badge">{{ job.jobGroup }}</span>
                <span v-else>-</span>
              </template>

              <!-- 3. Description (Link) -->
              <template v-else-if="col.field === 'description'">
                <router-link :to="'/app/' + appId + '/job/' + job.jobId" class="job-link">
                  {{ job.description || 'Job ' + job.jobId }}
                </router-link>
              </template>

              <!-- 4. Stages Count -->
              <template v-else-if="col.field === 'numStages'">
                {{ job.numStages }}
              </template>

              <!-- 5. Stage IDs (Status Colored) -->
              <template v-else-if="col.field === 'stageIds'">
                <div class="stage-ids-list" :title="job.stageIds">
                  <template v-if="job.stageIds">
                      <span v-for="(sid, idx) in job.stageIds.split(',')" :key="sid">
                        <router-link :to="'/app/' + appId + '/stage/' + sid" :class="'stage-id-link ' + getStageStatusClass(job, sid)">{{ sid }}</router-link>
                        <span v-if="idx < job.stageIds.split(',').length - 1">, </span>
                      </span>
                  </template>
                  <template v-else>-</template>
                </div>
              </template>

              <!-- 6. Submission Time -->
              <template v-else-if="col.field === 'submissionTime'">
                {{ formatTime(job.submissionTime) }}
              </template>

              <!-- 7. Duration -->
              <template v-else-if="col.field === 'duration'">
                {{
                  job.duration ? commonFormatTime(job.duration) : calculateDuration(job.submissionTime, job.completionTime)
                }}
              </template>

              <!-- 8. Stages Progress -->
              <template v-else-if="col.field === 'stagesProgress'">
                <div class="progress-wrapper">
                  <div class="progress-track" v-if="job.numStages > 0">
                    <div class="progress-fill"
                         :style="{ width: calculatePercent(job.numCompletedStages, job.numStages) + '%' }"></div>
                    <div class="progress-text-overlay">{{ job.numCompletedStages || 0 }}/{{ job.numStages }}</div>
                  </div>
                </div>
              </template>

              <!-- 9. Tasks Progress -->
              <template v-else-if="col.field === 'numTasks'">
                <div class="progress-wrapper">
                  <div class="progress-track" v-if="job.numTasks > 0">
                    <div class="progress-fill tasks-fill"
                         :style="{ width: calculatePercent(job.numCompletedTasks, job.numTasks) + '%' }"></div>
                    <div class="progress-text-overlay">{{ job.numCompletedTasks || 0 }}/{{ job.numTasks }}</div>
                  </div>
                </div>
              </template>

              <!-- 10. Status -->
              <template v-else-if="col.field === 'status'">
                <span :class="'status-' + job.status">{{ job.status }}</span>
              </template>

              <!-- Fallback -->
              <template v-else>
                {{ job[col.field] }}
              </template>
            </td>
          </tr>
          <tr v-if="jobs.length === 0">
            <td :colspan="columns.length" style="text-align: center; padding: 40px;">No jobs found.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, computed} from 'vue';
import {getAppJobs} from '../../api';
import {formatTime as commonFormatTime} from '../../utils/format';
import { compareStore } from '../../store/compareStore';

const props = defineProps({
  appId: String,
  sqlExecutionId: [Number, String],
  hideToolbar: { type: Boolean, default: false }
});
const emit = defineEmits(['view-job-stages']);

const jobs = ref([]);
const totalJobs = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const searchJobId = ref(null);
const searchJobGroup = ref('');
const sorts = ref([{field: 'jobId', dir: 'desc'}]); // Default sort by Job ID DESC

const toggleSelection = (job) => {
  const key = `${props.appId}:job:${job.jobId}`;
  if (compareStore.hasItem('job', props.appId, job.jobId)) {
    compareStore.removeItem(key);
  } else {
    compareStore.addItem({
      type: 'job',
      appId: props.appId,
      itemId: job.jobId,
      name: job.description || `Job ${job.jobId}`,
      details: {
        duration: job.duration,
        stages: job.numStages
      }
    });
  }
};

// 可选列定义
const AVAILABLE_JOB_COLUMNS = [
  {key: 'description', label: 'Description', field: 'description', width: '300px', sortable: false},
  {key: 'numStages', label: 'Stages Count', field: 'numStages', width: '120px', sortable: true},
  {key: 'stageIds', label: 'Stage IDs', field: 'stageIds', width: '160px', sortable: false},
  {key: 'submissionTime', label: 'Submission Time', field: 'submissionTime', width: '180px', sortable: true},
  {key: 'duration', label: 'Duration', field: 'duration', width: '100px', sortable: true},
  {key: 'stagesProgress', label: 'Stages Progress', field: 'stagesProgress', width: '150px', sortable: false},
  {key: 'numTasks', label: 'Tasks Progress', field: 'numTasks', width: '150px', sortable: true},
  {key: 'status', label: 'Status', field: 'status', width: '100px', sortable: true}
];

const selectedMetrics = ref(AVAILABLE_JOB_COLUMNS.map(m => m.key));

const baseColumns = [
  {field: 'jobId', label: 'Job ID', width: '80px', sortable: true},
  {field: 'performanceScore', label: 'Score', width: '80px', sortable: true},
  {field: 'jobGroup', label: 'Job Group', width: '180px', sortable: true}
];

const columns = computed(() => {
  const cols = [...baseColumns];
  AVAILABLE_JOB_COLUMNS.forEach(m => {
    if (selectedMetrics.value.includes(m.key)) {
      cols.push(m);
    }
  });
  return cols;
});

const selectAllMetrics = () => {
  selectedMetrics.value = AVAILABLE_JOB_COLUMNS.map(m => m.key);
};

const clearAllMetrics = () => {
  selectedMetrics.value = [];
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchJobs();
};

const fetchJobs = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getAppJobs(props.appId, currentPage.value, pageSize.value, sortStr, searchJobId.value, searchJobGroup.value, props.sqlExecutionId);
    if (res.data && res.data.items) {
      jobs.value = res.data.items;
      totalJobs.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else {
      jobs.value = [];
      totalJobs.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch jobs", err);
  }
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchJobs();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchJobs();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => {
  jumpToPage(jumpPageInput.value);
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchJobs();
};

const handleSort = (field, event) => {
  const col = columns.value.find(c => c.field === field);
  if (!col || !col.sortable) return;

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
  fetchJobs();
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
  currentPage.value = 1;
  fetchJobs();
};

const clearSorts = () => {
  sorts.value = [];
  currentPage.value = 1;
  fetchJobs();
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

const formatTime = (t) => t ? new Date(t).toLocaleString() : '-';

const calculateDuration = (s, e) => {
  if (!s || !e) return '-';
  const diff = new Date(e).getTime() - new Date(s).getTime();
  return commonFormatTime(diff);
};

const calculatePercent = (val, total) => {
  if (!total || total === 0) return 0;
  return Math.min(100, Math.max(0, ((val || 0) / total) * 100));
};

const getStageStatusClass = (job, stageId) => {
  if (!job.stageList) return 'status-skipped';
  const stage = job.stageList.find(s => String(s.stageId) === String(stageId));
  if (!stage) return 'status-skipped';
  if (stage.status === 'SUCCEEDED') return 'status-succeeded';
  if (stage.status === 'FAILED') return 'status-failed';
  if (stage.status === 'RUNNING') return 'status-running';
  return 'status-unknown';
};

onMounted(fetchJobs);
watch(() => props.appId, () => {
  currentPage.value = 1;
  fetchJobs();
});
watch(() => props.sqlExecutionId, () => {
  currentPage.value = 1;
  fetchJobs();
});
</script>

<style scoped>
.jobs-view-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.jobs-table-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.jobs-table-card.plain-mode {
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

.header-left h4 {
  margin: 0;
  color: #2c3e50;
  display: flex;
  align-items: center;
}

.header-left small {
  color: #7f8c8d;
  font-weight: normal;
  margin-left: 8px;
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

/* Metric Selector Styles */
.metric-selector-card {
  background: #fdfdfd;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  border: 1px solid #edf2f7;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 8px;
}

.selector-header strong {
  font-size: 0.9rem;
  color: #2c3e50;
}

.selector-actions {
  display: flex;
  gap: 10px;
}

.selector-actions button {
  background: none;
  border: 1px solid #ddd;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
}

.selector-actions button:hover {
  border-color: #3498db;
  color: #3498db;
  background: #f7fbff;
}

.checkbox-group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px 15px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: #555;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}

.checkbox-item input {
  cursor: pointer;
}

/* Pagination & Sort Styles (Copied from TaskTable) */
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

.modern-pagination {
  display: flex;
  align-items: center;
  gap: 20px;
}

.page-size-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: #606266;
}

.modern-select {
  padding: 4px 24px 4px 8px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  outline: none;
  cursor: pointer;
  background: white;
  transition: all 0.2s;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  font-size: 0.85rem;
  color: #606266;
  background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 6px center;
  background-size: 14px;
  min-width: 60px;
  height: 32px;
}

.modern-select:hover {
  border-color: #3498db;
}

.pager-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  color: #606266;
  transition: all 0.2s;
}

.pager-btn:hover:not(:disabled) {
  border-color: #3498db;
  color: #3498db;
  background: #f0f7ff;
}

.pager-btn:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  background: #f5f7fa;
}

.pager-btn .material-symbols-outlined {
  font-size: 1.2rem;
}

.pager-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 8px;
}

.pager-input {
  width: 40px;
  height: 28px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  text-align: center;
  font-size: 0.85rem;
  outline: none;
}

.pager-input:focus {
  border-color: #3498db;
}

.pager-total {
  font-size: 0.85rem;
  color: #909399;
}

.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  min-width: 1300px;
}

.styled-table th, .styled-table td {
  padding: 12px 8px;
  text-align: left;
  border-bottom: 1px solid #eee;
  vertical-align: middle;
}

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.styled-table th {
  background-color: #f8f9fa;
  color: #333;
  font-weight: 600;
  font-size: 0.9em;
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

.job-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}

.job-link:hover {
  text-decoration: underline;
  color: #2980b9;
}

.job-group-badge {
  display: inline-block;
  font-size: 0.75em;
  background-color: #e9ecef;
  color: #666;
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
  white-space: normal;
}

.stage-ids-list {
  font-size: 0.8rem;
  color: #666;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-id-link {
  font-weight: bold;
  text-decoration: none;
}

.stage-id-link:hover {
  text-decoration: underline;
}

.status-succeeded {
  color: #27ae60;
}

.status-failed {
  color: #e74c3c;
}

.status-running {
  color: #3498db;
}

.status-skipped {
  color: #95a5a6;
}

.status-unknown {
  color: #f39c12;
}

.progress-wrapper {
  display: flex;
  flex-direction: column;
}

.progress-track {
  width: 100%;
  height: 16px;
  background-color: #e9ecef;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.progress-fill {
  height: 100%;
  background-color: #3498db; /* Blue for Stages */
  transition: width 0.3s ease;
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1;
}

.tasks-fill {
  background-color: #27ae60; /* Green for Tasks */
}

.progress-text-overlay {
  position: relative;
  z-index: 2;
  font-size: 0.75rem;
  font-weight: bold;
  color: #333;
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.8);
  white-space: nowrap;
}

.status-SUCCEEDED {
  color: #27ae60;
  font-weight: bold;
  background-color: rgba(39, 174, 96, 0.1);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.85em;
}

.status-FAILED {
  color: #e74c3c;
  font-weight: bold;
  background-color: rgba(231, 76, 60, 0.1);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.85em;
}

.status-RUNNING {
  color: #f39c12;
  font-weight: bold;
  background-color: rgba(243, 156, 18, 0.1);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.85em;
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

.select-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #909399;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.select-btn:hover {
  color: #3498db;
  background: #f0f7ff;
}

.select-btn.selected {
  color: #3498db;
}

.select-btn .material-symbols-outlined {
  font-size: 20px;
}
</style>
