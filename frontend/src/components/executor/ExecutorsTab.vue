<template>
  <div class="executors-tab">
    <!-- 1. Column Selector Card -->
    <div class="metric-selector-card">
      <div class="selector-header">
        <strong>Select Metrics to Display:</strong>
        <div class="selector-actions">
          <button @click="selectAll">Select All</button>
          <button @click="selectDefault">Default</button>
          <button @click="clearAll">Clear All</button>
        </div>
      </div>
      <div class="checkbox-group">
        <label v-for="col in allMetricColumns" :key="col.field" class="checkbox-item">
          <input type="checkbox" :value="col.field" v-model="selectedFields">
          {{ col.label }}
        </label>
      </div>
    </div>

    <!-- 2.5. Executor Timeline Card -->
    <CollapsibleCard title="Executor Lifecycle Timeline" :initial-collapsed="false">
      <template #actions>
        <button class="lock-btn"
                v-if="timelineRef"
                @click="timelineRef.toggleZoomLock()"
                :title="timelineRef.isZoomLocked ? 'Unlock Zoom' : 'Lock Zoom'">
          <span class="material-symbols-outlined" style="font-size: 14px; vertical-align: middle; margin-right: 4px;">
            {{ timelineRef.isZoomLocked ? 'lock' : 'lock_open' }}
          </span>
          {{ timelineRef.isZoomLocked ? 'Locked' : 'Unlocked' }}
        </button>
      </template>
      <ExecutorTimeline ref="timelineRef" :executors="executors"/>
    </CollapsibleCard>

    <!-- 3. Executors List Card -->
    <CollapsibleCard title="Executors List">
      <!-- Active Sorts Display (Inside the card now) -->
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
            <th v-for="col in visibleColumns"
                :key="col.field"
                @click="handleSort(col.field, $event)"
                :class="{ sortable: true }"
                :style="{ minWidth: col.width || '100px' }">
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
          <tr v-for="ex in sortedExecutors" :key="ex.executorId">
            <td v-for="col in visibleColumns" :key="col.field">
              <!-- Status Column -->
              <template v-if="col.field === 'status'">
                <div class="status-wrapper">
                  <span :class="ex.isActive ? 'active-dot' : 'dead-dot'"></span>
                  <span :class="ex.isActive ? 'status-text' : 'status-text-dead'">
                      {{ ex.isActive ? 'Active' : 'Dead' }}
                    </span>
                </div>
              </template>

              <!-- Memory Columns -->
              <template v-else-if="col.type === 'bytes'">
                {{ formatBytes(ex[col.field]) }}
              </template>

              <!-- Time Columns -->
              <template v-else-if="col.type === 'time'">
                {{ formatTime(ex[col.field]) }}
              </template>

              <!-- DateTime Columns -->
              <template v-else-if="col.type === 'datetime'">
                {{ formatDateTime(ex[col.field]) }}
              </template>

              <!-- Composite Columns -->
              <template v-else-if="col.field === 'peakJvm'">
                <div class="sub-cell">On: {{ formatBytes(ex.peakJvmOnHeap) }}</div>
                <div class="sub-cell">Off: {{ formatBytes(ex.peakJvmOffHeap) }}</div>
              </template>
              <template v-else-if="col.field === 'peakExecution'">
                <div class="sub-cell">On: {{ formatBytes(ex.peakExecutionOnHeap) }}</div>
                <div class="sub-cell">Off: {{ formatBytes(ex.peakExecutionOffHeap) }}</div>
              </template>
              <template v-else-if="col.field === 'peakStorage'">
                <div class="sub-cell">On: {{ formatBytes(ex.peakStorageOnHeap) }}</div>
                <div class="sub-cell">Off: {{ formatBytes(ex.peakStorageOffHeap) }}</div>
              </template>
              <template v-else-if="col.field === 'peakPool'">
                <div class="sub-cell">Dir: {{ formatBytes(ex.peakPoolDirect) }}</div>
                <div class="sub-cell">Map: {{ formatBytes(ex.peakPoolMapped) }}</div>
              </template>
              <template v-else-if="col.field === 'taskTime'">
                {{ formatTime(ex.taskTimeMs) }}
                <small class="gc-label" v-if="ex.gcTimeMs > 0">({{ formatTime(ex.gcTimeMs) }})</small>
              </template>

              <!-- Default -->
              <template v-else>
                {{ ex[col.field] }}
              </template>
            </td>
          </tr>
          <tr v-if="executors.length === 0">
            <td :colspan="visibleColumns.length" style="text-align: center; padding: 40px;">No executors found.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </CollapsibleCard>
  </div>
</template>

<script setup>
import {ref, computed} from 'vue';
import CollapsibleCard from '../common/CollapsibleCard.vue';
import ExecutorTimeline from './ExecutorTimeline.vue';
import {formatBytes, formatTime, formatNum, formatDateTime} from '../../utils/format';

const props = defineProps({
  executors: {type: Array, default: () => []}
});

const timelineRef = ref(null);
const sorts = ref([{field: 'executorId', dir: 'asc'}]);
const selectedFields = ref([
  'executorId', 'host', 'status', 'addTime', 'totalCores', 'storageMemory', 'totalTasks', 'completedTasks', 'failedTasks', 'inputBytes'
]);

const baseColumns = [
  {field: 'executorId', label: 'ID', width: '80px'},
  {field: 'host', label: 'Address', width: '140px'},
  {field: 'status', label: 'Status', width: '90px'}
];

