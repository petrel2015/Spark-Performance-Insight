<template>
  <div class="stage-summary-container">
    <!-- Active Sorts Display -->
    <div v-if="sorts.length > 0" class="active-sorts-bar">
      <span class="sort-label">Sort by:</span>
      <div class="sort-tags">
        <span v-for="(sort, index) in sorts" :key="sort.field" class="sort-tag">
          {{ getColumnLabel(sort.field) }} 
          <span class="sort-dir">{{ sort.dir === 'asc' ? 'ASC' : 'DESC' }}</span>
          <span @click="removeSort(index)" class="remove-sort" title="Remove sort">×</span>
        </span>
      </div>
      <button @click="clearSorts" class="clear-sort-btn">Clear All</button>
    </div>

    <div class="matrix-wrapper">
      <table class="matrix-table">
        <thead>
        <tr>
          <th @click="handleSort('label', $event)" class="sortable">Metric {{ getSortIcon('label') }}</th>
          <th @click="handleSort('minValue', $event)" class="sortable">Min {{ getSortIcon('minValue') }}</th>
          <th @click="handleSort('p25', $event)" class="sortable">25th Pct {{ getSortIcon('p25') }}</th>
          <th @click="handleSort('p50', $event)" class="sortable">Median {{ getSortIcon('p50') }}</th>
          <th @click="handleSort('p75', $event)" class="sortable">75th Pct {{ getSortIcon('p75') }}</th>
          <th @click="handleSort('maxValue', $event)" class="sortable">Max {{ getSortIcon('maxValue') }}</th>
          <th class="total-col">Total</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="row in sortedRows" :key="row.label">
          <td class="metric-label">{{ row.label }}</td>
          <td v-for="key in ['minValue', 'p25', 'p50', 'p75', 'maxValue']" :key="key">
            {{ formatCell(row, key) }}
          </td>
          <td class="total-cell">{{ formatTotal(row) }}</td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import {ref, computed} from 'vue';
import {formatTime, formatBytes, formatNum} from '../../utils/format';

const props = defineProps({
  stats: {type: Array, default: () => []},
  stageId: [Number, String],
  visibleMetrics: {type: Array, default: () => []},
  stage: {type: Object, default: () => ({})}
});

const sorts = ref([]);

const getStat = (name) => props.stats.find(s => s.metricName === name);

const allPossibleRows = computed(() => {
  const rows = [];
  const addRow = (key, label, type) => {
    const s = getStat(key);
    rows.push({key, label, type, data: s || {}});
  };
  const addComposite = (key, nameBytes, nameRecords, label) => {
    rows.push({
      key, label, type: 'composite',
      bytes: getStat(nameBytes) || {},
      records: getStat(nameRecords) || {}
    });
  };

  addRow('task_deserialization_time', 'Task Deserialization Time', 'time');
  addRow('duration', 'Duration', 'time');
  addRow('gc_time', 'GC Time', 'time');
  addRow('result_serialization_time', 'Result Serialization Time', 'time');
  addRow('getting_result_time', 'Getting Result Time', 'time');
  addRow('scheduler_delay', 'Scheduler Delay', 'time');
  addRow('peak_execution_memory', 'Peak Execution Memory', 'bytes');
  addRow('memory_spill', 'Spill (memory)', 'bytes');
  addRow('disk_spill', 'Spill (disk)', 'bytes');
  addComposite('input', 'input_bytes', 'input_records', 'Input Size / Records');
  addComposite('output', 'output_bytes', 'output_records', 'Output Size / Records');
  addComposite('shuffle_read', 'shuffle_read_bytes', 'shuffle_read_records', 'Shuffle Read Size / Records');
  addComposite('shuffle_write', 'shuffle_write_bytes', 'shuffle_write_records', 'Shuffle Write Size / Records');
  addRow('shuffle_write_time', 'Shuffle Write Time', 'nanos');

  return rows;
});

