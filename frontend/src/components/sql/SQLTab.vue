<template>
  <div class="sql-view-container">
    <div class="sql-table-card">
      <div class="table-header-toolbar">
        <div class="header-left">
          <h4>SQL / DataFrame List <small>(Total: {{ totalSqls }})</small></h4>
        </div>
        
        <div class="header-right">
          <div class="search-box">
            <input type="text" v-model="searchQuery" placeholder="Search Description" @keyup.enter="handleSearch"
                   class="search-input" style="width: 200px;">
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
      </div>

      <div class="table-wrapper">
        <table class="styled-table">
          <thead>
          <tr>
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
          <tr v-for="sql in sqls" :key="sql.executionId">
            <td v-for="col in columns" :key="col.field">
              <!-- ID -->
              <template v-if="col.field === 'executionId'">
                {{ sql.executionId }}
              </template>

              <!-- Description -->
              <template v-else-if="col.field === 'description'">
                <router-link :to="'/app/' + appId + '/sql/' + sql.executionId" class="sql-link">
                  {{ sql.description }}
                </router-link>
              </template>

              <!-- Submitted -->
              <template v-else-if="col.field === 'startTime'">
                {{ formatDateTime(sql.startTime) }}
              </template>

              <!-- Duration -->
              <template v-else-if="col.field === 'duration'">
                {{ formatDuration(sql.duration) }}
              </template>

              <!-- Job IDs -->
              <template v-else-if="col.field === 'jobIds'">
                <div v-if="sql.jobIds && sql.jobIds.length > 0">
                  <span v-for="(jid, idx) in sql.jobIds" :key="jid">
                    <router-link :to="'/app/' + appId + '/job/' + jid" class="job-id-link">{{ jid }}</router-link>
                    <span v-if="idx < sql.jobIds.length - 1">, </span>
                  </span>
                </div>
                <div v-else>-</div>
              </template>

              <!-- Status -->
              <template v-else-if="col.field === 'status'">
                <span :class="'status-' + sql.status">{{ sql.status }}</span>
              </template>

              <!-- Fallback -->
              <template v-else>
                {{ sql[col.field] }}
              </template>
            </td>
          </tr>
          <tr v-if="sqls.length === 0">
            <td :colspan="columns.length" style="text-align: center; padding: 40px;">No SQL executions found.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, computed} from 'vue';
import {getAppSqlExecutions} from '../../api';
import {formatTime as formatDuration, formatDateTime} from '../../utils/format';

const props = defineProps({
  appId: String
});
const emit = defineEmits(['view-sql-detail']);

const sqls = ref([]);
const totalSqls = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const searchQuery = ref('');
const sorts = ref([{field: 'executionId', dir: 'desc'}]);

const columns = [
  {field: 'executionId', label: 'ID', width: '80px', sortable: true},
  {field: 'description', label: 'Description', sortable: true},
  {field: 'startTime', label: 'Submitted', width: '180px', sortable: true},
  {field: 'duration', label: 'Duration', width: '120px', sortable: true},
  {field: 'jobIds', label: 'Job IDs', width: '120px', sortable: false},
  {field: 'status', label: 'Status', width: '100px', sortable: true}
];

const fetchSqls = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getAppSqlExecutions(props.appId, currentPage.value, pageSize.value, sortStr, searchQuery.value);
    if (res.data && res.data.items) {
      sqls.value = res.data.items;
      totalSqls.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else {
      sqls.value = [];
      totalSqls.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch SQL executions", err);
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchSqls();
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchSqls();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchSqls();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => {
  jumpToPage(jumpPageInput.value);
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchSqls();
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
    sorts.value.push({field, dir: 'asc'});
  }
  currentPage.value = 1;
  fetchSqls();
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
  currentPage.value = 1;
  fetchSqls();
};

const clearSorts = () => {
  sorts.value = [];
  currentPage.value = 1;
  fetchSqls();
};

const getColumnLabel = (field) => {
  const col = columns.find(c => c.field === field);
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

onMounted(fetchSqls);
watch(() => props.appId, () => {
  currentPage.value = 1;
  fetchSqls();
});
</script>

<style scoped>
.sql-view-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.sql-table-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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

.sql-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}

.sql-link:hover {
  text-decoration: underline;
  color: #2980b9;
}

.job-id-link {
  color: #3498db;
  text-decoration: none;
  font-weight: bold;
}

.job-id-link:hover {
  text-decoration: underline;
}

.status-SUCCEEDED {
  color: #27ae60;
  font-weight: bold;
}

.status-FAILED {
  color: #e74c3c;
  font-weight: bold;
}

.status-RUNNING {
  color: #f39c12;
  font-weight: bold;
}
</style>
