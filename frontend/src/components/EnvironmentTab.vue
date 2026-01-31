<template>
  <div class="env-tab">
    <div class="search-bar">
      <input v-model="searchQuery" placeholder="Search configuration..." />
    </div>
    <table class="styled-table">
      <thead>
        <tr>
          <th>Name</th>
          <th>Value</th>
          <th>Category</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="config in filteredConfigs" :key="config.id">
          <td class="key-col">{{ config.paramKey }}</td>
          <td class="value-col">{{ config.paramValue }}</td>
          <td>{{ config.category }}</td>
        </tr>
        <tr v-if="filteredConfigs.length === 0">
          <td colspan="3" style="text-align: center;">No configurations found.</td>
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

const filteredConfigs = computed(() => {
  if (!searchQuery.value) return props.configs;
  const q = searchQuery.value.toLowerCase();
  return props.configs.filter(c => 
    c.paramKey.toLowerCase().includes(q) || 
    c.paramValue.toLowerCase().includes(q)
  );
});
</script>

<style scoped>
.env-tab { padding: 1rem; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.search-bar { margin-bottom: 1rem; }
.search-bar input { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 0.9rem; }

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.styled-table th, .styled-table td { padding: 10px 15px; text-align: left; border-bottom: 1px solid #eee; }
.styled-table th { background: #f8f9fa; color: #333; }
.key-col { font-weight: bold; color: #2c3e50; width: 30%; word-break: break-all; }
.value-col { width: 60%; word-break: break-all; font-family: monospace; color: #555; }
</style>