const sortedRows = computed(() => {
  let result = allPossibleRows.value.filter(row => props.visibleMetrics.includes(row.key));

  if (sorts.value.length === 0) return result;

  return [...result].sort((a, b) => {
    for (const sort of sorts.value) {
      let valA, valB;
      if (sort.field === 'label') {
        valA = a.label;
        valB = b.label;
      } else {
        valA = a.type === 'composite' ? a.bytes[sort.field] : a.data[sort.field];
        valB = b.type === 'composite' ? b.bytes[sort.field] : b.data[sort.field];
      }

      if (valA === valB) continue;
      if (valA == null) return 1;
      if (valB == null) return -1;
      const res = valA < valB ? -1 : 1;
      return sort.dir === 'asc' ? res : -res;
    }
    return 0;
  });
});

const handleSort = (field, event) => {
  const existingIndex = sorts.value.findIndex(s => s.field === field);
  const isShift = event.shiftKey;
  if (existingIndex > -1) {
    const existing = sorts.value[existingIndex];
    if (existing.dir === 'asc') existing.dir = 'desc';
    else sorts.value.splice(existingIndex, 1);
  } else {
    if (!isShift) sorts.value = [];
    sorts.value.push({field, dir: 'asc'});
  }
};

const removeSort = (index) => {
  sorts.value.splice(index, 1);
};
const clearSorts = () => {
  sorts.value = [];
};
const getSortIcon = (field) => {
  const index = sorts.value.findIndex(x => x.field === field);
  if (index === -1) return '↕';
  const s = sorts.value[index];
  const icon = s.dir === 'asc' ? '↑' : '↓';
  return sorts.value.length > 1 ? `${icon}${index + 1}` : icon;
};
const getColumnLabel = (field) => {
  const labels = {label: 'Metric', minValue: 'Min', p25: '25th Pct', p50: 'Median', p75: '75th Pct', maxValue: 'Max'};
  return labels[field] || field;
};

const formatCell = (row, key) => {
  if (row.type === 'composite') {
    const b = row.bytes[key];
    const r = row.records[key];
    if (b === undefined && r === undefined) return '-';
    return `${formatBytes(b || 0)} / ${formatNum(r || 0)}`;
  }
  const val = row.data[key];
  if (val === undefined || val === null) return '-';

  if (row.type === 'time' || row.type === 'nanos') {
    const msValue = row.type === 'nanos' ? val / 1000000 : val;
    const timeStr = formatTime(msValue);

    // Show percentage for time metrics other than Duration
    if (row.key !== 'duration' && props.stage && props.stage.duration > 0) {
      // Note: We compare individual task metric stats vs Stage TOTAL duration? 
      // Actually, Stage Duration is usually wall clock time.
      // But typically, we compare Task metric vs Task Duration.
      // The summary table shows stats (Min, Median, Max) of metrics across tasks.
      // So if Max GC is 1s, and Max Task Duration is 10s, it's 10%.
      // BUT here we don't have the corresponding "Total Task Duration" for that specific task in this context easily (it's aggregated stats).
      // However, we can use the P50/Max of the specific metric vs P50/Max of the 'duration' row to give a rough idea?
      // Or simply % of the Stage Duration?
      // Spark UI shows % of "Task Duration" usually.
      // Since we don't have the reference "Task Duration" for the specific task that had Min/Max GC, 
      // we can't be 100% precise unless we know it's the SAME task.
      // But usually, comparing Median GC vs Median Duration is a valid statistical insight.

      const durationStat = getStat('duration');
      if (durationStat && durationStat[key]) { // key is 'minValue', 'p50', etc.
        const refDuration = durationStat[key];
        if (refDuration > 0) {
          const pct = Math.round((msValue / refDuration) * 100);
          return `${timeStr} (${pct}%)`;
        }
      }
    }
    return timeStr;
  }

  if (row.type === 'bytes') return formatBytes(val);
  return val;
};

