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
          <span class="muted-text">© 2026 Spark Performance Insight</span>
          <span class="divider">|</span>
          <a href="mailto:petrel2015@foxmail.com" class="footer-link">Contact</a>
        </div>
        <div class="footer-right">
          <a href="https://github.com/hongyusu/Spark-Performance-Insight" target="_blank" class="badge-link">
            <img src="https://img.shields.io/github/stars/hongyusu/Spark-Performance-Insight?style=flat-square&logo=github&label=Stars&color=2c3e50" alt="GitHub Stars">
          </a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { compareStore } from './store/compareStore';
import { SYSTEM_VERSION } from './constants/config';
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
  background: #f8f9fa;
  border-top: 1px solid #ebedf0;
  padding: 0.6rem 2rem;
  flex-shrink: 0;
}

.footer-content {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted-text {
  color: #95a5a6;
  font-size: 0.8rem;
}

.divider {
  color: #dcdde1;
  margin: 0 10px;
  font-size: 0.8rem;
}

.footer-link {
  color: #7f8c8d;
  text-decoration: none;
  font-size: 0.8rem;
  transition: color 0.2s;
}

.footer-link:hover {
  color: #34495e;
  text-decoration: underline;
}

.badge-link {
  display: flex;
  align-items: center;
  opacity: 0.7;
  transition: opacity 0.2s;
}

.badge-link:hover {
  opacity: 1;
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
