<template>
  <div class="collapsible-card" :class="{ collapsed: isCollapsed }">
    <div class="card-header" @click="toggle">
      <div class="header-left">
        <span class="toggle-icon material-symbols-outlined">
          {{ isCollapsed ? 'chevron_right' : 'expand_more' }}
        </span>
        <slot name="title">
          <h4 class="default-title">{{ title }}</h4>
        </slot>
      </div>
      <div class="header-right" @click.stop>
        <slot name="actions"></slot>
      </div>
    </div>
    <div v-show="!isCollapsed" class="card-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import {ref} from 'vue';

const props = defineProps({
  title: String,
  initialCollapsed: {type: Boolean, default: false}
});

const isCollapsed = ref(props.initialCollapsed);

const toggle = () => {
  isCollapsed.value = !isCollapsed.value;
};
</script>

<style scoped>
.collapsible-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.card-header {
  padding: 0.75rem 1.5rem;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}

.card-header:hover {
  background: #fcfcfc;
}

.collapsible-card.collapsed .card-header {
  border-bottom: none;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toggle-icon {
  font-size: 20px !important;
  color: #999;
  width: 20px;
}

.default-title {
  margin: 0;
  font-size: 0.95rem;
  color: #2c3e50;
  font-weight: 600;
}

.card-content {
  padding: 1.5rem;
}

.header-right {
  display: flex;
  align-items: center;
}
</style>
