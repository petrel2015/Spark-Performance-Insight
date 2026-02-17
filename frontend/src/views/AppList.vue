<template>
  <div class="app-list-container">
    <div v-if="alertMsg" class="processing-alert" :class="{ 'error-alert': alertType === 'error' }">
      <div class="alert-content">
        <span class="material-symbols-outlined">{{ alertType === 'error' ? 'error' : 'info' }}</span>
        <span>{{ alertMsg }}</span>
      </div>
      <button class="close-alert" @click="clearAlert">&times;</button>
    </div>

    <div class="header-section">
      <h2>Application List</h2>
      <div class="header-actions">
        <div class="search-box">
          <input type="text" v-model="searchQuery" placeholder="Search by name, ID or user..."
                 @keyup.enter="handleSearch" class="search-input">
          <button @click="handleSearch" class="search-btn">
            <span class="material-symbols-outlined" style="font-size: 18px; vertical-align: middle; margin-right: 4px;">search</span>
            Search
          </button>
        </div>
      </div>
    </div>

    <!-- Column Selector -->
    <div class="column-selector-card">
      <div class="selector-header">
        <strong>Select Columns to Display:</strong>
        <div class="selector-actions">
          <button @click="selectAllColumns">Select All</button>
          <button @click="clearAllColumns">Clear All</button>
        </div>
      </div>
      <div class="checkbox-group">
        <label v-for="c in AVAILABLE_COLUMNS" :key="c.field" class="checkbox-item">
          <input type="checkbox" :value="c.field" v-model="selectedColumnFields">
          {{ c.label }}
        </label>
      </div>
    </div>

    <div class="table-card">
      <div class="table-header-toolbar">
        <div class="header-left">
          <span>Total: {{ totalApps }}</span>
        </div>

        <div class="modern-pagination">
          <div class="page-size-picker">
            <span>Rows per page:</span>
            <select v-model="pageSize" @change="handleSizeChange" class="modern-select">
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
            </select>
          </div>

          <div class="pager-actions">
            <button class="pager-btn" @click="jumpToPage(1)" :disabled="currentPage === 1" title="First Page">
              <span class="material-symbols-outlined">first_page</span>
            </button>
            <button class="pager-btn" @click="changePage(-1)" :disabled="currentPage === 1" title="Previous Page">
              <span class="material-symbols-outlined">chevron_left</span>
            </button>

            <div class="pager-info">
              <input type="number"
                     v-model.number="jumpPageInput"
                     @keyup.enter="handleJump"
                     class="pager-input"
                     min="1"
                     :max="totalPages"/>
              <span class="pager-total">/ {{ totalPages }}</span>
            </div>

            <button class="pager-btn" @click="changePage(1)" :disabled="currentPage === totalPages" title="Next Page">
              <span class="material-symbols-outlined">chevron_right</span>
            </button>
            <button class="pager-btn" @click="jumpToPage(totalPages)" :disabled="currentPage === totalPages" title="Last Page">
              <span class="material-symbols-outlined">last_page</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Active Sorts Display -->
      <div v-if="sorts.length > 0" class="active-sorts-bar">
        <span class="sort-label">Sort by:</span>
        <div class="sort-tags">
          <span v-for="(sort, index) in sorts" :key="sort.field" class="sort-tag">
            {{ getColumnLabel(sort.field) }} 
            <span class="sort-dir">{{ sort.dir === 'asc' ? 'ASC' : 'DESC' }}</span>
            <span @click="removeSort(index)" class="remove-sort" title="Remove sort">
              <span class="material-symbols-outlined" style="font-size: 14px;">close</span>
            </span>
          </span>
        </div>
        <button @click="clearSorts" class="clear-sort-btn">Clear All</button>
        <small class="sort-hint">(Hold <b>Shift</b> + Click headers to sort by multiple columns)</small>
      </div>

      <div class="table-wrapper">
        <table class="styled-table">
          <thead>
          <tr>
            <!-- Comparison Selection Column -->
            <th v-if="compareStore.isCompareMode" style="width: 60px; min-width: 60px; text-align: center;">
              <div class="header-container" style="justify-content: center;">
                <span class="material-symbols-outlined" style="font-size: 20px; color: #666;">compare_arrows</span>
              </div>
            </th>
            <th v-for="col in columns"
                :key="col.field"
                @click="col.sortable !== false && handleSort(col.field, $event)"
                :class="{ 'sortable': col.sortable !== false }"
                :style="{ width: col.width }">
              <div class="header-container">
                {{ col.label }}
                <div v-if="col.sortable !== false" class="sort-indicator">
                  <span class="material-symbols-outlined sort-icon" :class="{ active: isFieldSorted(col.field) }">
                    {{ getSortIcon(col.field) }}
                  </span>
                  <span v-if="getSortOrder(col.field)" class="sort-order">{{ getSortOrder(col.field) }}</span>
                </div>
              </div>
            </th>
            <th style="width: 220px;">Action</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="app in apps" :key="app.appId" :class="{ 'processing-row': isProcessing(app.parsingStatus) }">
            <!-- Comparison Selection -->
            <td v-if="compareStore.isCompareMode" style="width: 60px; min-width: 60px; text-align: center;">
              <button class="select-btn" 
                      style="margin: 0 auto;"
                      :disabled="!isReady(app.parsingStatus)"
                      :class="{ selected: compareStore.isInWorkspace(app.appId, 'app') }"
                      @click="toggleCompare(app)">
                <span class="material-symbols-outlined">
                  {{ compareStore.isInWorkspace(app.appId, 'app') ? 'check_box' : 'check_box_outline_blank' }}
                </span>
              </button>
            </td>

            <td v-for="col in columns" :key="col.field">
              <!-- 1. App Name -->
              <template v-if="col.field === 'appName'">
                <div class="name-cell-wrapper" :title="app.parsingProgress || 'Click to view details'">
                  <router-link :to="'/app/' + app.appId" 
                               class="app-link"
                               :class="{ 'disabled-link': !isReady(app.parsingStatus) }">
                    {{ app.appName || 'Unknown Application' }}
                  </router-link>
                  <div v-if="app.parsingStatus === 'PENDING_OVERWRITE'" class="overwrite-prompt">
                    <span class="prompt-text">New logs found. Re-import?</span>
                    <div class="prompt-actions">
                      <button class="mini-btn confirm" @click="handleOverwrite(app.appId, true)" title="Yes, clear data and re-import">Confirm</button>
                      <button class="mini-btn cancel" @click="handleOverwrite(app.appId, false)" title="No, keep current data">Cancel</button>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 2. Spark Version -->
              <template v-else-if="col.field === 'sparkVersion'">
                <span v-if="app.sparkVersion && app.sparkVersion !== 'unknown'" class="spark-version-badge">
                  {{ app.sparkVersion }}
                </span>
              </template>

              <!-- 3. App ID -->
              <template v-else-if="col.field === 'appId'">
                <code class="app-id-code">{{ app.appId }}</code>
              </template>

              <!-- 4. User -->
              <template v-else-if="col.field === 'userName'">
                {{ app.userName }}
              </template>

              <!-- 5. Submitted (Start Time) -->
              <template v-else-if="col.field === 'startTime'">
                {{ formatDateTime(app.startTime) }}
              </template>

              <!-- 6. Duration -->
              <template v-else-if="col.field === 'duration'">
                {{ formatTime(app.duration) }}
              </template>

              <!-- 6.5 Total Size -->
              <template v-else-if="col.field === 'totalLogSize'">
                <div class="size-cell">
                  <span class="raw-size">{{ formatBytes(app.totalLogSize) }}</span>
                  <small v-if="estimateUncompressedSize(app.totalLogSize, app.compressionFormat)" 
                         class="est-size" 
                         title="Estimated Uncompressed Size">
                    (Est. {{ formatBytes(estimateUncompressedSize(app.totalLogSize, app.compressionFormat)) }})
                  </small>
                </div>
              </template>

              <!-- 6.6 Compression -->
              <template v-else-if="col.field === 'compressionFormat'">
                <span class="compression-badge" :class="app.compressionFormat?.toLowerCase()">
                  {{ app.compressionFormat || 'None' }}
                </span>
              </template>

              <!-- 7. Notes -->
              <template v-else-if="col.field === 'notes'">
                <div class="notes-cell" @click="startEditing(app)">
                  <div v-if="editingAppId !== app.appId" class="notes-text" :class="{ 'empty-notes': !app.notes }">
                    {{ app.notes || 'Click to add note...' }}
                  </div>
                  <input v-else
                         ref="notesInput"
                         v-model="editingNotes"
                         class="notes-input"
                         @blur="saveNotes(app)"
                         @keyup.enter="saveNotes(app)"
                         placeholder="Enter note..."/>
                </div>
              </template>

              <!-- 8. Parsing Start -->
              <template v-else-if="col.field === 'parsingStartTime'">
                {{ formatDateTime(app.parsingStartTime) }}
              </template>

              <!-- 9. Parsing End -->
              <template v-else-if="col.field === 'parsingEndTime'">
                {{ formatDateTime(app.parsingEndTime) }}
              </template>

              <!-- 10. Detected At (Scan Time) -->
              <template v-else-if="col.field === 'createdAt'">
                {{ formatDateTime(app.createdAt) }}
              </template>

              <!-- 11. Progress -->
              <template v-else-if="col.field === 'progress'">
                <div class="progress-cell">
                  <div v-if="shouldShowProgress(app.parsingStatus)" class="progress-track" :title="getProgressTooltip(app)">
                    <div class="progress-fill" 
                         :style="{ 
                           width: (app.parsingStatus === 'SUCCESS' ? 100 : (app.progressValue || 0)) + '%', 
                           backgroundColor: getProgressColor(app.parsingStatus || 'PENDING_LOAD') 
                         }">
                    </div>
                    <div class="progress-text-overlay">
                      {{ app.parsingStatus === 'SUCCESS' ? '100%' : Math.round(app.progressValue || 0) + '%' }}
                    </div>
                  </div>
                  <div v-else class="na-progress">-</div>
                </div>
              </template>

              <!-- 12. Status -->
              <template v-else-if="col.field === 'status'">
                <div class="status-cell">
                  <span :class="'status-badge status-' + (app.parsingStatus || 'PENDING_LOAD')" 
                        :title="getStatusTooltip(app)">
                    {{ formatStatus(app.parsingStatus) }}
                  </span>
                </div>
              </template>
            </td>

            <!-- ACTION Column -->
            <td>
              <div class="action-cell">
                <!-- PENDING_LOAD: Import -->
                <button v-if="app.parsingStatus === 'PENDING_LOAD' || !app.parsingStatus || app.parsingStatus === 'READY'" 
                        class="action-btn bronze" 
                        @click="openConfirmation('full', app.appId)" 
                        title="Start Medallion Pipeline">
                  <span class="material-symbols-outlined">play_arrow</span>
                  Import
                </button>

                <!-- PENDING_REIMPORT: Re-import -->
                <button v-if="app.parsingStatus === 'PENDING_REIMPORT'" 
                        class="action-btn reimport" 
                        @click="openConfirmation('full', app.appId)" 
                        title="Files changed. Click to re-run pipeline.">
                  <span class="material-symbols-outlined">sync_problem</span>
                  Update
                </button>

                <!-- SUCCESS/FAILED: Granular Re-run Options -->
                <template v-if="['SUCCESS', 'FAILED'].includes(app.parsingStatus)">
                  
                  <!-- Using class 'dropdown-container' mainly for click-outside reference if needed, 
                       but with Teleport we might handle closing differently or rely on global click listener -->
                  <div class="dropdown-trigger-wrapper">
                    <button class="action-btn" @click.stop="toggleDropdown(app.appId, $event)" title="Re-run Pipeline Options">
                      <span class="material-symbols-outlined">refresh</span>
                      Re-run
                      <span class="material-symbols-outlined" style="font-size: 14px">arrow_drop_down</span>
                    </button>
                    
                    <Teleport to="body">
                      <div v-if="activeDropdown === app.appId" 
                           class="dropdown-menu-fixed"
                           :style="{ top: dropdownStyle.top, right: dropdownStyle.right }"
                           v-click-outside="() => activeDropdown = null">
                         <button class="dropdown-item" @click="openConfirmation('full', app.appId)">
                           <span class="material-symbols-outlined">database</span>
                           Full (Raw &rarr; Gold)
                         </button>
                         <button v-if="canStartFromSilver(app)" class="dropdown-item" @click="openConfirmation('bronze-to-gold', app.appId)">
                           <span class="material-symbols-outlined">step</span>
                           From Bronze (Bronze &rarr; Gold)
                         </button>
                         <button v-if="canStartFromGold(app)" class="dropdown-item" @click="openConfirmation('silver-to-gold', app.appId)">
                           <span class="material-symbols-outlined">analytics</span>
                           From Silver (Silver &rarr; Gold)
                         </button>
                      </div>
                    </Teleport>
                  </div>
                </template>
                
                <!-- Processing: Cancel Option -->
                <button v-if="app.parsingStatus === 'QUEUED'" 
                        class="action-btn cancel" 
                        @click="handleCancel(app.appId)" 
                        title="Cancel Queued Job">
                  <span class="material-symbols-outlined">cancel</span>
                  Cancel
                </button>

                <!-- Processing: No Actions (except Cancel for Queued) -->
                <span v-if="isActivePipeline(app.parsingStatus)" class="processing-label">
                  {{ formatStatus(app.parsingStatus) }}
                  <span v-if="getRemainingTime(app)" class="eta-text">ETA: {{ getRemainingTime(app) }}</span>
                  <span v-else class="percent-text">
                    ({{ (app.progressValue && app.progressValue < 1) ? app.progressValue.toFixed(1) : Math.round(app.progressValue || 0) }}%)
                  </span>
                </span>
              </div>
            </td>
          </tr>
          <tr v-if="apps.length === 0">
            <td :colspan="columns.length + (compareStore.isCompareMode ? 1 : 0)" style="text-align: center; padding: 40px;">No applications found.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>

    <ConfirmationModal 
      :is-open="confirmState.isOpen"
      :title="confirmState.title"
      :message="confirmState.message"
      :type="confirmState.type"
      :confirm-text="confirmState.confirmText"
      @confirm="handleConfirm"
      @cancel="confirmState.isOpen = false"
    ></ConfirmationModal>
  </div>