const formatTotal = (row) => {
  if (!props.stage) return '-';
  const s = props.stage;

  const formatTimeWithPct = (ms) => {
    const timeStr = formatTime(ms);
    if (row.key !== 'duration' && s.tasksDurationSum > 0) {
      const pct = Math.round((ms / s.tasksDurationSum) * 100);
      return `${timeStr} (${pct}%)`;
    }
    return timeStr;
  };

  switch (row.key) {
    case 'duration':
      return formatTime(s.tasksDurationSum || 0);
    case 'gc_time':
      return formatTimeWithPct(s.gcTimeSum || 0);
    case 'task_deserialization_time':
      return formatTimeWithPct(s.executorDeserializeTimeSum || 0);
    case 'result_serialization_time':
      return formatTimeWithPct(s.resultSerializationTimeSum || 0);
    case 'getting_result_time':
      return formatTimeWithPct(s.gettingResultTimeSum || 0);
    case 'scheduler_delay':
      return formatTimeWithPct(s.schedulerDelaySum || 0);
    case 'shuffle_write_time':
      return formatTimeWithPct((s.shuffleWriteTimeSum || 0) / 1000000);
    case 'peak_execution_memory':
      return formatBytes(s.peakExecutionMemorySum || 0);
    case 'memory_spill':
      return formatBytes(s.memoryBytesSpilledSum || 0);
    case 'disk_spill':
      return formatBytes(s.diskBytesSpilledSum || 0);
    case 'input':
      return `${formatBytes(s.inputBytes || 0)} / ${formatNum(s.inputRecords || 0)}`;
    case 'output':
      return `${formatBytes(s.outputBytes || 0)} / ${formatNum(s.outputRecords || 0)}`;
    case 'shuffle_read':
      return `${formatBytes(s.shuffleReadBytes || 0)} / ${formatNum(s.shuffleReadRecords || 0)}`;
    case 'shuffle_write':
      return `${formatBytes(s.shuffleWriteBytes || 0)} / ${formatNum(s.shuffleWriteRecords || 0)}`;
    default:
      return '-';
  }
};
</script>

<style scoped>
.stage-summary-container {
  width: 100%;
}

.matrix-wrapper {
  overflow-x: auto;
}

.matrix-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.matrix-table th, .matrix-table td {
  padding: 12px 8px;
  text-align: right;
  border-bottom: 1px solid #eee;
}

.matrix-table th.sortable {
  cursor: pointer;
  user-select: none;
}

.matrix-table th.sortable:hover {
  background: #edf2f7;
  color: #3498db;
}

.matrix-table tbody tr:hover {
  background-color: #f7fbff;
}

.matrix-table th:first-child {
  text-align: left;
}

.matrix-table .metric-label {
  text-align: left;
  font-weight: bold;
  color: #2c3e50;
}

.total-col {
  background: #f8f9fa;
  font-weight: bold;
  color: #1565c0;
  border-left: 2px solid #e3f2fd;
}

.total-cell {
  background: #fbfcfe;
  font-weight: 600;
  color: #1565c0;
  border-left: 2px solid #e3f2fd;
}

/* Active Sorts Bar (Sync style) */
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
  border: 1px solid #eee;
}

.sort-label {
  font-weight: 600;
  color: #555;
}

.sort-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

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

.sort-dir {
  margin-left: 4px;
  font-size: 0.7rem;
  opacity: 0.8;
  font-weight: bold;
}

.remove-sort {
  margin-left: 6px;
  cursor: pointer;
  font-weight: bold;
  opacity: 0.6;
}

.remove-sort:hover {
  opacity: 1;
  color: #c62828;
}

.clear-sort-btn {
  background: none;
  border: none;
  color: #666;
  text-decoration: underline;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 0 4px;
}

.clear-sort-btn:hover {
  color: #d32f2f;
}
</style>