const allMetricColumns = [
  {field: 'addTime', label: 'Add Time', type: 'datetime', width: '160px'},
  {field: 'removeTime', label: 'Remove Time', type: 'datetime', width: '160px'},
  {field: 'rddBlocks', label: 'RDD Blocks', type: 'number'},
  {field: 'storageMemory', label: 'Storage Memory', type: 'bytes', width: '120px'},
  {field: 'onHeapStorageMemory', label: 'On Heap Storage', type: 'bytes'},
  {field: 'offHeapStorageMemory', label: 'Off Heap Storage', type: 'bytes'},
  {field: 'peakJvm', label: 'Peak JVM Mem (On/Off)', type: 'composite', width: '150px'},
  {field: 'peakExecution', label: 'Peak Exec Mem (On/Off)', type: 'composite', width: '150px'},
  {field: 'peakStorage', label: 'Peak Storage Mem (On/Off)', type: 'composite', width: '150px'},
  {field: 'peakPool', label: 'Peak Pool Mem (Dir/Map)', type: 'composite', width: '150px'},
  {field: 'diskUsed', label: 'Disk Used', type: 'bytes'},
  {field: 'totalCores', label: 'Cores', type: 'number'},
  {field: 'resourceProfileId', label: 'Resource Profile ID', type: 'number'},
  {field: 'activeTasks', label: 'Active Tasks', type: 'number'},
  {field: 'failedTasks', label: 'Failed Tasks', type: 'number'},
  {field: 'completedTasks', label: 'Complete Tasks', type: 'number'},
  {field: 'totalTasks', label: 'Total Tasks', type: 'number'},
  {field: 'taskTime', label: 'Task Time (GC Time)', type: 'composite', width: '140px'},
  {field: 'inputBytes', label: 'Input', type: 'bytes'},
  {field: 'shuffleReadBytes', label: 'Shuffle Read', type: 'bytes'},
  {field: 'shuffleWriteBytes', label: 'Shuffle Write', type: 'bytes'},
  {field: 'execLossReason', label: 'Exec Loss Reason', type: 'string', width: '200px'}
];

const visibleColumns = computed(() => {
  const cols = [];
  // 强制保留基础列并按顺序排列
  baseColumns.forEach(bc => {
    if (selectedFields.value.includes(bc.field)) cols.push(bc);
  });
  allMetricColumns.forEach(mc => {
    if (selectedFields.value.includes(mc.field) && !cols.find(c => c.field === mc.field)) {
      cols.push(mc);
    }
  });
  return cols;
});

const sortedExecutors = computed(() => {
  if (sorts.value.length === 0) return props.executors;
  return [...props.executors].sort((a, b) => {
    for (const sort of sorts.value) {
      let valA = a[sort.field];
      let valB = b[sort.field];
      if (valA === valB) continue;
      if (valA == null) return 1;
      if (valB == null) return -1;
      const res = valA < valB ? -1 : 1;
      return sort.dir === 'asc' ? res : -res;
    }
    return 0;
  });
});

const handleSort = (field, event) => {
  const existingIndex = sorts.value.findIndex(s => s.field === field);
  const isShift = event.shiftKey;

  if (existingIndex > -1) {
    const existing = sorts.value[existingIndex];
    if (existing.dir === 'asc') existing.dir = 'desc';
    else sorts.value.splice(existingIndex, 1);
  } else {
    if (!isShift) sorts.value = [];
    sorts.value.push({field, dir: 'asc'});
  }
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

const removeSort = (index) => {
  sorts.value.splice(index, 1);
};

const getColumnLabel = (field) => {
  const col = [...baseColumns, ...allMetricColumns].find(c => c.field === field);
  return col ? col.label : field;
};

const clearSorts = () => {
  sorts.value = [];
};
const selectAll = () => {
  selectedFields.value = [...baseColumns.map(c => c.field), ...allMetricColumns.map(c => c.field)];
};
const clearAll = () => {
  selectedFields.value = ['executorId'];
};
const selectDefault = () => {
  selectedFields.value = ['executorId', 'host', 'status', 'totalCores', 'storageMemory', 'totalTasks', 'completedTasks', 'failedTasks', 'inputBytes'];
};
</script>

<style scoped>
.executors-tab {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.metric-selector-card {
  background: white;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid #f0f0f0;
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
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
}

.selector-actions button:hover {
  border-color: #3498db;
  color: #3498db;
}

.checkbox-group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px 15px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: #555;
  cursor: pointer;
  white-space: nowrap;
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
  border: 1px solid #eee;
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
  width: 100%;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.styled-table th, .styled-table td {
  padding: 10px 8px;
  text-align: left;
  border-bottom: 1px solid #eee;
  white-space: nowrap;
}

.styled-table th {
  background: #f8f9fa;
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

.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.status-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.active-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #27ae60;
  border-radius: 50%;
}

.dead-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #e74c3c;
  border-radius: 50%;
}

.status-text {
  color: #27ae60;
  font-weight: 600;
}

.status-text-dead {
  color: #e74c3c;
  font-weight: 600;
}

.sub-cell {
  font-size: 0.75rem;
  color: #7f8c8d;
}

.gc-label {
  color: #e67e22;
  font-size: 0.7rem;
}

.has-failed {
  color: #e74c3c;
  font-weight: bold;
}

.sort-indicator {
  font-size: 0.75rem;
  color: #3498db;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mini-btn {
  padding: 1px 4px;
  font-size: 0.7rem;
  cursor: pointer;
}

.lock-btn {
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.7rem;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
  min-width: 80px;
  text-align: center;
}

.lock-btn:hover {
  background: #f0f0f0;
  color: #333;
}
</style>