</template>

<script setup>
import {ref, onMounted, onUnmounted, computed, reactive, nextTick, watch} from 'vue';
import {getApps, updateAppNotes} from '../api';
import {formatDateTime, formatTime, formatBytes} from '../utils/format';
import {useRoute, useRouter} from 'vue-router';
import { compareStore } from '../store/compareStore';
import ConfirmationModal from '../components/common/ConfirmationModal.vue';
import axios from 'axios';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

// Simple click-outside directive
const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = function(event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event, el);
      }
    };
    document.body.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.body.removeEventListener('click', el.clickOutsideEvent);
  }
};

const route = useRoute();
const router = useRouter();

const alertMsg = ref('');
const alertType = ref('info');

const clearAlert = () => {
  alertMsg.value = '';
  router.replace({ query: {} });
};

const apps = ref([]);
const totalApps = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const pageSize = ref(parseInt(localStorage.getItem('appList_pageSize') || '20'));
const jumpPageInput = ref(1);
const searchQuery = ref('');
const sorts = ref(JSON.parse(localStorage.getItem('appList_sorts') || '[{"field": "createdAt", "dir": "desc"}]'));

watch(pageSize, (newVal) => {
  localStorage.setItem('appList_pageSize', newVal.toString());
});

watch(sorts, (newVal) => {
  localStorage.setItem('appList_sorts', JSON.stringify(newVal));
}, { deep: true });
const activeDropdown = ref(null);
const dropdownStyle = reactive({ top: '0px', left: '0px' });

