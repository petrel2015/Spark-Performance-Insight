<template>
  <div class="storage-tab">
    <!-- RDD List View -->
    <CollapsibleCard v-if="!selectedRdd" title="Persisted RDDs / DataFrames">
      <div class="table-wrapper">
        <table class="styled-table">
          <thead>
          <tr>
            <th style="width: 80px;">ID</th>
            <th>RDD Name</th>
            <th style="width: 150px;">Storage Level</th>
            <th style="width: 150px;">Cached Partitions</th>
            <th style="width: 120px;">Size in Memory</th>
            <th style="width: 120px;">Size on Disk</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="rdd in rdds" :key="rdd.rddId">
            <td>{{ rdd.rddId }}</td>
            <td>
              <a href="javascript:void(0)" @click="viewRddDetail(rdd)" class="rdd-link">{{ rdd.name }}</a>
            </td>
            <td>{{ rdd.storageLevel }}</td>
            <td>
              <div class="progress-container">
                <div class="progress-bar" :style="{ width: (rdd.numCached_partitions / rdd.numPartitions * 100) + '%' }"></div>
                <span class="progress-text">{{ rdd.numCached_partitions }} / {{ rdd.numPartitions }}</span>
              </div>
            </td>
            <td>{{ formatBytes(rdd.memorySize) }}</td>
            <td>{{ formatBytes(rdd.diskSize) }}</td>
          </tr>
          <tr v-if="rdds.length === 0">
            <td colspan="6" class="empty-msg">No persisted RDDs found in this application.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </CollapsibleCard>

    <!-- RDD Detail View -->
    <div v-else class="rdd-detail-view">
      <div class="detail-header">
        <button @click="selectedRdd = null" class="back-btn">← Back to Storage List</button>
        <h3>Details for RDD {{ selectedRdd.name }} (ID {{ selectedRdd.rddId }})</h3>
      </div>

      <div class="summary-cards">
        <div class="mini-card">
          <label>Storage Level</label>
          <div class="value">{{ selectedRdd.storageLevel }}</div>
        </div>
        <div class="mini-card">
          <label>Partitions</label>
          <div class="value">{{ selectedRdd.numPartitions }}</div>
        </div>
        <div class="mini-card">
          <label>Memory Size</label>
          <div class="value">{{ formatBytes(selectedRdd.memorySize) }}</div>
        </div>
        <div class="mini-card">
          <label>Disk Size</label>
          <div class="value">{{ formatBytes(selectedRdd.diskSize) }}</div>
        </div>
      </div>

      <CollapsibleCard title="Data Distribution on Executors">
        <div class="table-wrapper">
          <table class="styled-table">
            <thead>
            <tr>
              <th>Host</th>
              <th>Executor ID</th>
              <th>Storage Level</th>
              <th>Memory Size</th>
              <th>Disk Size</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="block in rddBlocks" :key="block.id">
              <td>{{ block.host }}</td>
              <td>{{ block.executorId }}</td>
              <td>{{ block.storageLevel }}</td>
              <td>{{ formatBytes(block.memorySize) }}</td>
              <td>{{ formatBytes(block.diskSize) }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </CollapsibleCard>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getAppStorage, getRddStorage } from '../../api';
import { formatBytes } from '../../utils/format';
import CollapsibleCard from '../common/CollapsibleCard.vue';

const props = defineProps({
  appId: { type: String, required: true }
});

const rdds = ref([]);
const selectedRdd = ref(null);
const rddBlocks = ref([]);

const fetchStorageData = async () => {
  try {
    const res = await getAppStorage(props.appId);
    rdds.value = res.data || [];
  } catch (err) {
    console.error("Failed to fetch storage data", err);
  }
};

const viewRddDetail = async (rdd) => {
  selectedRdd.value = rdd;
  try {
    const res = await getRddStorage(props.appId, rdd.rddId);
    rddBlocks.value = res.data || [];
  } catch (err) {
    console.error("Failed to fetch RDD detail", err);
  }
};

onMounted(fetchStorageData);
</script>

<style scoped>
.storage-tab {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.table-wrapper {
  overflow-x: auto;
}

.styled-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.styled-table th, .styled-table td {
  padding: 12px 10px;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.styled-table th {
  background: #f8f9fa;
  font-weight: 600;
  color: #333;
}

.rdd-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}

.rdd-link:hover {
  text-decoration: underline;
}

.progress-container {
  width: 100%;
  height: 18px;
  background: #eee;
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: #27ae60;
  transition: width 0.3s;
}

.progress-text {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  text-align: center;
  font-size: 0.7rem;
  line-height: 18px;
  font-weight: bold;
  color: #333;
  text-shadow: 0 0 2px white;
}

.empty-msg {
  text-align: center;
  padding: 40px;
  color: #999;
  font-style: italic;
}

/* Detail View Styles */
.rdd-detail-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 20px;
}

.back-btn {
  padding: 6px 12px;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.summary-cards {
  display: flex;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.mini-card {
  background: white;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  border: 1px solid #eee;
  min-width: 150px;
}

.mini-card label {
  display: block;
  font-size: 0.75rem;
  color: #999;
  text-transform: uppercase;
  margin-bottom: 5px;
}

.mini-card .value {
  font-size: 1.1rem;
  font-weight: bold;
  color: #2c3e50;
}
</style>
