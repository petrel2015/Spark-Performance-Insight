<template>
  <div class="env-tab">
    <div v-if="isLoading" class="loading-state">
      <div class="spinner"></div>
      <p>Loading environment data...</p>
    </div>
    <div v-else v-for="section in sections" :key="section.category">
      <CollapsibleCard :title="section.title" :initial-collapsed="section.defaultCollapsed">
        <div v-if="section.searchable" class="search-bar">
          <input v-model.trim="searchQueries[section.category]" :placeholder="'Search ' + section.title + '...'"/>
        </div>

        <div class="table-wrapper">
          <table class="styled-table">
            <thead>
            <tr>
              <th style="width: 35%;">Name</th>
              <th style="width: 65%;">Value</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="config in (processedConfigs[section.category] || [])" :key="config.id">
              <td class="key-col">{{ config.paramKey }}</td>
              <td class="value-col">{{ config.paramValue }}</td>
            </tr>
            <tr v-if="(processedConfigs[section.category] || []).length === 0">
              <td colspan="2" class="empty-hint">No data available in this section.</td>
            </tr>
            </tbody>
          </table>
        </div>
      </CollapsibleCard>
    </div>
  </div>
</template>

<script setup>
import {ref, computed, reactive} from 'vue';
import CollapsibleCard from '../common/CollapsibleCard.vue';

const props = defineProps({
  configs: {
    type: Array,
    default: () => []
  },
  isLoading: {
    type: Boolean,
    default: false
  }
});

const sections = [
  {title: 'Runtime Information', category: 'jvm_info', searchable: false, defaultCollapsed: false},
  {title: 'Spark Properties', category: 'spark_conf', searchable: true, defaultCollapsed: false},
  {title: 'Hadoop Properties', category: 'hadoop_conf', searchable: true, defaultCollapsed: true},
  {title: 'System Properties', category: 'system_props', searchable: true, defaultCollapsed: true},
  {title: 'Metrics Properties', category: 'metrics_props', searchable: true, defaultCollapsed: true},
  {title: 'Classpath Entries', category: 'classpath_entries', searchable: true, defaultCollapsed: true}
];

const searchQueries = reactive({
  spark_conf: '',
  hadoop_conf: '',
  system_props: '',
  metrics_props: '',
  classpath_entries: ''
});

// 使用 computed 缓存过滤和排序后的结果，避免在模板中多次调用函数
const processedConfigs = computed(() => {
  const grouped = {};
  sections.forEach(section => {
    const category = section.category;
    let result = props.configs.filter(c => c.category === category);

    const query = searchQueries[category]?.toLowerCase();
    if (query) {
      result = result.filter(c =>
          (c.paramKey || '').toLowerCase().includes(query) ||
          (c.paramValue || '').toLowerCase().includes(query)
      );
    }

    // 按 Key 排序
    grouped[category] = [...result].sort((a, b) => (a.paramKey || '').localeCompare(b.paramKey || ''));
  });
  return grouped;
});
</script>

<style scoped>
.env-tab {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.search-bar {
  margin-bottom: 1rem;
}

.search-bar input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.85rem;
}

.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  table-layout: fixed;
}

.styled-table th, .styled-table td {
  padding: 10px 15px;
  text-align: left;
  border-bottom: 1px solid #eee;
  word-break: break-all;
}

.styled-table th {
  background: #f8f9fa;
  color: #333;
  font-weight: 600;
  border-bottom: 2px solid #dee2e6;
}

.styled-table tbody tr:hover {
  background-color: #f1f8ff;
}

.styled-table tbody tr:hover td {
  background-color: transparent;
}

.key-col {
  font-weight: 600;
  color: #2c3e50;
  transition: background-color 0.2s;
}

.value-col {
  font-family: monospace;
  color: #444;
  font-size: 0.8rem;
}

.empty-hint {
  padding: 20px;
  text-align: center;
  color: #999;
  font-style: italic;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #3498db;
  gap: 15px;
  background: white;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
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