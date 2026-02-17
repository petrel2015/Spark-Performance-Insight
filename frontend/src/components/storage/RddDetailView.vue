<template>
  <div class="rdd-detail-view" v-if="rdd">
    <div class="detail-header">
      <button @click="$emit('back')" class="back-btn">← Back to Storage List</button>
      <h3 class="rdd-title">Details for RDD {{ rdd.name }} (ID {{ rdd.rddId }})</h3>
    </div>

    <div class="summary-cards">
      <div class="mini-card storage-level-card">
        <label>Storage Level</label>
        <div class="storage-grid" v-if="parseStorageLevelObject(rdd.storageLevel)">
          <div class="grid-item" :class="{ active: parseStorageLevelObject(rdd.storageLevel).useDisk }">
            <span class="dot"></span> Disk
          </div>
          <div class="grid-item" :class="{ active: parseStorageLevelObject(rdd.storageLevel).useMemory }">
            <span class="dot"></span> Memory
          </div>
          <div class="grid-item" :class="{ active: parseStorageLevelObject(rdd.storageLevel).deserialized }">
            <span class="dot"></span> Deserialized
          </div>
          <div class="grid-item" :class="{ active: parseStorageLevelObject(rdd.storageLevel).replication > 1 }">
            <span class="dot"></span> {{ parseStorageLevelObject(rdd.storageLevel).replication }}x Repl
          </div>
        </div>
        <div v-else class="value">{{ rdd.storageLevel }}</div>
      </div>
      <div class="mini-card">
        <label>Partitions</label>
        <div class="value">{{ rdd.numPartitions }}</div>
      </div>
      <div class="mini-card">
        <label>Memory Size</label>
        <div class="value">{{ formatBytes(rdd.memorySize) }}</div>
      </div>
      <div class="mini-card">
        <label>Disk Size</label>
        <div class="value">{{ formatBytes(rdd.diskSize) }}</div>
      </div>
    </div>

    <CollapsibleCard title="Data Distribution on Executors">
      <div v-if="loading" class="loading-state">Loading block distribution...</div>
      <div v-else-if="rddBlocks.length > 0" class="table-wrapper">
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
            <td>
              <div class="storage-level-tags">
                <span v-for="tag in formatStorageLevel(block.storageLevel)" :key="tag" :class="['storage-tag', tag.toLowerCase()]">
                  {{ tag }}
                </span>
              </div>
            </td>
            <td>{{ formatBytes(block.memorySize) }}</td>
            <td>{{ formatBytes(block.diskSize) }}</td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-blocks">
        No block distribution data available for this RDD.
      </div>
    </CollapsibleCard>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getAppStorage, getRddStorage } from '../../api';
import { formatBytes } from '../../utils/format';
import { parseStorageLevelObject, formatStorageLevel } from '../../utils/storage';
import CollapsibleCard from '../common/CollapsibleCard.vue';

const props = defineProps({
  appId: { type: String, required: true },
  rddId: { type: Number, required: true }
});

const emit = defineEmits(['back']);

const rdd = ref(null);
const rddBlocks = ref([]);
const loading = ref(false);

const fetchData = async () => {
  loading.value = true;
  try {
    // 1. Fetch all RDDs for this app to find the metadata for our specific RDD
    // Note: If there's an API for single RDD metadata, it would be better.
    // For now, filtering from the list res.
    const appRes = await getAppStorage(props.appId);
    const rdds = appRes.data || [];
    rdd.value = rdds.find(r => r.rddId === props.rddId);

    // 2. Fetch block details
    const res = await getRddStorage(props.appId, props.rddId);
    rddBlocks.value = res.data || [];
  } catch (err) {
    console.error("Failed to fetch RDD detail", err);
  } finally {
    loading.value = false;
  }
};

onMounted(fetchData);
watch(() => props.rddId, fetchData);
</script>

<style scoped>
.rdd-detail-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.detail-header {
  display: flex;
  align-items: flex-start;
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
  flex-shrink: 0;
  white-space: nowrap;
  margin-top: 4px;
}

.rdd-title {
  margin: 0;
  font-size: 1.25rem;
  line-height: 1.4;
  word-break: break-word;
  overflow-wrap: break-word;
  flex: 1;
  min-width: 0;
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

.storage-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.grid-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: #bdc3c7;
  font-weight: 500;
}

.grid-item.active {
  color: #2c3e50;
}

.grid-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ecf0f1;
}

.grid-item.active .dot {
  background: #27ae60;
  box-shadow: 0 0 4px rgba(39, 174, 96, 0.4);
}

.loading-state, .empty-blocks {
  padding: 2rem;
  text-align: center;
  color: #999;
  font-style: italic;
}
</style>
