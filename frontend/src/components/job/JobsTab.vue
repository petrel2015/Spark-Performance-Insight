<template>
  <div class="jobs-view">
    <div class="table-header">
      <div class="header-left">
        <h4>Jobs List <small>(Total: {{ totalJobs }})</small></h4>
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
              :class="{ sortable: col.sortable }"
              :style="{ width: col.width }">
            {{ col.label }} <span v-if="col.sortable">{{ getSortIcon(col.field) }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.jobId">
          <td>
            <div class="job-id-cell">
              <span>{{ job.jobId }}</span>
              <span v-if="job.jobGroup" class="job-group-badge">{{ job.jobGroup }}</span>
            </div>
          </td>
          <td>
            <a href="javascript:void(0)" @click="$emit('view-job-detail', job.jobId)" class="job-link">
              {{ job.description || 'Job ' + job.jobId }}
            </a>
          </td>
          <td>{{ formatTime(job.submissionTime) }}</td>
          <td>{{ job.duration ? commonFormatTime(job.duration) : calculateDuration(job.submissionTime, job.completionTime) }}</td>
          <td>
            <div class="progress-wrapper">
              <span class="progress-text">{{ job.numCompletedStages || 0 }}/{{ job.numStages }}</span>
              <div class="progress-track" v-if="job.numStages > 0">
                <div class="progress-fill" :style="{ width: calculatePercent(job.numCompletedStages, job.numStages) + '%' }"></div>
              </div>
            </div>
          </td>
          <td>
            <div class="progress-wrapper">
              <span class="progress-text">{{ job.numCompletedTasks || 0 }}/{{ job.numTasks }}</span>
              <div class="progress-track" v-if="job.numTasks > 0">
                <div class="progress-fill tasks-fill" :style="{ width: calculatePercent(job.numCompletedTasks, job.numTasks) + '%' }"></div>
              </div>
            </div>
          </td>
          <td><span :class="'status-' + job.status">{{ job.status }}</span></td>
        </tr>
        <tr v-if="jobs.length === 0">
          <td colspan="7" style="text-align: center; padding: 40px;">No jobs found.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getAppJobs } from '../../api';
import { formatTime as commonFormatTime } from '../../utils/format';

const props = defineProps({
  appId: String
});
const emit = defineEmits(['view-job-stages']);

const jobs = ref([]);
const totalJobs = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const sorts = ref([]); // [{ field, dir }]

const columns = [
  { field: 'jobId', label: 'Job ID', width: '80px', sortable: true },
  { field: 'description', label: 'Description', sortable: false },
  { field: 'submissionTime', label: 'Submission Time', width: '180px', sortable: true },
  { field: 'duration', label: 'Duration', width: '100px', sortable: true },
  { field: 'numStages', label: 'Stages: Succeeded/Total', width: '150px', sortable: true },
  { field: 'numTasks', label: 'Tasks: Succeeded/Total', width: '150px', sortable: true },
  { field: 'status', label: 'Status', width: '100px', sortable: true }
];

const fetchJobs = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getAppJobs(props.appId, currentPage.value, pageSize.value, sortStr);
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
  const col = columns.find(c => c.field === field);
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
    sorts.value.push({ field, dir: 'asc' });
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

onMounted(fetchJobs);
watch(() => props.appId, () => {
  currentPage.value = 1;
  fetchJobs();
});
</script>

<style scoped>
.jobs-view {
  background: white; 
  border-radius: 8px; 
  padding: 1.5rem; 
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
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

.styled-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.styled-table th, .styled-table td { padding: 12px 8px; text-align: left; border-bottom: 1px solid #eee; vertical-align: middle; }

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.styled-table th { background-color: #f8f9fa; color: #333; font-weight: 600; font-size: 0.9em; }
.styled-table th.sortable { cursor: pointer; user-select: none; }
.styled-table th.sortable:hover { background: #edf2f7; color: #3498db; }

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
  display: block;
  font-size: 0.75em;
  background-color: #e9ecef;
  color: #666;
  padding: 2px 4px;
  border-radius: 4px;
  margin-top: 4px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.progress-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 0.85em;
  color: #666;
}

.progress-track {
  width: 100%;
  height: 6px;
  background-color: #e9ecef;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: #3498db; /* Blue for Stages */
  transition: width 0.3s ease;
}

.tasks-fill {
  background-color: #2ecc71; /* Green for Tasks */
}

.status-SUCCEEDED { color: #27ae60; font-weight: bold; background-color: rgba(39, 174, 96, 0.1); padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
.status-FAILED { color: #e74c3c; font-weight: bold; background-color: rgba(231, 76, 60, 0.1); padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
.status-RUNNING { color: #f39c12; font-weight: bold; background-color: rgba(243, 156, 18, 0.1); padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
</style>
