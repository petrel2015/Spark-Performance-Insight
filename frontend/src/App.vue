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
    <router-view></router-view>
  </div>
</template>

<script setup>
import { compareStore } from './store/compareStore';
import { SYSTEM_VERSION } from './constants/config';
</script>

<style scoped>
.container {
  min-height: 100vh;
}

.navbar {
  background: #2c3e50;
  color: white;
  padding: 0.5rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 40px;
}

.brand {
  margin: 0;
  font-size: 1rem;
  font-weight: 500;
  opacity: 0.9;
}

.brand-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.system-version {
  font-size: 0.75rem;
  background: rgba(255, 255, 255, 0.15);
  padding: 1px 6px;
  border-radius: 4px;
  color: #bdc3c7;
  font-family: monospace;
}

.compare-group {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding-left: 1rem;
  border-left: 1px solid rgba(255,255,255,0.1);
}

.compare-toggle-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8rem;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #576574;
  transition: .4s;
}

.slider:before {
  position: absolute;
  content: "";
  height: 14px;
  width: 14px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .4s;
}

input:checked + .slider {
  background-color: #42b983;
}

input:checked + .slider:before {
  transform: translateX(16px);
}

.slider.round {
  border-radius: 20px;
}

.slider.round:before {
  border-radius: 50%;
}

.toggle-label {
  color: #bdc3c7;
  transition: color 0.2s;
  user-select: none;
}

.toggle-label.active {
  color: #42b983;
  font-weight: 600;
}

.links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.links a {
  color: white;
  text-decoration: none;
  font-size: 0.85rem;
  opacity: 0.8;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 4px;
  transition: all 0.2s;
  position: relative;
}

.links a:hover {
  opacity: 1;
}

.links a.router-link-active {
  border-bottom: 2px solid #42b983;
  opacity: 1;
}

.nav-icon {
  font-size: 1.2rem !important;
}

.badge {
  background-color: #e74c3c;
  color: white;
  border-radius: 10px;
  padding: 1px 6px;
  font-size: 0.7rem;
  font-weight: bold;
  margin-left: 2px;
}
</style>
