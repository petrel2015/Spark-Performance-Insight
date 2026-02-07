import { reactive, watch } from 'vue';

const STORAGE_KEY_MODE = 'spark_insight_compare_mode';
const STORAGE_KEY_ITEMS = 'spark_insight_compare_items';

// Helper to load from local storage
const loadMode = (): boolean => localStorage.getItem(STORAGE_KEY_MODE) === 'true';
const loadItems = (): any[] => {
  const stored = localStorage.getItem(STORAGE_KEY_ITEMS);
  try {
    return stored ? JSON.parse(stored) : [];
  } catch (e) {
    return [];
  }
};

export const compareStore = reactive({
  isCompareMode: loadMode(),
  selectedItems: loadItems() as Array<{
    id: string; // unique key (e.g. "appId:type:itemId")
    type: 'job' | 'stage' | 'app';
    appId: string;
    itemId: number | string; // jobId or stageId
    name?: string;
    details?: any;
  }>,
  
  // Track which IDs are currently checked for the active comparison action
  comparisonSelection: [] as string[],

  toggleCompareMode() {
    this.isCompareMode = !this.isCompareMode;
  },

  addItem(item: { type: 'job' | 'stage' | 'app', appId: string, itemId: number | string, name?: string, details?: any }) {
    const key = `${item.appId}:${item.type}:${item.itemId}`;
    if (!this.selectedItems.find(i => i.id === key)) {
      this.selectedItems.push({ ...item, id: key });
    }
  },

  removeItem(key: string) {
    const index = this.selectedItems.findIndex(i => i.id === key);
    if (index > -1) {
      this.selectedItems.splice(index, 1);
      // Also remove from comparison selection if present
      this.toggleComparisonItem(key, false);
    }
  },

  toggleComparisonItem(key: string, force?: boolean) {
    const index = this.comparisonSelection.indexOf(key);
    if (force === true || (force === undefined && index === -1)) {
      if (index === -1) this.comparisonSelection.push(key);
    } else {
      if (index > -1) this.comparisonSelection.splice(index, 1);
    }
  },

  isItemSelected(key: string) {
    return this.comparisonSelection.includes(key);
  },

  hasItem(type: string, appId: string, itemId: number | string) {
    const key = `${appId}:${type}:${itemId}`;
    return this.selectedItems.some(i => i.id === key);
  },
  
  clear() {
    this.selectedItems = [];
    this.comparisonSelection = [];
  }
});

// Watch for changes and sync to localStorage
watch(() => compareStore.isCompareMode, (val) => {
  localStorage.setItem(STORAGE_KEY_MODE, String(val));
});

watch(() => compareStore.selectedItems, (val) => {
  localStorage.setItem(STORAGE_KEY_ITEMS, JSON.stringify(val));
}, { deep: true });
