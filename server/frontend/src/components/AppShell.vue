<template>
  <!-- 统一的页面骨架：顶栏 + 可选侧栏 + 内容区 -->
  <div class="app-shell">
    <AppHeader />

    <div class="app-body" :class="{ 'has-sidebar': $slots.sidebar }">
      <aside v-if="$slots.sidebar" class="app-sidebar">
        <slot name="sidebar" />
      </aside>

      <main class="app-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
// 页面外壳：统一最大宽度、内边距与侧栏位置，Monitor / Profile 共用
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--bg);
}

.app-body {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: var(--space-5) var(--space-5) var(--space-7);
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-5);
  align-items: start;
}

.app-body.has-sidebar {
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
}

.app-sidebar {
  position: sticky;
  top: calc(var(--header-height) + var(--space-5));
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.app-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  min-width: 0;
}

@media (max-width: 1024px) {
  .app-body.has-sidebar {
    grid-template-columns: 1fr;
  }

  .app-sidebar {
    position: static;
  }
}

@media (max-width: 640px) {
  .app-body {
    padding: var(--space-4) var(--space-4) var(--space-6);
    gap: var(--space-4);
  }
}
</style>