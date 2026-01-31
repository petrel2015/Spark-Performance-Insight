<template>
  <div class="jobs-view">
    <table class="styled-table">
      <thead>
        <tr>
          <th>Job ID</th>
          <th>Submission Time</th>
          <th>Duration</th>
          <th>Stages</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.jobId">
          <td>{{ job.jobId }}</td>
          <td>{{ formatTime(job.submissionTime) }}</td>
          <td>{{ calculateDuration(job.submissionTime, job.completionTime) }}</td>
          <td>{{ job.numStages }} (Stages: {{ job.stageIds }})</td>
          <td><span :class="'status-' + job.status">{{ job.status }}</span></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
const props = defineProps(['jobs']);

const formatTime = (t) => t ? new Date(t).toLocaleString() : '-';
const calculateDuration = (s, e) => {
  if (!s || !e) return '-';
  const diff = new Date(e) - new Date(s);
  return (diff / 1000).toFixed(1) + 's';
};
</script>

<style scoped>
.styled-table { width: 100%; border-collapse: collapse; }
.styled-table th, .styled-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
.status-SUCCEEDED { color: #27ae60; font-weight: bold; }
.status-FAILED { color: #e74c3c; font-weight: bold; }
</style>