// Inline editing for notes
const editingAppId = ref(null);
const editingNotes = ref('');
const notesInput = ref(null);

const startEditing = (app) => {
  editingAppId.value = app.appId;
  editingNotes.value = app.notes || '';
  nextTick(() => {
    if (notesInput.value) {
      notesInput.value.focus();
    }
  });
};

const saveNotes = async (app) => {
  if (editingAppId.value !== app.appId) return;
  
  const oldNotes = app.notes;
  const newNotes = editingNotes.value;
  
  // Optimistic update
  app.notes = newNotes;
  editingAppId.value = null;
  
  if (oldNotes === newNotes) return;
  
  try {
    await updateAppNotes(app.appId, newNotes);
  } catch (err) {
    console.error("Failed to update notes", err);
    app.notes = oldNotes; // Rollback
  }
};

const confirmState = reactive({
  isOpen: false,
  title: '',
  message: '',
  type: 'info',
  confirmText: 'Confirm',
  action: null,
  payload: null
});

// WebSocket State
let stompClient = null;
let socket = null;

const toggleDropdown = (appId, event) => {
  if (activeDropdown.value === appId) {
    activeDropdown.value = null;
  } else {
    activeDropdown.value = appId;
    // Calculate position
    nextTick(() => {
      const button = event.currentTarget;
      const rect = button.getBoundingClientRect();
      dropdownStyle.top = `${rect.bottom + 4}px`;
      dropdownStyle.right = `${document.documentElement.clientWidth - rect.right}px`;
      dropdownStyle.left = 'auto';
    });
  }
};

