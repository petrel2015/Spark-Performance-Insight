<template>
  <div class="compare-result-container" v-if="result">
    <div class="header-section">
      <div class="title-row">
        <h2>{{ result.type }} Performance Comparison</h2>
      </div>
      <div class="comparison-meta">
        <!-- Baseline Item Card -->
        <div class="meta-item-card">
          <span class="label">Baseline (Source)</span>
          <div class="item-name" :title="result.source.name">{{ result.source.name }}</div>
          <div class="app-identifier">
            <span class="material-symbols-outlined">Apps</span>
            <code class="app-id-text">{{ result.source.appId }}</code>
          </div>
          <div class="item-stats">
            <span class="stat-badge id-badge">ID: {{ result.source.id }}</span>
            <span v-if="result.source.duration" class="stat-badge">
              <span class="material-symbols-outlined">timer</span>
              {{ formatTime(result.source.duration) }}
            </span>
            <span v-if="result.source.stageCount" class="stat-badge">
              <span class="material-symbols-outlined">account_tree</span>
              {{ result.source.stageCount }} Stages
            </span>
            <span v-if="result.source.taskCount" class="stat-badge">
              <span class="material-symbols-outlined">task_alt</span>
              {{ result.source.taskCount }} Tasks
            </span>
          </div>
          <div class="meta-actions">
            <router-link :to="getItemDetailRoute(result.source)" class="view-detail-btn">
              View Detail
            </router-link>
          </div>
        </div>

        <div class="arrow-container">
          <span class="material-symbols-outlined">compare_arrows</span>
          <span class="vs-text">VS</span>
        </div>

        <!-- Target Item Card -->
        <div class="meta-item-card">
          <span class="label">Comparison (Target)</span>
          <div class="item-name" :title="result.target.name">{{ result.target.name }}</div>
          <div class="app-identifier">
            <span class="material-symbols-outlined">Apps</span>
            <code class="app-id-text">{{ result.target.appId }}</code>
          </div>
          <div class="item-stats">
            <span class="stat-badge id-badge">ID: {{ result.target.id }}</span>
            <span v-if="result.target.duration" class="stat-badge">
              <span class="material-symbols-outlined">timer</span>
              {{ formatTime(result.target.duration) }}
            </span>
            <span v-if="result.target.stageCount" class="stat-badge">
              <span class="material-symbols-outlined">account_tree</span>
              {{ result.target.stageCount }} Stages
            </span>
            <span v-if="result.target.taskCount" class="stat-badge">
              <span class="material-symbols-outlined">task_alt</span>
              {{ result.target.taskCount }} Tasks
            </span>
          </div>
          <div class="meta-actions">
            <router-link :to="getItemDetailRoute(result.target)" class="view-detail-btn">
              View Detail
            </router-link>
          </div>
        </div>
      </div>
    </div>

    <!-- 1. Executive Summary -->
    <div class="conclusion-card" :class="result.conclusionType.toLowerCase()">
      <span class="material-symbols-outlined icon">
        {{ getConclusionIcon(result.conclusionType) }}
      </span>
      <div class="conclusion-content">
        <h3>{{ result.conclusion }}</h3>
        <p>Expert summary based on automated heuristics and statistical analysis.</p>
      </div>
    </div>

    <!-- 2. Metrics Diff Table -->
    <div class="card main-metrics">
      <h3 class="card-title">Performance Metrics Breakdown</h3>
      <div class="table-responsive">
        <table class="diff-table">
          <thead>
            <tr>
              <th>Metric</th>
              <th>Baseline</th>
              <th>Target</th>
              <th>Delta</th>
              <th>Change (%)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in result.keyMetrics" :key="m.name">
              <td class="metric-label-col">
                <span class="m-label">{{ m.label }}</span>
              </td>
              <td class="val">{{ formatMetric(m.sourceValue, m.unit) }}</td>
              <td class="val">{{ formatMetric(m.targetValue, m.unit) }}</td>
              <td class="val" :class="{ 'negative': m.delta < 0, 'positive': m.delta > 0 }">
                {{ m.delta > 0 ? '+' : '' }}{{ formatMetric(m.delta, m.unit) }}
              </td>
              <td class="pct">
                <span class="diff-badge" :class="m.severity.toLowerCase()">
                  {{ m.pctChange > 0 ? '+' : '' }}{{ m.pctChange.toFixed(1) }}%
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 3. Config/Resource Context Table -->
    <div class="card resources-section" v-if="result.configDiffs && result.configDiffs.length > 0">
      <h3 class="card-title">Environment Configuration Comparison</h3>
      <p class="section-hint">Listing all differing configurations across Spark properties, JVM, and Hadoop settings. Identical items are hidden.</p>
      <div class="table-responsive">
        <table class="diff-table config-table">
          <thead>
            <tr>
              <th style="text-align: left; width: 15%">Category</th>
              <th style="text-align: left; width: 35%">Configuration Key</th>
              <th style="width: 25%">Baseline Value</th>
              <th style="width: 25%">Target Value</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in result.configDiffs" :key="c.category + ':' + c.key">
              <td style="text-align: left">
                <span class="category-tag">{{ c.category.replace('_', ' ') }}</span>
              </td>
              <td class="config-key-col" style="text-align: left">{{ c.key }}</td>
              <td class="val code-font">{{ c.sourceValue }}</td>
              <td class="val code-font" :class="{ 'highlight-diff': c.sourceValue !== c.targetValue }">
                {{ c.targetValue }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-else-if="result" class="card empty-config-card">
      <h3 class="card-title">Environment Configuration Comparison</h3>
      <div class="empty-state">
        <span class="material-symbols-outlined">check_circle</span>
        <p>No configuration differences detected between these two runs. Environment settings are identical.</p>
      </div>
    </div>
  </div>
  <div v-else class="loading-container">
    <div class="spinner"></div>
    <p>Crunching metrics and identifying bottlenecks...</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getComparisonResult } from '../api';
