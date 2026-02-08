<template>
  <div class="compare-workspace">
    <div v-if="!compareStore.isCompareMode" class="redirect-notice">
      <div class="notice-card">
        <span class="material-symbols-outlined warning-icon">warning</span>
        <h3>Compare Mode is Disabled</h3>
        <p>You need to enable Compare Mode in the header to use this workspace.</p>
        <p class="redirect-text">Redirecting to Application List in {{ countdown }} seconds...</p>
      </div>
    </div>

    <template v-else>
      <div class="header-section">
        <div class="title-row">
          <h2>Compare Workspace</h2>
          <div class="header-actions">
            <button v-if="compareStore.selectedItems.length > 0" class="clear-all-btn" @click="compareStore.clear()">
              <span class="material-symbols-outlined">delete_sweep</span>
              Clear All
            </button>
          </div>
        </div>
        <p class="subtitle">Candidate items for cross-application and cross-version performance benchmarking.</p>
      </div>

      <div class="workspace-content">
        <!-- 0. Applications Comparison Selection -->
        <CollapsibleCard title="Application Candidates for Comparison" :initial-collapsed="false">
          <template #actions>
            <button class="compare-now-btn" 
                    :disabled="!canCompareApps" 
                    @click="startComparison('app')">
              <span class="material-symbols-outlined">analytics</span>
              Start Application Comparison
            </button>
          </template>
          
          <div v-if="selectedApps.length > 0" class="items-grid">
            <div v-for="item in selectedApps" :key="item.id" 
                 class="selected-item-card app-card clickable"
                 :class="{ 'is-checked': compareStore.isItemSelected(item.id) }"
                 @click="compareStore.toggleComparisonItem(item.id)">
               
               <div class="item-name" :title="item.name">{{ item.name }}</div>
               <div class="app-identifier">
                 <span class="material-symbols-outlined">Apps</span>
                 <code class="app-id-text">{{ item.appId }}</code>
               </div>
               <div class="item-stats">
                 <span class="stat-badge id-badge">ID: {{ item.itemId }}</span>
                 <template v-if="item.details">
                   <span v-if="item.details.duration" class="stat-badge">
                     <span class="material-symbols-outlined">timer</span>
                     {{ formatTime(item.details.duration) }}
                   </span>
                 </template>
               </div>
               <div class="card-actions" @click.stop>
                 <button class="action-btn select-action-btn" 
                         :class="{ selected: compareStore.isItemSelected(item.id) }"
                         @click="compareStore.toggleComparisonItem(item.id)">
                   <span class="material-symbols-outlined">
                     {{ compareStore.isItemSelected(item.id) ? 'check_circle' : 'radio_button_unchecked' }}
                   </span>
                   {{ compareStore.isItemSelected(item.id) ? 'Selected' : 'Select' }}
                 </button>
                 <router-link :to="`/app/${item.appId}`" class="action-btn view-link">View Detail</router-link>
                 <button class="remove-btn-small" @click="compareStore.removeItem(item.id)" title="Remove from workspace">
                   <span class="material-symbols-outlined">delete</span>
                 </button>
               </div>
            </div>
          </div>
          <div v-else class="inner-empty-state">
            <p>No Application candidates selected.</p>
          </div>
        </CollapsibleCard>

        <!-- 1. Jobs Comparison Selection -->
        <CollapsibleCard title="Job Candidates for Comparison" :initial-collapsed="false">
          <template #actions>
            <button class="compare-now-btn" 
                    :disabled="!canCompareJobs" 
                    @click="startComparison('job')">
              <span class="material-symbols-outlined">analytics</span>
              Start Job Comparison
            </button>
          </template>
          
          <div v-if="selectedJobs.length > 0" class="items-grid">
            <div v-for="item in selectedJobs" :key="item.id" 
                 class="selected-item-card job-card clickable"
                 :class="{ 'is-checked': compareStore.isItemSelected(item.id) }"
                 @click="compareStore.toggleComparisonItem(item.id)">
               
               <div class="item-name" :title="item.name">{{ item.name }}</div>
               <div class="app-identifier">
                 <span class="material-symbols-outlined">Apps</span>
                 <code class="app-id-text">{{ item.appId }}</code>
               </div>
               <div class="item-stats">
                 <span class="stat-badge id-badge">ID: {{ item.itemId }}</span>
                 <template v-if="item.details">
                   <span v-if="item.details.duration" class="stat-badge">
                     <span class="material-symbols-outlined">timer</span>
                     {{ formatTime(item.details.duration) }}
                   </span>
                   <span v-if="item.details.stages" class="stat-badge">
                     <span class="material-symbols-outlined">account_tree</span>
                     {{ item.details.stages }} Stages
                   </span>
                 </template>
               </div>
               <div class="card-actions" @click.stop>
                 <button class="action-btn select-action-btn" 
                         :class="{ selected: compareStore.isItemSelected(item.id) }"
                         @click="compareStore.toggleComparisonItem(item.id)">
                   <span class="material-symbols-outlined">
                     {{ compareStore.isItemSelected(item.id) ? 'check_circle' : 'radio_button_unchecked' }}
                   </span>
                   {{ compareStore.isItemSelected(item.id) ? 'Selected' : 'Select' }}
                 </button>
                 <router-link :to="`/app/${item.appId}/job/${item.itemId}`" class="action-btn view-link">View Detail</router-link>
                 <button class="remove-btn-small" @click="compareStore.removeItem(item.id)" title="Remove from workspace">
                   <span class="material-symbols-outlined">delete</span>
                 </button>
               </div>
            </div>
          </div>
          <div v-else class="inner-empty-state">
            <p>No Job candidates selected.</p>
          </div>
        </CollapsibleCard>

        <!-- 2. Stages Comparison Selection -->
        <CollapsibleCard title="Stage Candidates for Comparison" :initial-collapsed="false">
          <template #actions>
            <button class="compare-now-btn" 
                    :disabled="!canCompareStages" 
                    @click="startComparison('stage')">
              <span class="material-symbols-outlined">analytics</span>
              Start Stage Comparison
            </button>
          </template>

          <div v-if="selectedStages.length > 0" class="items-grid">
            <div v-for="item in selectedStages" :key="item.id" 
                 class="selected-item-card stage-card clickable"
                 :class="{ 'is-checked': compareStore.isItemSelected(item.id) }"
                 @click="compareStore.toggleComparisonItem(item.id)">
               
               <div class="item-name" :title="item.name">{{ item.name }}</div>
               <div class="app-identifier">
                 <span class="material-symbols-outlined">Apps</span>
                 <code class="app-id-text">{{ item.appId }}</code>
               </div>
               <div class="item-stats">
                 <span class="stat-badge id-badge">ID: {{ item.itemId }}</span>
                 <template v-if="item.details">
                   <span v-if="item.details.duration" class="stat-badge">
                     <span class="material-symbols-outlined">timer</span>
                     {{ formatTime(item.details.duration) }}
                   </span>
                   <span v-if="item.details.tasks" class="stat-badge">
                     <span class="material-symbols-outlined">task_alt</span>
                     {{ item.details.tasks }} Tasks
                   </span>
                 </template>
               </div>
               <div class="card-actions" @click.stop>
                 <button class="action-btn select-action-btn" 
                         :class="{ selected: compareStore.isItemSelected(item.id) }"
                         @click="compareStore.toggleComparisonItem(item.id)">
                   <span class="material-symbols-outlined">
                     {{ compareStore.isItemSelected(item.id) ? 'check_circle' : 'radio_button_unchecked' }}
                   </span>
                   {{ compareStore.isItemSelected(item.id) ? 'Selected' : 'Select' }}
                 </button>
                 <router-link :to="`/app/${item.appId}/stage/${item.itemId}`" class="action-btn view-link">View Detail</router-link>
                 <button class="remove-btn-small" @click="compareStore.removeItem(item.id)" title="Remove from workspace">
                   <span class="material-symbols-outlined">delete</span>
                 </button>
               </div>
            </div>
          </div>
          <div v-else class="inner-empty-state">
            <p>No Stage candidates selected.</p>
          </div>
        </CollapsibleCard>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { compareStore } from '../store/compareStore';
