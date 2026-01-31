<template>
  <div class="jobs-view">
    <table class="styled-table">
      <thead>
        <tr>
          <th style="width: 80px;">Job ID</th>
          <th>Description</th>
          <th style="width: 180px;">Submission Time</th>
          <th style="width: 100px;">Duration</th>
          <th style="width: 150px;">Stages: Succeeded/Total</th>
          <th style="width: 150px;">Tasks: Succeeded/Total</th>
          <th style="width: 100px;">Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.jobId">
          <td>
            <div class="job-id-cell">
              <a href="#" @click.prevent="$emit('view-job-stages', job)">{{ job.jobId }}</a>
              <span v-if="job.jobGroup" class="job-group-badge">{{ job.jobGroup }}</span>
            </div>
          </td>
          <td class="description-cell">
            <a href="#" @click.prevent="$emit('view-job-stages', job)" :title="job.description">
              {{ job.description || '(No Description)' }}
            </a>
          </td>
          <td>{{ formatTime(job.submissionTime) }}</td>
          <td>{{ calculateDuration(job.submissionTime, job.completionTime) }}</td>
          <td>
            <div class="progress-wrapper">
              <span class="progress-text">{{ job.numCompletedStages || 0 }}/{{ job.numStages }}</span>
              <div class="progress-track" v-if="job.numStages > 0">
                <div class="progress-fill" :style="{ width: calculatePercent(job.numCompletedStages, job.numStages) + '%' }"></div>
              </div>
            </div>
          </td>
          <td>
            <div class="progress-wrapper">
              <span class="progress-text">{{ job.numCompletedTasks || 0 }}/{{ job.numTasks }}</span>
              <div class="progress-track" v-if="job.numTasks > 0">
                <div class="progress-fill tasks-fill" :style="{ width: calculatePercent(job.numCompletedTasks, job.numTasks) + '%' }"></div>
              </div>
            </div>
          </td>
          <td><span :class="'status-' + job.status">{{ job.status }}</span></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
const props = defineProps(['jobs']);
const emit = defineEmits(['view-job-stages']);

const formatTime = (t) => t ? new Date(t).toLocaleString() : '-';

const calculateDuration = (s, e) => {
  if (!s || !e) return '-';
  const diff = new Date(e) - new Date(s);
  return (diff / 1000).toFixed(1) + 's';
};

const calculatePercent = (val, total) => {
  if (!total || total === 0) return 0;
  return Math.min(100, Math.max(0, ((val || 0) / total) * 100));
};
</script>

<style scoped>
.styled-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.styled-table th, .styled-table td { padding: 12px 8px; text-align: left; border-bottom: 1px solid #eee; vertical-align: middle; }
.styled-table th { background-color: #f8f9fa; color: #333; font-weight: 600; font-size: 0.9em; }

.description-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
  font-size: 0.9em;
  color: #555;
}

.description-cell a, .job-id-cell a {
  color: #3498db;
  text-decoration: none;
  cursor: pointer;
}
.description-cell a:hover, .job-id-cell a:hover {
  text-decoration: underline;
}

.job-group-badge {
  display: block;
  font-size: 0.75em;
  background-color: #e9ecef;
  color: #666;
  padding: 2px 4px;
  border-radius: 4px;
  margin-top: 4px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* ... rest of existing styles ... */
.progress-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 0.85em;
  color: #666;
}

.progress-track {
  width: 100%;
  height: 6px;
  background-color: #e9ecef;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: #3498db; /* Blue for Stages */
  transition: width 0.3s ease;
}

.tasks-fill {
  background-color: #2ecc71; /* Green for Tasks */
}

.status-SUCCEEDED { color: #27ae60; font-weight: bold; background-color: rgba(39, 174, 96, 0.1); padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
.status-FAILED { color: #e74c3c; font-weight: bold; background-color: rgba(231, 76, 60, 0.1); padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
.status-RUNNING { color: #f39c12; font-weight: bold; background-color: rgba(243, 156, 18, 0.1); padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
</style>
