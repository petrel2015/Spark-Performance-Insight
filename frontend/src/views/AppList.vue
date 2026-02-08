<template>
  <div class="app-list-container">
    <!-- Processing Alert -->
    <div v-if="processingMessage" class="processing-alert">
      <div class="alert-content">
        <span class="icon material-symbols-outlined">hourglass_top</span>
        <span>{{ processingMessage }}</span>
      </div>
      <button @click="processingMessage = null" class="close-alert">
        <span class="material-symbols-outlined">close</span>
      </button>
    </div>

    <div class="header-section">
      <h2>Application List</h2>
      <div class="header-actions">
        <div class="search-box">
          <input type="text" v-model="searchQuery" placeholder="Search by name, ID or user..."
                 @keyup.enter="handleSearch" class="search-input">
          <button @click="handleSearch" class="search-btn">
            <span class="material-symbols-outlined" style="font-size: 18px; vertical-align: middle; margin-right: 4px;">search</span>
            Search
          </button>
        </div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-header-toolbar">
        <div class="header-left">
          <span>Total: {{ totalApps }}</span>
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
          <tr v-for="app in apps" :key="app.appId">
            <td>
              <router-link :to="'/app/' + app.appId" class="app-link">
                {{ app.appName }}
              </router-link>
            </td>

            <td>
                          <span v-if="app.sparkVersion && app.sparkVersion !== 'unknown'" class="spark-version-badge">
                            {{ app.sparkVersion }}
                          </span>
            </td>
            <td><code class="app-id-code">{{ app.appId }}</code></td>
            <td>{{ app.userName }}</td>
            <td>{{ formatDateTime(app.startTime) }}</td>
            <td>{{ (app.duration / 1000).toFixed(1) }} s</td>
            <td><span :class="'status-' + app.status">{{ app.status }}</span></td>
            <td>
              <div class="actions-cell">
                <button v-if="compareStore.isCompareMode" 
                        class="compare-action-btn"
                        :class="{ 'in-workspace': compareStore.isInWorkspace(app.appId, 'app') }"
                        @click="toggleCompare(app)">
                  <span class="material-symbols-outlined">
                    {{ compareStore.isInWorkspace(app.appId, 'app') ? 'check_circle' : 'add_circle' }}
                  </span>
                  {{ compareStore.isInWorkspace(app.appId, 'app') ? 'Added' : 'Compare' }}
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="apps.length === 0">
            <td colspan="10" style="text-align: center; padding: 40px;">No applications found.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, computed} from 'vue';
import {getApps} from '../api';
import {formatDateTime} from '../utils/format';
import {useRoute, useRouter} from 'vue-router';
import { compareStore } from '../store/compareStore';

const route = useRoute();
const router = useRouter();

const apps = ref([]);
const processingMessage = ref('');
const totalApps = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const searchQuery = ref('');
const sorts = ref([{field: 'startTime', dir: 'desc'}]);

const columns = [
  {field: 'appName', label: 'App Name'},
  {field: 'sparkVersion', label: 'Version', width: '100px'},
  {field: 'appId', label: 'App ID', width: '280px'},
  {field: 'userName', label: 'User', width: '120px'},
  {field: 'startTime', label: 'Submitted', width: '180px'},
  {field: 'duration', label: 'Duration', width: '120px'},
  {field: 'status', label: 'Status', width: '100px'},
  {field: 'actions', label: 'Actions', width: '120px'}
];

const toggleCompare = (app) => {
  compareStore.addItem({
    id: `app:${app.appId}`,
    type: 'app',
    itemId: app.appId,
    appId: app.appId,
    name: app.appName,
    details: {
      duration: app.duration,
      status: app.status
    }
  });
};

const fetchApps = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getApps(currentPage.value, pageSize.value, sortStr, searchQuery.value);
    if (res.data && res.data.items) {
      apps.value = res.data.items;
      totalApps.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else {
      apps.value = [];
      totalApps.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch apps", err);
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchApps();
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchApps();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchApps();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => {
  jumpToPage(jumpPageInput.value);
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchApps();
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
  fetchApps();
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
  currentPage.value = 1;
  fetchApps();
};

const clearSorts = () => {
  sorts.value = [];
  currentPage.value = 1;
  fetchApps();
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

onMounted(() => {
  if (route.query.processingMsg) {
    processingMessage.value = route.query.processingMsg;
    // Clear query param to avoid showing it on refresh
    router.replace({query: {}});
  }
  fetchApps();
});
</script>

<style scoped>
.app-list-container {
  padding: 1.5rem;
}

.processing-alert {
  background-color: #fff3cd;
  color: #856404;
  border: 1px solid #ffeeba;
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  animation: fadeIn 0.3s ease-out;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
}

.close-alert {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #856404;
  line-height: 1;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.header-section h2 {
  margin: 0;
  color: #2c3e50;
}

.header-actions {
  display: flex;
  gap: 20px;
  align-items: center;
}

.search-box {
  display: flex;
  gap: 8px;
}

.search-input {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  width: 250px;
  font-size: 0.9rem;
}

.search-btn {
  padding: 8px 16px;
  background: #f8f9fa;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.search-btn:hover {
  background: #e9ecef;
}

.table-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
}

.table-header-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #f0f0f0;
}

.header-left span {
  color: #7f8c8d;
  font-size: 0.9rem;
  font-weight: 500;
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

.table-wrapper {
  overflow-x: auto;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.styled-table th, .styled-table td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.styled-table th {
  background-color: #f8f9fa;
  font-weight: bold;
  color: #333;
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

.styled-table tbody tr:hover {
  background-color: #fcfcfc;
}

.name-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  vertical-align: middle;
}

.app-name {
  font-weight: 600;
  color: #2c3e50;
}

.spark-version-badge {
  background-color: #e8f4f8;
  color: #2980b9;
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #d1e9f0;
  font-weight: 600;
  white-space: nowrap;
}

.status-FINISHED {
  color: #27ae60;
  font-weight: bold;
}

.status-RUNNING {
  color: #f39c12;
  font-weight: bold;
}

.status-FAILED {
  color: #e74c3c;
  font-weight: bold;
}

.view-btn {
  padding: 6px 12px;
  background: white;
  border: 1px solid #3498db;
  color: #3498db;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.2s;
}

.view-btn:hover {
  background: #3498db;
  color: white;
}

.app-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}

.app-link:hover {
  text-decoration: underline;
  color: #2980b9;
}

.app-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}

.app-link:hover {
  text-decoration: underline;
  color: #2980b9;
}

.app-id-code {
  word-break: break-all;
  white-space: normal;
  font-family: monospace;
  font-size: 0.8rem;
  color: #c7254e;
  background-color: #f9f2f4;
  padding: 2px 4px;
  border-radius: 3px;
}

.actions-cell {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.compare-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
  border: 1px solid #3498db;
  color: #3498db;
}

.compare-action-btn:hover {
  background: #f0f7ff;
}

.compare-action-btn.in-workspace {
  background: #3498db;
  color: white;
}

.compare-action-btn .material-symbols-outlined {
  font-size: 16px;
}
</style>