import { formatTime } from '../utils/format';
import CollapsibleCard from '../components/common/CollapsibleCard.vue';

const router = useRouter();
const countdown = ref(3);
let timer = null;

const selectedJobs = computed(() => compareStore.selectedItems.filter(i => i.type === 'job'));
const selectedStages = computed(() => compareStore.selectedItems.filter(i => i.type === 'stage'));
const selectedApps = computed(() => compareStore.selectedItems.filter(i => i.type === 'app'));

const checkedJobsCount = computed(() => {
  return selectedJobs.value.filter(j => compareStore.isItemSelected(j.id)).length;
});

const checkedStagesCount = computed(() => {
  return selectedStages.value.filter(s => compareStore.isItemSelected(s.id)).length;
});

const checkedAppsCount = computed(() => {
  return selectedApps.value.filter(a => compareStore.isItemSelected(a.id)).length;
});

const canCompareJobs = computed(() => checkedJobsCount.value === 2);
const canCompareStages = computed(() => checkedStagesCount.value === 2);
const canCompareApps = computed(() => checkedAppsCount.value === 2);

const startComparison = (type) => {
  const selected = compareStore.selectedItems.filter(i => i.type === type && compareStore.isItemSelected(i.id));
  if (selected.length !== 2) return;
  
  const id1 = selected[0].itemId;
  const id2 = selected[1].itemId;
  const app1 = selected[0].appId;
  const app2 = selected[1].appId;
  
  if (type === 'app') {
    router.push(`/compare/result?type=app&app1=${app1}&id1=${app1}&app2=${app2}&id2=${app2}`);
  } else {
    router.push(`/compare/result?type=${type}&app1=${app1}&id1=${id1}&app2=${app2}&id2=${id2}`);
  }
};