const canStartFromSilver = (app) => {
  return app.completedStages && app.completedStages.includes("Bronze Finished");
};

const canStartFromGold = (app) => {
  return app.completedStages && app.completedStages.includes("Silver Finished");
};

const connectWebSocket = () => {
  socket = new SockJS('/ws-status');
  stompClient = Stomp.over(socket);
  stompClient.debug = null; // Disable debug logging
  stompClient.connect({}, () => {
    stompClient.subscribe('/topic/status', (message) => {
      const data = JSON.parse(message.body);
      updateAppProgress(data);
    });
  }, (err) => {
    console.error('WebSocket connection error:', err);
    // Retry connection after 5 seconds
    setTimeout(connectWebSocket, 5000);
  });
};

const updateAppProgress = (data) => {
  const index = apps.value.findIndex(a => a.appId === data.appId);
  if (index !== -1) {
    const app = apps.value[index];
    
    // Core property updates from WebSocket - NEVER call fetchApps here during processing
    app.parsingStatus = data.status;
    app.parsingProgress = data.progressText;
    app.progressValue = data.progressValue;
    app.parsingStartTime = data.startTime;
    
    // Update name locally if it's the first time we're seeing it
    if (data.appName && (app.appName === 'Initializing...' || !app.appName || app.appName === 'New Application' || app.appName === 'Pending Analysis' || app.appName === 'Unparsed Application')) {
      app.appName = data.appName;
    }
    
    // Only refresh the full list when the application reaches a terminal state
    if (data.status === 'SUCCESS' || data.status === 'FAILED') {
      // Small delay to ensure DB is fully flushed
      setTimeout(fetchApps, 2000);
    }
  } else {
    // If a completely new app is detected (that wasn't in the initial list)
    // and we are on page 1, we do one refresh to show it.
    if (currentPage.value === 1 && ['QUEUED', 'INGESTING_BRONZE'].includes(data.status)) {
       // Debounce or just check if we already have it in a separate logic?
       // For now, only refresh if it's definitely not in our apps array.
       fetchApps();
    }
  }
};

const handleOverwrite = async (appId, confirm) => {
  try {
    const action = confirm ? 'confirm' : 'cancel';
    await axios.post(`/api/applications/${appId}/overwrite-${action}`);
    fetchApps();
  } catch (err) {
    console.error(`Failed to ${action} overwrite`, err);
  }
};

const handleCancel = async (appId) => {
  try {
    await axios.post(`/api/bronze/cancel/${appId}`);
    // Optimistic update
    const app = apps.value.find(a => a.appId === appId);
    if (app) {
        app.parsingStatus = 'CANCELLED'; // Or revert to previous? Usually just let WS update or fetchApps
        setTimeout(fetchApps, 500);
    }
  } catch (err) {
    console.error(`Failed to cancel ${appId}`, err);
  }
};

const openConfirmation = (action, appId) => {
  activeDropdown.value = null;
  confirmState.action = action;
  confirmState.payload = appId;
  confirmState.isOpen = true;

  const app = apps.value.find(a => a.appId === appId);
  const isFirstImport = app && app.parsingStatus === 'PENDING_LOAD';

  if (action === 'delete') {
    confirmState.title = 'Delete Application';
    confirmState.message = `Are you sure you want to delete application ${appId}?\nThis will permanently clear all parsed data.`;
    confirmState.type = 'danger';
    confirmState.confirmText = 'Delete';
  } else if (action === 'full') {
    if (isFirstImport) {
      confirmState.title = 'Import Application';
      confirmState.message = `Start importing application ${appId}?\nThis process involves deep log parsing and analytical aggregation, which may take a few minutes for large logs.`;
      confirmState.type = 'info';
      confirmState.confirmText = 'Start Import';
    } else {
      confirmState.title = 'Full Re-import';
      confirmState.message = `Full Re-import for ${appId}?\nThis will delete all existing parsed data and re-ingest raw logs.`;
      confirmState.type = 'danger';
      confirmState.confirmText = 'Re-import';
    }
  } else if (action === 'bronze-to-gold') {
    confirmState.title = 'Re-process from Bronze';
    confirmState.message = `Re-process from Bronze for ${appId}?\nThis keeps raw data but re-computes Silver and Gold layers.`;
    confirmState.type = 'warning';
    confirmState.confirmText = 'Start';
  } else if (action === 'silver-to-gold') {
    confirmState.title = 'Re-aggregate from Silver';
    confirmState.message = `Re-aggregate from Silver for ${appId}?\nThis keeps Silver tables but re-computes Gold metrics and Final Summary.`;
    confirmState.type = 'warning';
    confirmState.confirmText = 'Start';
  }
};

const handleConfirm = () => {
  const { action, payload } = confirmState;
  confirmState.isOpen = false;

  if (action === 'delete') {
    handleDelete(payload);
  } else {
    triggerReimport(payload, action);
  }
};

const handleDelete = async (appId) => {
  try {
    await axios.delete(`/api/applications/${appId}`);
    fetchApps();
  } catch (err) {
    console.error(`Failed to delete application ${appId}`, err);
    alert('Delete failed: ' + (err.response?.data?.message || err.message));
  }
};

