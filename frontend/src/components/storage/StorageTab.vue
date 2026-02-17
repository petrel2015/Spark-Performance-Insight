<template>
  <div class="storage-tab">
    <!-- RDD List View -->
    <CollapsibleCard title="Persisted RDDs / DataFrames">
      <div v-if="isLoading" class="loading-state">
        <div class="spinner"></div>
        <p>Loading storage data...</p>
      </div>
      <div v-else-if="rdds.length > 0" class="table-wrapper">
        <table class="styled-table">
          <thead>
          <tr>
            <th style="width: 80px;">ID</th>
            <th>Name</th>
            <th style="width: 180px;">Storage Level</th>
            <th style="width: 150px;">Cached Partitions</th>
            <th style="width: 120px;">Size in Memory</th>
            <th style="width: 120px;">Size on Disk</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="rdd in rdds" :key="rdd.rddId">
            <td>{{ rdd.rddId }}</td>
            <td>
              <a href="javascript:void(0)" @click="$emit('view-rdd-detail', rdd.rddId)" class="rdd-link">{{ rdd.name }}</a>
            </td>
            <td>
              <div class="storage-level-tags">
                <span v-for="tag in formatStorageLevel(rdd.storageLevel)" :key="tag" :class="['storage-tag', tag.toLowerCase()]">
                  {{ tag }}
                </span>
              </div>
            </td>
            <td>
              <div class="progress-container">
                <div class="progress-bar" :style="{ width: (rdd.numCachedPartitions / Math.max(1, rdd.numPartitions) * 100) + '%' }"></div>
                <span class="progress-text">{{ rdd.numCachedPartitions }} / {{ rdd.numPartitions }}</span>
              </div>
            </td>
            <td>{{ formatBytes(rdd.memorySize) }}</td>
            <td>{{ formatBytes(rdd.diskSize) }}</td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-storage">
        <span class="material-symbols-outlined">inventory_2</span>
        <p>No persisted RDDs/DataFrames found</p>
        <small>Data only appears here if .cache() or .persist() was explicitly called in the Spark code and an Action was triggered.</small>
      </div>
    </CollapsibleCard>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getAppStorage } from '../../api';
import { formatBytes } from '../../utils/format';
import { formatStorageLevel } from '../../utils/storage';
import CollapsibleCard from '../common/CollapsibleCard.vue';

const props = defineProps({
  appId: { type: String, required: true }
});

defineEmits(['view-rdd-detail']);

const rdds = ref([]);
const isLoading = ref(true);

const fetchStorageData = async () => {
  isLoading.value = true;
  try {
    const res = await getAppStorage(props.appId);
    rdds.value = res.data || [];
  } catch (err) {
    console.error("Failed to fetch storage data", err);
  } finally {
    isLoading.value = false;
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
  text-transform: capitalize;
}

.storage-tag.disk { background: #fff4e6; color: #d9480f; border-color: #ffd8a8; }
.storage-tag.memory { background: #e7f5ff; color: #1971c2; border-color: #a5d8ff; }
.storage-tag.offheap { background: #f3f0ff; color: #6741d9; border-color: #d0bfff; }
.storage-tag.deserialized { background: #f8f9fa; color: #495057; border-color: #ced4da; }

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

.empty-storage {
  text-align: center;
  padding: 60px 20px;
  color: #909399;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.empty-storage .material-symbols-outlined {
  font-size: 3rem;
  color: #dcdfe6;
}

.empty-storage small {
  color: #c0c4cc;
}
</style>
