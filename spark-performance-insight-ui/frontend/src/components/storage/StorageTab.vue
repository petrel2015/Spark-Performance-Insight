<template>
  <div class="storage-view-container">
    <!-- Metric Visibility Selector -->
    <div class="metric-selector-card">
      <div class="selector-header">
        <strong>Select Columns to Display:</strong>
        <div class="selector-actions">
          <button @click="selectAllMetrics">Select All</button>
          <button @click="clearAllMetrics">Clear All</button>
        </div>
      </div>
      <div class="checkbox-group">
        <label v-for="m in AVAILABLE_STORAGE_COLUMNS" :key="m.key" class="checkbox-item">
          <input type="checkbox" :value="m.key" v-model="selectedMetrics">
          {{ m.label }}
        </label>
      </div>
    </div>

    <!-- Main Storage Table Card -->
    <div class="storage-table-card">
      <div v-if="isLoading" class="loading-state">
        <div class="spinner"></div>
        <p>Loading storage data...</p>
      </div>
      <template v-else>
        <div class="table-header-toolbar">
          <div class="header-left">
            <h4>Persisted RDDs / DataFrames <small>(Total: {{ totalRdds }})</small></h4>
          </div>
          
          <div class="header-right">
            <div class="search-box">
              <input type="text"
                     v-model="searchQuery"
                     placeholder="RDD Name or ID"
                     @keyup.enter="handleSearch"
                     class="search-input"
                     style="width: 200px;">
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
            <tr v-for="rdd in rdds" :key="rdd.rddId">
              <td v-for="col in columns" :key="col.field">
                <!-- 1. RDD ID -->
                <template v-if="col.field === 'rddId'">
                  {{ rdd.rddId }}
                </template>

                <!-- 2. Name -->
                <template v-else-if="col.field === 'name'">
                  <a href="javascript:void(0)" @click="$emit('view-rdd-detail', rdd.rddId)" class="rdd-link">{{ rdd.name }}</a>
                </template>

                <!-- 3. Storage Level -->
                <template v-else-if="col.field === 'storageLevel'">
                  <div class="storage-level-tags">
                    <span v-for="tag in formatStorageLevel(rdd.storageLevel)" :key="tag" :class="['storage-tag', tag.toLowerCase()]">
                      {{ tag }}
                    </span>
                  </div>
                </template>

                <!-- 4. Cached Partitions (Progress) -->
                <template v-else-if="col.field === 'numCachedPartitions'">
                  <div class="progress-track">
                    <div class="progress-fill"
                         :style="{ width: (rdd.numCachedPartitions / Math.max(1, rdd.numPartitions) * 100) + '%' }"></div>
                    <div class="progress-text-overlay">{{ rdd.numCachedPartitions }} / {{ rdd.numPartitions }}</div>
                  </div>
                </template>

                <!-- 5. Size in Memory -->
                <template v-else-if="col.field === 'memorySize'">
                  {{ formatBytes(rdd.memorySize) }}
                </template>

                <!-- 6. Size on Disk -->
                <template v-else-if="col.field === 'diskSize'">
                  {{ formatBytes(rdd.diskSize) }}
                </template>

                <!-- Fallback -->
                <template v-else>
                  {{ rdd[col.field] }}
                </template>
              </td>
            </tr>
            <tr v-if="rdds.length === 0">
              <td :colspan="columns.length" style="text-align: center; padding: 40px;">No persisted RDDs found.</td>
            </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue';
import { getAppStorage } from '../../api';
import { formatBytes } from '../../utils/format';
import { formatStorageLevel } from '../../utils/storage';

const props = defineProps({
  appId: { type: String, required: true }
});

defineEmits(['view-rdd-detail']);

const rdds = ref([]);
const totalRdds = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const jumpPageInput = ref(1);
const searchQuery = ref('');
const sorts = ref([{field: 'rddId', dir: 'asc'}]); 
const isLoading = ref(true);

const AVAILABLE_STORAGE_COLUMNS = [
  {key: 'storageLevel', label: 'Storage Level', field: 'storageLevel', width: '180px', sortable: false},
  {key: 'numCachedPartitions', label: 'Cached Partitions', field: 'numCachedPartitions', width: '150px', sortable: true},
  {key: 'memorySize', label: 'Size in Memory', field: 'memorySize', width: '120px', sortable: true},
  {key: 'diskSize', label: 'Size on Disk', field: 'diskSize', width: '120px', sortable: true}
];