const triggerReimport = async (appId, mode) => {
  try {
    let url = `/api/bronze/import/${appId}`; // default (full)
    if (mode === 'bronze-to-gold') url = `/api/bronze/reimport/${appId}/bronze-to-gold`;
    else if (mode === 'silver-to-gold') url = `/api/bronze/reimport/${appId}/silver-to-gold`;
    
    await axios.post(url);
    
    // Optimistic update
    const app = apps.value.find(a => a.appId === appId);
    if (app) {
        app.parsingStatus = 'QUEUED';
        app.progressValue = 0;
        app.parsingProgress = 'Waiting in queue...';
    }
  } catch (err) {
    console.error(`Failed to trigger import for ${appId}`, err);
    alert('Import failed: ' + (err.response?.data?.message || err.message));
  }
};

const AVAILABLE_COLUMNS = [
  {field: 'appName', label: 'App Name', sortable: true},
  {field: 'sparkVersion', label: 'Version', width: '100px', sortable: true},
  {field: 'appId', label: 'App ID', width: '400px', sortable: true},
  {field: 'userName', label: 'User', width: '120px', sortable: true},
  {field: 'startTime', label: 'Submitted', width: '180px', sortable: true},
  {field: 'duration', label: 'Duration', width: '120px', sortable: true},
  {field: 'totalLogSize', label: 'Total Size', width: '120px', sortable: true},
  {field: 'compressionFormat', label: 'Compression', width: '120px', sortable: true},
  {field: 'notes', label: 'Notes', width: '250px', sortable: false},
  {field: 'parsingStartTime', label: 'Last Parsing Start', width: '180px', sortable: true},
  {field: 'parsingEndTime', label: 'Last Parsing End', width: '180px', sortable: true},
  {field: 'createdAt', label: 'Scan Time', width: '180px', sortable: true},
  {field: 'progress', label: 'Progress', width: '150px', sortable: false},
  {field: 'status', label: 'Status', width: '150px', sortable: true}
];

const selectedColumnFields = ref(JSON.parse(localStorage.getItem('appList_columns') || '["appName", "appId", "startTime", "duration", "totalLogSize", "notes", "createdAt", "progress", "status"]'));

watch(selectedColumnFields, (newVal) => {
  localStorage.setItem('appList_columns', JSON.stringify(newVal));
}, { deep: true });

const columns = computed(() => {
  return AVAILABLE_COLUMNS.filter(c => selectedColumnFields.value.includes(c.field));
});

const selectAllColumns = () => {
  selectedColumnFields.value = AVAILABLE_COLUMNS.map(c => c.field);
};

const clearAllColumns = () => {
  selectedColumnFields.value = [];
};

const getProgressColor = (status) => {
  if (status === 'FAILED') return '#e74c3c'; // Red
  if (status === 'SUCCESS') return '#27ae60'; // Green
  if (status === 'INGESTING_BRONZE') return '#cd7f32'; // Bronze
  if (status === 'TRANSFORMING_SILVER') return '#95a5a6'; // Silver
  if (status === 'AGGREGATING_GOLD') return '#f1c40f'; // Gold
  return '#3498db'; // Default Blue
};

const estimateUncompressedSize = (size, format) => {
  if (!size || !format || format === 'None') return null;
  
  const ratios = {
    'ZSTD': 10,
    'GZIP': 8,
    'LZ4': 4,
    'SNAPPY': 3
  };
  
  const ratio = ratios[format.toUpperCase()] || 1;
  if (ratio === 1) return null;
  
  return size * ratio;
};

const formatStatus = (status) => {
  if (!status || status === 'READY') return 'Detected';
  if (status === 'SUCCESS') return 'Success';
  
  // Custom mapping for specific statuses
  const map = {
    'INGESTING_BRONZE': 'Bronze Ingestion',
    'TRANSFORMING_SILVER': 'Silver Transform',
    'AGGREGATING_GOLD': 'Gold Aggregation',
    'PENDING_LOAD': 'Ready to Load',
    'PENDING_REIMPORT': 'Log Changed',
    'QUEUED': 'Queued',
    'FAILED': 'Failed'
  };
  if (map[status]) return map[status];
  
  return status.split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
};

const STAGE_DESCRIPTIONS = {
  'INGESTING_BRONZE': 'Bronze (Ingestion): Raw event logs are loaded into the database without modification. It is the Source of Truth.',
  'TRANSFORMING_SILVER': 'Silver (Transformation): Data is parsed, structured, and normalized. Business logic and initial metrics are applied.',
  'AGGREGATING_GOLD': 'Gold (Aggregation): High-level metrics, P95/Median values, and performance scores are pre-calculated for the UI.',
  'QUEUED': 'Job is waiting in the processing queue.',
  'PENDING_LOAD': 'New log discovered. Waiting for user to start import.',
  'FAILED': 'Pipeline failed. Check tooltip for error details.'
};

const getStatusTooltip = (app) => {
  let tip = app.parsingProgress || formatStatus(app.parsingStatus);
  const desc = STAGE_DESCRIPTIONS[app.parsingStatus];
  if (desc) tip += `\n\n${desc}`;
  return tip;
};

