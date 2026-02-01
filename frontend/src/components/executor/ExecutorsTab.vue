<template>
  <div class="executors-view">
    <table class="styled-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Address</th>
          <th>Cores</th>
          <th>Memory</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="ex in executors" :key="ex.executorId">
          <td>{{ ex.executorId }}</td>
          <td>{{ ex.host }}</td>
          <td>{{ ex.totalCores }}</td>
          <td>{{ (ex.memory / 1024 / 1024 / 1024).toFixed(1) }} GB</td>
          <td><span class="active-dot"></span> Active</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
const props = defineProps(['executors']);
</script>

<style scoped>
.styled-table { width: 100%; border-collapse: collapse; }
.styled-table th, .styled-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
.active-dot { display: inline-block; width: 10px; height: 10px; background: #27ae60; border-radius: 50%; margin-right: 5px; }
</style>