onMounted(() => {
  if (!compareStore.isCompareMode) {
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        clearInterval(timer);
        router.push('/');
      }
    }, 1000);
  }
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.compare-workspace {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.redirect-notice {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 60vh;
}

.notice-card {
  text-align: center;
  background: white;
  padding: 3rem;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
  border: 1px solid #ffeeba;
  max-width: 400px;
}

.warning-icon {
  font-size: 4rem !important;
  color: #f39c12;
  margin-bottom: 1rem;
}

.redirect-text {
  margin-top: 1.5rem;
  color: #3498db;
  font-weight: 600;
}

.header-section {
  margin-bottom: 1rem;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-section h2 {
  margin: 0;
  color: #2c3e50;
}

.clear-all-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: white;
  border: 1px solid #ff7675;
  color: #d63031;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-all-btn:hover {
  background: #fff5f5;
  box-shadow: 0 2px 5px rgba(214, 48, 49, 0.1);
}

.subtitle {
  color: #7f8c8d;
  margin-top: 0.5rem;
  font-size: 0.95rem;
}

.workspace-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  overflow: visible;
}

.inner-empty-state {
  padding: 2rem;
  text-align: center;
  color: #bdc3c7;
  font-style: italic;
}

.browse-btn {
  display: inline-block;
  margin-top: 1.5rem;
  padding: 10px 24px;
  background: #3498db;
  color: white;
  text-decoration: none;
  border-radius: 4px;
  font-weight: 600;
  transition: background 0.2s;
}

.browse-btn:hover {
  background: #2980b9;
}

/* Selection List Styles */
.items-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
  padding-top: 5px;
  overflow: visible;
}

.selected-item-card {
  border: 1px solid #dcdfe6;
  padding: 1.2rem;
  border-radius: 10px;
  background: #fff;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  border-top: 4px solid #f0f0f0; /* Unified neutral top border when not selected */
}

.selected-item-card.clickable {
  cursor: pointer;
}

.selected-item-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 15px rgba(0,0,0,0.08);
  border-color: #b3d8ff;
}

.selected-item-card.is-checked {
  border-color: #3498db;
  background: #f0f7ff;
}

.selected-item-card.is-checked { 
  border-top-color: #3498db; 
}

.item-name {
  font-weight: 600;
  color: #34495e;
  margin-bottom: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.95rem;
}

.app-identifier {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  padding: 6px 8px;
  background: #f1f3f5;
  border-radius: 4px;
  color: #606266;
  transition: all 0.2s;
}

.is-checked .app-identifier {
  background: rgba(52, 152, 219, 0.1);
}

.app-identifier .material-symbols-outlined {
  font-size: 16px;
}

.app-id-text {
  font-size: 0.75rem;
  font-family: monospace;
  word-break: break-all;
}

.item-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 15px;
  padding-top: 10px;
  border-top: 1px solid #f5f5f5;
}

.stat-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  color: #666;
  font-weight: 500;
}

.id-badge {
  background: #fff;
  border-color: #3498db;
  color: #3498db;
  font-family: 'Roboto Mono', monospace;
  font-weight: 700;
}

.stat-badge .material-symbols-outlined {
  font-size: 14px;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 4px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
  cursor: pointer;
}

.select-action-btn {
  background: white;
  border: 1px solid #dcdfe6;
  color: #606266;
}

.select-action-btn:hover {
  background: #f5f7fa;
  border-color: #3498db;
  color: #3498db;
}

.select-action-btn.selected {
  background: #3498db;
  color: white;
  border-color: #3498db;
}

.select-action-btn .material-symbols-outlined {
  font-size: 16px;
}

.view-link {
  background: #f8f9fa;
  border: 1px solid #dcdfe6;
  color: #3498db;
}

.view-link:hover {
  background: #3498db;
  color: white;
  border-color: #3498db;
}

.remove-btn-small {
  padding: 6px 8px;
  background: white;
  border: 1px solid #dcdfe6;
  color: #909399;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.remove-btn-small:hover {
  background: #ffebee;
  color: #e74c3c;
  border-color: #ff7675;
}

.remove-btn-small .material-symbols-outlined {
  font-size: 18px;
}

.compare-now-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #3498db;
  color: white;
  border: none;
  padding: 6px 16px;
  border-radius: 4px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.compare-now-btn:hover:not(:disabled) {
  background: #2980b9;
  box-shadow: 0 2px 5px rgba(52, 152, 219, 0.3);
}

.compare-now-btn:disabled {
  background: #bdc3c7;
  color: #eee;
  cursor: not-allowed;
}
</style>
