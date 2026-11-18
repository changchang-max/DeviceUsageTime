<template>
  <!-- 品牌标识：一个"日晷/刻度"符号，表达"时间被记录" -->
  <span class="brand" :class="{ compact }">
    <svg class="brand-mark" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.2 2" />
    </svg>
    <span v-if="!compact" class="brand-name">设备使用时间</span>
  </span>
</template>

<script setup lang="ts">
// 品牌标识组件：登录页、页头、侧栏复用同一套字形与尺寸
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text);
}

.brand-mark {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: var(--primary);
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  flex-shrink: 0;
}

.brand-name {
  font-size: var(--text-md);
  font-weight: 650;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.compact .brand-mark {
  width: 26px;
  height: 26px;
}
</style>