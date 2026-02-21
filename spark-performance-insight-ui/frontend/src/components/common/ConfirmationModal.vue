<template>
  <div v-if="isOpen" class="modal-overlay">
    <div class="modal-card">
      <div class="modal-header" :class="type">
        <span class="material-symbols-outlined">{{ icon }}</span>
        <h3>{{ title }}</h3>
      </div>
      <div class="modal-body">
        <p>{{ message }}</p>
      </div>
      <div class="modal-footer">
        <button class="btn cancel" @click="cancel">Cancel</button>
        <button class="btn confirm" :class="type" @click="confirm">{{ confirmText }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  isOpen: Boolean,
  title: String,
  message: String,
  confirmText: { type: String, default: 'Confirm' },
  type: { type: String, default: 'info' } // info, warning, danger
});

const emit = defineEmits(['confirm', 'cancel']);

const icon = computed(() => {
  if (props.type === 'danger') return 'error';
  if (props.type === 'warning') return 'warning';
  return 'info';
});

const confirm = () => emit('confirm');
const cancel = () => emit('cancel');
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
}

.modal-card {
  background: white;
  border-radius: 8px;
  width: 700px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-20px); }
  to { opacity: 1; transform: translateY(0); }
}

.modal-header {
  padding: 1rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 10px;
  color: white;
}

.modal-header.info { background-color: #3498db; }
.modal-header.warning { background-color: #f39c12; }
.modal-header.danger { background-color: #e74c3c; }

.modal-header h3 {
  margin: 0;
  font-size: 1.1rem;
}

.modal-body {
  padding: 2rem 1.5rem;
  text-align: center;
  color: #333;
  line-height: 1.6;
  white-space: pre-line;
}

.modal-footer {
  padding: 1rem 1.5rem;
  background: #f8f9fa;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 500;
  border: 1px solid #ddd;
  background: white;
  transition: all 0.2s;
}

.btn.cancel:hover { background: #f1f1f1; }

.btn.confirm { color: white; border: none; }
.btn.confirm.info { background: #3498db; }
.btn.confirm.info:hover { background: #2980b9; }

.btn.confirm.warning { background: #f39c12; }
.btn.confirm.warning:hover { background: #d35400; }

.btn.confirm.danger { background: #e74c3c; }
.btn.confirm.danger:hover { background: #c0392b; }
</style>
