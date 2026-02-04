<template>
  <div class="app-list-container">
    <!-- Processing Alert -->
    <div v-if="processingMessage" class="processing-alert">
      <div class="alert-content">
        <span class="icon">⏳</span>
        <span>{{ processingMessage }}</span>
      </div>
      <button @click="processingMessage = null" class="close-alert">×</button>
    </div>

    <div class="header-section">
      <h2>Applications</h2>
      <div class="header-actions">
        <div class="search-box">
          <input type="text" v-model="searchQuery" placeholder="Search by name, ID or user..." @keyup.enter="handleSearch" class="search-input">
          <button @click="handleSearch" class="search-btn">Search</button>
        </div>
        <button :disabled="selectedApps.length !== 2" @click="handleCompare" class="compare-btn">
          Compare Selected ({{ selectedApps.length }}/2)
        </button>
      </div>
    </div>

    <div class="table-card">
      <div class="table-header-toolbar">
        <div class="header-left">
          <span>Total: {{ totalApps }}</span>
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

      <div class="table-wrapper">
        <table class="styled-table">
          <thead>
            <tr>
              <th style="width: 50px;">Select</th>
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
            <tr v-for="app in apps" :key="app.appId">
              <td>
                <input type="checkbox" :value="app.appId" v-model="selectedApps" 
                       :disabled="selectedApps.length >= 2 && !selectedApps.includes(app.appId)">
              </td>
              <td>
                <a href="javascript:void(0)" @click="$router.push('/app/' + app.appId)" class="app-link">
                  {{ app.appName }}
                </a>
              </td>
                        
                        <td>
                          <span v-if="app.sparkVersion && app.sparkVersion !== 'unknown'" class="spark-version-badge">
                            {{ app.sparkVersion }}
                          </span>
                        </td>
                        <td><code class="app-id-code">{{ app.appId }}</code></td>              <td>{{ app.userName }}</td>
              <td>{{ formatDateTime(app.startTime) }}</td>
              <td>{{ (app.duration / 1000).toFixed(1) }} s</td>
              <td><span :class="'status-' + app.status">{{ app.status }}</span></td>
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
import { ref, onMounted, computed } from 'vue';
import { getApps } from '../api';
import { formatDateTime } from '../utils/format';
import { useRoute, useRouter } from 'vue-router';

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
const sorts = ref([{ field: 'startTime', dir: 'desc' }]);
const selectedApps = ref([]);

const columns = [
  { field: 'appName', label: 'App Name' },
  { field: 'sparkVersion', label: 'Version', width: '100px' },
  { field: 'appId', label: 'App ID', width: '280px' },
  { field: 'userName', label: 'User', width: '120px' },
  { field: 'startTime', label: 'Submitted', width: '180px' },
  { field: 'duration', label: 'Duration', width: '120px' },
  { field: 'status', label: 'Status', width: '100px' }
];

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
    sorts.value.push({ field, dir: 'asc' });
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
  if (index === -1) return '↕';
  const s = sorts.value[index];
  const icon = s.dir === 'asc' ? '↑' : '↓';
  if (sorts.value.length > 1) {
    return `${icon}${index + 1}`;
  }
  return icon;
};

const handleCompare = () => {
  // Navigation to compare page or similar
  alert('Comparing ' + selectedApps.value.join(' and '));
};

onMounted(() => {
  if (route.query.processingMsg) {
    processingMessage.value = route.query.processingMsg;
    // Clear query param to avoid showing it on refresh
    router.replace({ query: {} });
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
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
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

.compare-btn {
  background: #3498db;
  color: white;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
  transition: background 0.2s;
}

.compare-btn:hover:not(:disabled) {
  background: #2980b9;
}

.compare-btn:disabled {
  background: #bdc3c7;
  cursor: not-allowed;
}

.table-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
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

/* Pagination & Sorting styles */
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

.table-wrapper { overflow-x: auto; }
.styled-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.styled-table th, .styled-table td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eee; }
.styled-table th { background-color: #f8f9fa; font-weight: bold; color: #333; }
.styled-table th.sortable { cursor: pointer; user-select: none; }
.styled-table th.sortable:hover { background: #edf2f7; color: #3498db; }
.styled-table tbody tr:hover { background-color: #fcfcfc; }

.name-cell { display: inline-flex; align-items: center; gap: 10px; vertical-align: middle; }
.app-name { font-weight: 600; color: #2c3e50; }
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

.status-FINISHED { color: #27ae60; font-weight: bold; }
.status-RUNNING { color: #f39c12; font-weight: bold; }
.status-FAILED { color: #e74c3c; font-weight: bold; }

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
</style>