const getRemainingTime = (app) => {
  if (!app.parsingStartTime || !app.progressValue || app.progressValue <= 0 || app.progressValue >= 100) {
    return null;
  }

  // Handle both ISO string and epoch milliseconds
  const startTime = typeof app.parsingStartTime === 'number' 
    ? app.parsingStartTime 
    : new Date(app.parsingStartTime).getTime();
    
  const now = Date.now();
  let elapsedMs = now - startTime;
  
  // Handle clock skew
  if (elapsedMs < 0) elapsedMs = 0;
  
  // Show estimate after 1 second of processing to provide immediate feedback
  if (elapsedMs < 1000) return null;

  const totalEstimatedMs = (elapsedMs / app.progressValue) * 100;
  const remainingMs = totalEstimatedMs - elapsedMs;

  if (remainingMs <= 500) return null; // Too close to finish

  const totalSecs = Math.floor(remainingMs / 1000);
  const mins = Math.floor(totalSecs / 60);
  const secs = totalSecs % 60;
  return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
};

const getProgressTooltip = (app) => {
  if (!app.parsingStartTime || !app.progressValue || app.progressValue <= 0 || app.progressValue >= 100) {
    return app.parsingProgress || 'No timing info available';
  }

  const startTime = typeof app.parsingStartTime === 'number' 
    ? app.parsingStartTime 
    : new Date(app.parsingStartTime).getTime();
    
  const now = Date.now();
  let elapsedMs = now - startTime;
  
  if (elapsedMs < 0) elapsedMs = 0;

  const formatMs = (ms) => {
    const totalSecs = Math.floor(ms / 1000);
    const mins = Math.floor(totalSecs / 60);
    const secs = totalSecs % 60;
    return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
  };

  const remaining = getRemainingTime(app);
  let tip = `Elapsed: ${formatMs(elapsedMs)}`;
  if (remaining) {
    tip += `\nEstimated Remaining: ${remaining}`;
  }
  return tip;
};

const isProcessing = (status) => {
  if (!status || status === 'READY' || status === 'PENDING_LOAD' || status === 'PENDING_REIMPORT') return false; 
  return ['INGESTING_BRONZE', 'TRANSFORMING_SILVER', 'AGGREGATING_GOLD', 'LOADING', 'QUEUED', 'PRE_CALCULATING'].includes(status);
};

const isReady = (status) => {
  // Only SUCCESS or PENDING_REIMPORT (if it was previously success) should allow entry
  // For simplicity, let's strictly allow only SUCCESS and PENDING_REIMPORT
  return ['SUCCESS', 'PENDING_REIMPORT'].includes(status);
};

const isActivePipeline = (status) => {
  return ['INGESTING_BRONZE', 'TRANSFORMING_SILVER', 'AGGREGATING_GOLD', 'LOADING', 'PRE_CALCULATING'].includes(status);
};

const shouldShowProgress = (status) => {
  if (!status) return false;
  return [...['INGESTING_BRONZE', 'TRANSFORMING_SILVER', 'AGGREGATING_GOLD', 'LOADING', 'SUCCESS', 'FAILED', 'PRE_CALCULATING']].includes(status);
};

const toggleCompare = (app) => {
  const key = `${app.appId}:app:${app.appId}`;
  if (compareStore.isInWorkspace(app.appId, 'app')) {
    compareStore.removeItem(key);
  } else {
    compareStore.addItem({
      type: 'app',
      itemId: app.appId,
      appId: app.appId,
      name: app.appName,
      details: {
        duration: app.duration,
        status: app.status
      }
    });
  }
};

const fetchApps = async () => {
  try {
    const sortStr = sorts.value.map(s => `${s.field},${s.dir}`).join(';');
    const res = await getApps(currentPage.value, pageSize.value, sortStr, searchQuery.value);
    if (res.data && res.data.items) {
      apps.value = res.data.items.map(app => {
        // Use the numeric progress value from DB if available
        app.progressValue = app.parsingProgressValue || 0;
        return app;
      });
      totalApps.value = res.data.total;
      totalPages.value = res.data.totalPages;
    } else {
      apps.value = [];
      totalApps.value = 0;
      totalPages.value = 0;
    }
    jumpPageInput.value = currentPage.value;
  } catch (err) {
    console.error("Failed to fetch apps", err);
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchApps();
};

const changePage = (delta) => {
  const newPage = currentPage.value + delta;
  if (newPage >= 1 && newPage <= totalPages.value) {
    currentPage.value = newPage;
    fetchApps();
  }
};

const jumpToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    fetchApps();
  } else {
    jumpPageInput.value = currentPage.value;
  }
};

const handleJump = () => {
  jumpToPage(jumpPageInput.value);
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchApps();
};

const handleSort = (field, event) => {
  const col = columns.value.find(c => c.field === field);
  if (!col || col.sortable === false) return;

  const existingIndex = sorts.value.findIndex(s => s.field === field);
  const isShift = event.shiftKey;

  if (existingIndex > -1) {
    const existing = sorts.value[existingIndex];
    if (existing.dir === 'asc') {
      existing.dir = 'desc';
    } else {
      sorts.value.splice(existingIndex, 1);
    }
  } else {
    if (!isShift) {
      sorts.value = [];
    }
    sorts.value.push({field, dir: 'asc'});
  }
  currentPage.value = 1;
  fetchApps();
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
  currentPage.value = 1;
  fetchApps();
};

const clearSorts = () => {
  sorts.value = [];
  currentPage.value = 1;
  fetchApps();
};

const getColumnLabel = (field) => {
  const col = columns.value.find(c => c.field === field);
  return col ? col.label : field;
};

const getSortIcon = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  if (index === -1) return 'unfold_more';
  const s = sorts.value[index];
  return s.dir === 'asc' ? 'arrow_upward' : 'arrow_downward';
};

const getSortOrder = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  return (index !== -1 && sorts.value.length > 1) ? index + 1 : null;
};

const isFieldSorted = (field) => {
  return sorts.value.some(x => x.field === field);
};