const selectedMetrics = ref(AVAILABLE_STORAGE_COLUMNS.map(m => m.key));

const baseColumns = [
  {field: 'rddId', label: 'ID', width: '80px', sortable: true},
  {field: 'name', label: 'RDD Name', width: 'auto', sortable: true}
];

const columns = computed(() => {
  const cols = [...baseColumns];
  AVAILABLE_STORAGE_COLUMNS.forEach(m => {
    if (selectedMetrics.value.includes(m.key)) {
      cols.push(m);
    }
  });
  return cols;
});

const selectAllMetrics = () => { selectedMetrics.value = AVAILABLE_STORAGE_COLUMNS.map(m => m.key); };
const clearAllMetrics = () => { selectedMetrics.value = []; };

const handleSearch = () => {
  currentPage.value = 1;
  fetchStorageData();
};

const fetchStorageData = async () => {
  isLoading.value = true;
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getAppStorage(props.appId, currentPage.value, pageSize.value, sortStr, searchQuery.value);
    if (res.data && res.data.items) {
      rdds.value = res.data.items;
      totalRdds.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else {
      rdds.value = [];
      totalRdds.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch storage data", err);
  } finally {
    isLoading.value = false;
  }
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchStorageData();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchStorageData();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => jumpToPage(jumpPageInput.value);
const handleSizeChange = () => { currentPage.value = 1; fetchStorageData(); };

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
    if (!isShift) sorts.value = [];
    sorts.value.push({field, dir: 'asc'});
  }
  currentPage.value = 1;
  fetchStorageData();
};

const removeSort = (index) => { sorts.value.splice(index, 1); currentPage.value = 1; fetchStorageData(); };
const clearSorts = () => { sorts.value = []; currentPage.value = 1; fetchStorageData(); };

const getColumnLabel = (field) => {
  const col = columns.value.find(c => c.field === field);
  return col ? col.label : field;
};

const getSortIcon = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  if (index === -1) return 'unfold_more';
  return sorts.value[index].dir === 'asc' ? 'arrow_upward' : 'arrow_downward';
};

const getSortOrder = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  return (index !== -1 && sorts.value.length > 1) ? index + 1 : null;
};

const isFieldSorted = (field) => sorts.value.some(x => x.field === field);

onMounted(fetchStorageData);
watch(() => props.appId, () => { currentPage.value = 1; fetchStorageData(); });
</script>

<style scoped>
.storage-view-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.storage-table-card {
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

/* Metric Selector Styles */
.metric-selector-card {
  background: #fdfdfd;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  border: 1px solid #edf2f7;
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

/* Active Sorts Bar */
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

.sort-hint {
  margin-left: auto;
  color: #888;
  font-style: italic;
  font-size: 0.8rem;
}

/* Modern Pagination */
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
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  outline: none;
  cursor: pointer;
  background: white;
  font-size: 0.85rem;
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
}

.pager-btn:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  background: #f5f7fa;
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

.pager-total {
  font-size: 0.85rem;
  color: #909399;
}

/* Table Styles */
.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.styled-table th, .styled-table td {
  padding: 12px 10px;
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

.rdd-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
  word-break: break-all;
}

.rdd-link:hover {
  text-decoration: underline;
}

.storage-level-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.storage-tag {
  font-size: 0.7rem;
  padding: 2px 6px;
  border-radius: 4px;
  background: #f1f3f5;
  color: #495057;
  border: 1px solid #dee2e6;
  font-weight: 600;
}

.storage-tag.disk { background: #fff4e6; color: #d9480f; border-color: #ffd8a8; }
.storage-tag.memory { background: #e7f5ff; color: #1971c2; border-color: #a5d8ff; }

/* Progress Bar Overrides */
.progress-track {
  width: 100%;
  height: 18px;
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
  background-color: #27ae60;
  transition: width 0.3s ease;
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1;
}

.progress-text-overlay {
  position: relative;
  z-index: 2;
  font-size: 0.75rem;
  font-weight: bold;
  color: #333;
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.8);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #3498db;
  gap: 15px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
