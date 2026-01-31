<template>
  <div class="app-list">
    <div class="header">
      <h2>Applications</h2>
      <button :disabled="selectedApps.length !== 2" @click="handleCompare" class="compare-btn">
        Compare Selected ({{ selectedApps.length }}/2)
      </button>
    </div>
    
    <table class="styled-table">
      <thead>
        <tr>
          <th>Select</th>
          <th>App Name</th>
          <th>App ID</th>
          <th>User</th>
          <th>Duration (s)</th>
          <th>Status</th>
          <th>Action</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="app in apps" :key="app.appId">
          <td><input type="checkbox" :value="app.appId" v-model="selectedApps" :disabled="selectedApps.length >= 2 && !selectedApps.includes(app.appId)"></td>
          <td>{{ app.appName }}</td>
          <td><code>{{ app.appId }}</code></td>
          <td>{{ app.userName }}</td>
          <td>{{ (app.duration / 1000).toFixed(1) }}</td>
          <td><span :class="'status-' + app.status">{{ app.status }}</span></td>
          <td>
            <button @click="$router.push('/app/' + app.appId)">View Report</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getApps } from '../api';

const apps = ref([]);
const selectedApps = ref([]);

onMounted(async () => {
  const res = await getApps();
  apps.value = res.data;
});

const handleCompare = () => {
  // TODO: Implement comparison view
  alert('Comparing ' + selectedApps.value.join(' and '));
};
</script>

<style scoped>
.app-list { padding: 2rem; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.compare-btn { background: #3498db; color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; }
.compare-btn:disabled { background: #bdc3c7; cursor: not-allowed; }

.styled-table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
.styled-table th, .styled-table td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #ddd; }
.styled-table th { background-color: #f8f9fa; font-weight: bold; }
.status-FINISHED { color: #27ae60; font-weight: bold; }
</style>