import { formatTime, formatBytes, formatNum } from '../utils/format';

const route = useRoute();
const result = ref(null);

const fetchResult = async () => {
  const { type, app1, id1, app2, id2 } = route.query;
  try {
    const res = await getComparisonResult(type, app1, id1, app2, id2);
    result.value = res.data;
  } catch (e) {
    console.error("Comparison failed", e);
  }
};

const getItemDetailRoute = (item) => {
  const type = result.value.type.toLowerCase();
  return `/app/${item.appId}/${type}/${item.id}`;
};

const getConclusionIcon = (type) => {
  if (type === 'IMPROVED') return 'verified'; 
  if (type === 'REGRESSED') return 'report'; 
  return 'info';
};

const formatMetric = (val, unit) => {
  if (val === null || val === undefined) return '-';
  if (unit === 'ms') return formatTime(val);
  if (unit === 'bytes') return formatBytes(Math.abs(val)) || '0 B';
  if (unit === 'count') return formatNum(Math.abs(val));
  return val.toFixed(1);
};

onMounted(fetchResult);
</script>

<style scoped>
.compare-result-container {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.header-section {
  margin-bottom: 1rem;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1.5rem;
}

.title-row h2 {
  margin: 0;
  color: #2c3e50;
  font-size: 1.5rem;
}

.comparison-meta {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  border: 1px solid #eee;
}

.comparison-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.meta-item-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: white;
  padding: 1.2rem;
  border-radius: 10px;
  border: 1px solid #eee;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
  text-align: left;
}

.item-name {
  font-weight: 600;
  color: #34495e;
  font-size: 0.95rem;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-identifier {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  padding: 6px 8px;
  background: #f1f3f5;
  border-radius: 4px;
  color: #606266;
  width: fit-content;
  transition: all 0.2s;
}

.app-identifier .material-symbols-outlined {
  font-size: 16px;
}

.app-id-text {
  font-size: 0.75rem;
  font-family: monospace;
}

.item-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
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

.stat-badge .material-symbols-outlined {
  font-size: 14px;
}

.id-badge {
  background: #fff;
  border-color: #3498db;
  color: #3498db;
  font-family: 'Roboto Mono', monospace;
  font-weight: 700;
}

.meta-actions {
  margin-top: 10px;
}

.view-detail-btn {
  display: inline-block;
  padding: 4px 12px;
  background: #f8f9fa;
  border: 1px solid #dcdfe6;
  color: #3498db;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
}

.view-detail-btn:hover {
  background: #3498db;
  color: white;
  border-color: #3498db;
}

.arrow-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #3498db;
  padding: 0 0.5rem;
  gap: 4px;
}