onMounted(() => {
  if (route.query.processingMsg) {
    alertMsg.value = route.query.processingMsg;
    alertType.value = 'info';
  } else if (route.query.errorMsg) {
    alertMsg.value = route.query.errorMsg;
    alertType.value = 'error';
  }
  fetchApps();
  connectWebSocket();
});

onUnmounted(() => {
  if (stompClient) {
    stompClient.disconnect();
  }
});
</script>

<style scoped>
.app-list-container {
  padding: 1.5rem;
}

.processing-alert {
  background-color: #fff3cd;
  color: #856404;
  border: 1px solid #ffeeba;
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  animation: fadeIn 0.3s ease-out;
}

.error-alert {
  background-color: #f8d7da;
  color: #721c24;
  border-color: #f5c6cb;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
}

.close-alert {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: inherit;
  line-height: 1;
  opacity: 0.5;
}

.close-alert:hover {
  opacity: 1;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.header-section h2 { margin: 0; color: #2c3e50; }

.header-actions { display: flex; gap: 20px; align-items: center; }

.search-box { display: flex; gap: 8px; }

.search-input {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  width: 250px;
  font-size: 0.9rem;
}

.search-btn {
  padding: 8px 16px;
  background: #f8f9fa;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.search-btn:hover { background: #e9ecef; }

/* Column Selector Styles */
.column-selector-card {
  background: #fdfdfd;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  border: 1px solid #edf2f7;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 8px;
}

.selector-header strong {
  font-size: 0.9rem;
  color: #2c3e50;
}

.selector-actions {
  display: flex;
  gap: 10px;
}

.selector-actions button {
  background: none;
  border: 1px solid #ddd;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
}

.selector-actions button:hover {
  border-color: #3498db;
  color: #3498db;
  background: #f7fbff;
}

.checkbox-group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px 15px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: #555;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}

.checkbox-item input {
  cursor: pointer;
}

.table-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  overflow-x: auto;
}

.table-header-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #f0f0f0;
}

.header-left span { color: #7f8c8d; font-size: 0.9rem; font-weight: 500; }

.modern-pagination { display: flex; align-items: center; gap: 20px; }

.page-size-picker { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: #606266; }

.modern-select {
  padding: 4px 24px 4px 8px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  outline: none;
  cursor: pointer;
  background: white;
  transition: all 0.2s;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  font-size: 0.85rem;
  color: #606266;
  background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 6px center;
  background-size: 14px;
  min-width: 60px;
  height: 32px;
}

.modern-select:hover { border-color: #3498db; }

.pager-actions { display: flex; align-items: center; gap: 4px; }

.pager-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  color: #606266;
  transition: all 0.2s;
}

.pager-btn:hover:not(:disabled) { border-color: #3498db; color: #3498db; background: #f0f7ff; }

.pager-btn:disabled { color: #c0c4cc; cursor: not-allowed; background: #f5f7fa; }

.pager-btn .material-symbols-outlined { font-size: 1.2rem; }

.pager-info { display: flex; align-items: center; gap: 6px; margin: 0 8px; }

.pager-input {
  width: 40px;
  height: 28px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  text-align: center;
  font-size: 0.85rem;
  outline: none;
}

.pager-input:focus { border-color: #3498db; }

.pager-total { font-size: 0.85rem; color: #909399; }

.active-sorts-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 1rem;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 6px;
  font-size: 0.85rem;
}

.sort-label { font-weight: 600; color: #555; }

.sort-tags { display: flex; gap: 8px; flex-wrap: wrap; }

.sort-tag {
  display: inline-flex;
  align-items: center;
  background: #e3f2fd;
  color: #1565c0;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.8rem;
  border: 1px solid #bbdefb;
}

.sort-dir { margin-left: 4px; font-size: 0.7rem; opacity: 0.8; font-weight: bold; }

.remove-sort { margin-left: 6px; cursor: pointer; font-weight: bold; opacity: 0.6; }

.remove-sort:hover { opacity: 1; color: #c62828; }

.clear-sort-btn {
  background: none;
  border: none;
  color: #666;
  text-decoration: underline;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 0 4px;
}

.clear-sort-btn:hover { color: #d32f2f; }

.sort-hint { margin-left: auto; color: #888; font-style: italic; font-size: 0.8rem; }

.table-wrapper { min-width: 1500px; }

.styled-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }

.styled-table th, .styled-table td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid #eee;
  white-space: nowrap;
  vertical-align: middle;
}

.styled-table th { background-color: #f8f9fa; font-weight: bold; color: #333; }

.styled-table th.sortable { cursor: pointer; user-select: none; }

.styled-table th.sortable:hover { background: #edf2f7; color: #3498db; }

.header-container { display: flex; align-items: center; gap: 4px; }

.sort-indicator { display: inline-flex; align-items: center; position: relative; }

.sort-icon { font-size: 16px !important; color: #ccc; transition: color 0.2s; }

.sort-icon.active { color: #3498db; }

.sort-order {
  font-size: 10px;
  background: #3498db;
  color: white;
  border-radius: 50%;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  right: -8px;
  top: -4px;
}

.styled-table tbody tr:hover { background-color: #fcfcfc; }

.spark-version-badge {
  background-color: #e8f4f8;
  color: #2980b9;
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #d1e9f0;
  font-weight: 600;
  white-space: nowrap;
}

.compression-badge {
  display: inline-block;
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
  background-color: #f0f0f0;
  color: #666;
  border: 1px solid #ddd;
}

.compression-badge.zstd { background-color: #e1f5fe; color: #0288d1; border-color: #b3e5fc; }
.compression-badge.gzip { background-color: #efebe9; color: #5d4037; border-color: #d7ccc8; }
.compression-badge.lz4 { background-color: #f1f8e9; color: #388e3c; border-color: #dcedc8; }
.compression-badge.snappy { background-color: #fff3e0; color: #f57c00; border-color: #ffe0b2; }

.size-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.est-size {
  color: #7f8c8d;
  font-size: 0.75rem;
  font-style: italic;
}

.app-link { color: #3498db; text-decoration: none; font-weight: 600; }

.app-link:hover { text-decoration: underline; color: #2980b9; }

.app-id-code {
  white-space: nowrap;
  font-family: monospace;
  font-size: 0.8rem;
  color: #c7254e;
  background-color: #f9f2f4;
  padding: 2px 4px;
  border-radius: 3px;
}

.processing-label {
  color: #3498db;
  font-size: 0.85rem;
  font-style: italic;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.eta-text, .percent-text {
  font-weight: normal;
  color: #2980b9;
}

.notes-cell {
  min-width: 200px;
  cursor: pointer;
}

.notes-text {
  padding: 4px 8px;
  border-radius: 4px;
  min-height: 24px;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notes-text:hover {
  background-color: #f0f7ff;
}

.empty-notes {
  color: #bdc3c7;
  font-style: italic;
  font-size: 0.85rem;
}

.notes-input {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid #3498db;
  border-radius: 4px;
  outline: none;
  font-size: 0.9rem;
  background: white;
}

.processing-row { opacity: 0.7; background-color: #f9f9f9; }

.disabled-link { pointer-events: none; color: #95a5a6 !important; cursor: default; }

.name-cell-wrapper { display: flex; flex-direction: column; gap: 4px; }

.overwrite-prompt {
  background: #fff3cd;
  border: 1px solid #ffeeba;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  pointer-events: auto !important;
}

.prompt-text { color: #856404; font-weight: 600; }

.prompt-actions { display: flex; gap: 4px; }

.mini-btn {
  padding: 2px 6px;
  border-radius: 3px;
  border: none;
  cursor: pointer;
  font-size: 0.7rem;
  font-weight: bold;
}

.mini-btn.confirm { background: #27ae60; color: white; }

.mini-btn.cancel { background: #bdc3c7; color: #333; }

.progress-cell { width: 100%; }

.na-progress {
  text-align: center;
  color: #bdc3c7;
  font-family: monospace;
}

.progress-track {
  width: 100%;
  height: 16px;
  background-color: #e9ecef;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
}

.progress-fill {
  height: 100%;
  transition: width 0.6s ease;
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1;
}

.progress-text-overlay {
  position: relative;
  z-index: 2;
  font-size: 0.7rem;
  font-weight: bold;
  color: #333;
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.8);
}

.status-cell { display: flex; align-items: center; }

.status-badge {
  font-weight: bold;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.status-SUCCESS, .status-FINISHED { color: #27ae60; background-color: rgba(39, 174, 96, 0.1); }

.status-FAILED { color: #e74c3c; background-color: rgba(231, 76, 60, 0.1); }

.status-INGESTING_BRONZE { color: #cd7f32; background-color: rgba(205, 127, 50, 0.1); }
.status-TRANSFORMING_SILVER { color: #7f8c8d; background-color: rgba(149, 165, 166, 0.1); }
.status-AGGREGATING_GOLD { color: #f1c40f; background-color: rgba(241, 196, 15, 0.1); }

.status-LOADING, .status-RUNNING { 
  color: #3498db; background-color: rgba(52, 152, 219, 0.1); 
}

.status-PENDING_LOAD, .status-PENDING_TO_LOADING, .status-READY { 
  color: #95a5a6; background-color: rgba(149, 165, 166, 0.1); 
}

.status-PENDING_REIMPORT, .status-PENDING_OVERWRITE { 
  color: #e67e22; background-color: rgba(230, 126, 34, 0.1); 
}

.status-PRE_CALCULATING { color: #9b59b6; background-color: rgba(155, 89, 182, 0.1); }

.select-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #909399;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.select-btn:hover:not(:disabled) { color: #3498db; background: #f0f7ff; }

.select-btn.selected { color: #3498db; }

.select-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.select-btn .material-symbols-outlined { font-size: 20px; }

.action-cell {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px solid #ddd;
  background: white;
  cursor: pointer;
  font-size: 0.8rem;
  font-weight: 500;
  transition: all 0.2s;
  color: #606266;
}

.action-btn .material-symbols-outlined {
  font-size: 16px;
}

.action-btn:hover:not(:disabled) {
  background: #f5f7fa;
}

.action-btn.reimport:hover:not(:disabled) {
  border-color: #3498db;
  color: #3498db;
  background: #f0f7ff;
}

.action-btn.bronze:hover:not(:disabled) {
  border-color: #9b59b6;
  color: #9b59b6;
  background: #f5ebf7;
}

.action-btn.delete:hover:not(:disabled) {
  border-color: #e74c3c;
  color: #e74c3c;
  background: #fff5f5;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #f5f7fa;
}

.dropdown-trigger-wrapper {
  display: inline-block;
}

.dropdown-menu-fixed {
  position: fixed;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 9999; /* High z-index to be on top of everything */
  min-width: 220px;
  display: flex;
  flex-direction: column;
  padding: 4px 0;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: none;
  background: none;
  width: 100%;
  text-align: left;
  cursor: pointer;
  font-size: 0.85rem;
  color: #333;
  transition: background 0.2s;
}

.dropdown-item:hover {
  background-color: #f5f7fa;
  color: #3498db;
}

.dropdown-item .material-symbols-outlined {
  font-size: 16px;
  color: #7f8c8d;
}

.dropdown-item:hover .material-symbols-outlined {
  color: #3498db;
}
</style>
