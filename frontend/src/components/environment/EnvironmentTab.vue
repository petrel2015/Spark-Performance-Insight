<template>
  <div class="env-tab">
    <div class="search-bar">
      <input v-model="searchQuery" placeholder="Search configuration..." />
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
          <th @click="handleSort('paramKey', $event)" class="sortable">
            Name {{ getSortIcon('paramKey') }}
          </th>
          <th @click="handleSort('paramValue', $event)" class="sortable">
            Value {{ getSortIcon('paramValue') }}
          </th>
          <th @click="handleSort('category', $event)" class="sortable" style="width: 15%;">
            Category {{ getSortIcon('category') }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="config in sortedConfigs" :key="config.id">
          <td class="key-col">{{ config.paramKey }}</td>
          <td class="value-col">{{ config.paramValue }}</td>
          <td>{{ config.category }}</td>
        </tr>
        <tr v-if="sortedConfigs.length === 0">
          <td colspan="3" style="text-align: center; padding: 20px;">No configurations found.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  configs: {
    type: Array,
    default: () => []
  }
});

const searchQuery = ref('');
const sorts = ref([{ field: 'paramKey', dir: 'asc' }]); // Multi-column sort state

const columns = [
  { field: 'paramKey', label: 'Name' },
  { field: 'paramValue', label: 'Value' },
  { field: 'category', label: 'Category' }
];

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
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
};

const clearSorts = () => {
  sorts.value = [];
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

const sortedConfigs = computed(() => {
  let result = props.configs;
  
  // 1. Filter
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    result = result.filter(c => 
      (c.paramKey || '').toLowerCase().includes(q) || 
      (c.paramValue || '').toLowerCase().includes(q)
    );
  }

  // 2. Sort (Multi-column Client-side)
  if (sorts.value.length === 0) return result;

  return [...result].sort((a, b) => {
    for (const sort of sorts.value) {
      const valA = (a[sort.field] || '').toString().toLowerCase();
      const valB = (b[sort.field] || '').toString().toLowerCase();
      if (valA < valB) return sort.dir === 'asc' ? -1 : 1;
      if (valA > valB) return sort.dir === 'asc' ? 1 : -1;
    }
    return 0;
  });
});
</script>

<style scoped>
.env-tab { padding: 1rem; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.search-bar { margin-bottom: 1rem; }
.search-bar input { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 0.9rem; }

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

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.styled-table th, .styled-table td { padding: 10px 15px; text-align: left; border-bottom: 1px solid #eee; }
.styled-table th { background: #f8f9fa; color: #333; font-weight: 600; }
.styled-table th.sortable { cursor: pointer; user-select: none; }
.styled-table th.sortable:hover { background: #edf2f7; color: #3498db; }

/* Row Hover Effect */
.styled-table tbody tr:hover {
  background-color: #f7fbff;
}

.key-col { font-weight: bold; color: #2c3e50; width: 35%; word-break: break-all; }
.value-col { width: 50%; word-break: break-all; font-family: monospace; color: #555; }
</style>