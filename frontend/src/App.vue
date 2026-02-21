<template>
  <div class="container">
    <nav class="navbar">
      <div class="brand-wrapper">
        <h3 class="brand">Spark Performance Insight</h3>
        <span class="system-version">v{{ SYSTEM_VERSION }}</span>
      </div>
      <div class="links">
        <router-link to="/">Application List</router-link>
        
        <div class="compare-group">
          <div class="compare-toggle-wrapper">
            <label class="toggle-switch">
              <input type="checkbox" :checked="compareStore.isCompareMode" @change="compareStore.toggleCompareMode()">
              <span class="slider round"></span>
            </label>
            <span class="toggle-label" :class="{ active: compareStore.isCompareMode }">Compare Mode</span>
          </div>

          <router-link v-if="compareStore.isCompareMode" to="/compare" class="compare-link">
            <span class="material-symbols-outlined nav-icon">compare_arrows</span>
            Compare Workspace
            <span v-if="compareStore.selectedItems.length > 0" class="badge">{{ compareStore.selectedItems.length }}</span>
          </router-link>
        </div>
      </div>
    </nav>

    <main class="main-content">
      <router-view></router-view>
    </main>

    <footer class="footer">
      <div class="footer-content">
        <div class="footer-left">
          <div class="copyright">
            © 2026 Spark Performance Insight. Built with <span class="heart">❤</span> by AI & Human.
          </div>
        </div>
        <div class="footer-right">
          <a href="https://github.com/hongyusu/Spark-Performance-Insight" target="_blank" class="github-btn" title="Star on GitHub">
            <svg class="github-icon" viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
              <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"></path>
            </svg>
            <span class="star-text">Star</span>
            <span v-if="stars !== null" class="star-count">{{ stars }}</span>
          </a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { compareStore } from './store/compareStore';
import { SYSTEM_VERSION } from './constants/config';

const stars = ref(null);

onMounted(async () => {
  try {
    const res = await fetch('https://api.github.com/repos/hongyusu/Spark-Performance-Insight');
    const data = await res.json();
    if (data.stargazers_count !== undefined) {
      stars.value = data.stargazers_count;
    }
  } catch (e) {
    console.error('Failed to fetch GitHub stars', e);
  }
});
</script>

<style scoped>
.container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

.navbar {
  background: #2c3e50;
  color: white;
  padding: 0.5rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 48px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  z-index: 1000;
  flex-shrink: 0;
}

.brand-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.system-version {
  font-size: 0.7rem;
  background: rgba(255, 255, 255, 0.15);
  padding: 2px 8px;
  border-radius: 12px;
  color: #bdc3c7;
  font-family: 'Roboto Mono', monospace;
}

.links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.links a {
  color: #ecf0f1;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 500;
  opacity: 0.85;
  transition: all 0.2s;
  padding: 4px 0;
}

.links a:hover {
  opacity: 1;
  color: #42b983;
}

.links a.router-link-active {
  opacity: 1;
  color: #42b983;
  border-bottom: 2px solid #42b983;
}

.compare-group {
  display: flex;
  align-items: center;
  gap: 1.2rem;
  padding-left: 1.2rem;
  border-left: 1px solid rgba(255,255,255,0.1);
}

.compare-toggle-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.85rem;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 34px;
  height: 18px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: #4b6584;
  transition: .3s;
  border-radius: 18px;
}

.slider:before {
  position: absolute;
  content: "";
  height: 12px; width: 12px;
  left: 3px; bottom: 3px;
  background-color: white;
  transition: .3s;
  border-radius: 50%;
}

input:checked + .slider {
  background-color: #42b983;
}

input:checked + .slider:before {
  transform: translateX(16px);
}

.toggle-label {
  color: #95a5a6;
  user-select: none;
}

.toggle-label.active {
  color: #42b983;
  font-weight: 600;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.footer {
  background: white;
  border-top: 1px solid #e1e8ed;
  padding: 1rem 2rem;
  flex-shrink: 0;
}

.footer-content {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.copyright {
  color: #7f8c8d;
  font-size: 0.85rem;
}

.heart {
  color: #e74c3c;
  display: inline-block;
  margin: 0 2px;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.15); }
  100% { transform: scale(1); }
}

.github-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #24292e;
  padding: 6px 12px;
  border-radius: 6px;
  color: white !important;
  text-decoration: none;
  font-size: 0.8rem;
  font-weight: 600;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.github-btn:hover {
  background: #000;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.star-count {
  background: rgba(255, 255, 255, 0.15);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 0.75rem;
  color: #42b983;
  margin-left: 2px;
}

.badge {
  background-color: #e74c3c;
  color: white;
  border-radius: 10px;
  padding: 1px 6px;
  font-size: 0.7rem;
  font-weight: bold;
  margin-left: 4px;
}

.nav-icon {
  font-size: 1.1rem !important;
  vertical-align: middle;
  margin-right: 4px;
}
</style>