.arrow-container .material-symbols-outlined {
  font-size: 32px;
}

.vs-text {
  font-weight: 900;
  font-size: 0.8rem;
  color: #bdc3c7;
}

.label {
  font-size: 0.7rem;
  text-transform: uppercase;
  color: #909399;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 2px;
}

.conclusion-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 1.5rem 2rem;
  border-radius: 12px;
  border-left: 6px solid;
  box-shadow: 0 4px 10px rgba(0,0,0,0.03);
}

.conclusion-card.improved {
  background: #f0fdf4;
  border-color: #27ae60;
  color: #166534;
}

.conclusion-card.regressed {
  background: #fef2f2;
  border-color: #ef4444;
  color: #991b1b;
}

.conclusion-card.similar {
  background: #f9fafb;
  border-color: #94a3b8;
  color: #475569;
}

.conclusion-card .icon {
  font-size: 40px !important;
}

.conclusion-card h3 {
  margin: 0 0 4px 0;
  font-size: 1.25rem;
}

.conclusion-card p {
  margin: 0;
  font-size: 0.9rem;
  opacity: 0.8;
}

.card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 4px 15px rgba(0,0,0,0.05);
  border: 1px solid #eee;
}

.card-title {
  margin-top: 0;
  margin-bottom: 1.5rem;
  color: #2c3e50;
  font-size: 1.1rem;
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-responsive {
  overflow-x: auto;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
}

.diff-table th {
  text-align: right;
  padding: 12px 15px;
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
}

.diff-table th:first-child {
  text-align: left;
}

.diff-table td {
  padding: 15px;
  border-bottom: 1px solid #f1f5f9;
  text-align: right;
  vertical-align: middle;
}

.diff-table td:first-child {
  text-align: left;
}

.metric-label-col {
  font-weight: 600;
  color: #334155;
}

.val {
  font-family: 'Roboto Mono', monospace;
  font-size: 0.9rem;
  color: #475569;
}

.positive { color: #e11d48; }
.negative { color: #059669; }

.diff-badge {
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 700;
  display: inline-block;
}

.diff-badge.critical { background: #fee2e2; color: #b91c1c; }
.diff-badge.warning { background: #fef3c7; color: #92400e; }
.diff-badge.good { background: #dcfce7; color: #15803d; }
.diff-badge.neutral { background: #f1f5f9; color: #64748b; }

/* Resources Section */
.section-hint {
  color: #94a3b8;
  font-size: 0.85rem;
  margin-bottom: 1.5rem;
}

.config-table th {
  /* Inherit from diff-table for consistency */
}

.config-table td {
  /* Inherit from diff-table for consistency */
  word-break: break-all;
}

.config-key-col {
  font-weight: 600;
  color: #475569;
  font-size: 0.85rem;
}

.config-category-col {
  text-align: left;
}

.category-tag {
  font-size: 0.65rem;
  background: #f1f3f5;
  color: #64748b;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  font-weight: bold;
  white-space: nowrap;
}

.empty-config-card {
  background: white;
  border: 1px solid #eee;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  color: #94a3b8;
  gap: 10px;
}

.empty-state .material-symbols-outlined {
  font-size: 40px;
  color: #27ae60;
}

.empty-state p {
  margin: 0;
  font-size: 0.9rem;
}

.code-font {
  font-family: 'Roboto Mono', monospace;
  font-size: 0.85rem;
  color: #334155;
}

.highlight-diff {
  color: #e11d48;
  background-color: #fff1f2;
  font-weight: 600;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  color: #64748b;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f1f5f9;
  border-top: 4px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>
