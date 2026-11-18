<template>
  <!-- 统计小卡片：键盘 / 鼠标点击 / 移动距离，三宫格复用 -->
  <div class="stat-cell">
    <span class="stat-icon" :style="{ color: tone.color, background: tone.soft }" aria-hidden="true">
      <el-icon :size="16"><component :is="icon" /></el-icon>
    </span>
    <div class="stat-body">
      <span class="stat-label">{{ label }}</span>
      <span v-if="loading" class="stat-skeleton" aria-hidden="true"></span>
      <span v-else class="stat-value tabular">
        {{ value }}<span v-if="unit" class="stat-unit">{{ unit }}</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EditPen, Mouse as MouseIcon, Position } from '@element-plus/icons-vue'
import { formatNumber } from '@/utils/format'

type StatType = 'keyboard' | 'mouse-click' | 'mouse-distance'

const props = withDefaults(defineProps<{
  type: StatType
  /** 原始数值(次数或米) */
  value: number
  /** 已格式化好的展示文本 */
  displayValue: string
  /** 单位(次 / km 等)，可为空 */
  unit?: string
  /** 标签 */
  label: string
  /** 是否加载中 */
  loading?: boolean
}>(), {
  unit: '',
  loading: false
})

const ICONS: Record<StatType, unknown> = {
  keyboard: EditPen,
  'mouse-click': MouseIcon,
  'mouse-distance': Position
}

const TONES: Record<StatType, { color: string; soft: string }> = {
  keyboard: { color: 'var(--primary)', soft: 'var(--primary-soft)' },
  'mouse-click': { color: 'var(--secondary)', soft: 'var(--secondary-soft)' },
  'mouse-distance': { color: 'var(--info)', soft: 'var(--info-soft)' }
}

const icon = computed(() => ICONS[props.type])
const tone = computed(() => TONES[props.type])

// 便于测试与外部复用的格式化出口
const formatted = computed(() => {
  if (props.type === 'mouse-distance') {
    return props.displayValue
  }
  return formatNumber(props.value)
})
</script>

<style scoped>
.stat-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  transition: border-color var(--duration-fast) var(--ease-out);
}

.stat-cell:hover {
  border-color: var(--border-strong);
}

.stat-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.stat-value {
  font-size: var(--text-lg);
  font-weight: 650;
  color: var(--text);
  line-height: 1.25;
}

.stat-unit {
  margin-left: 2px;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--text-muted);
}

.stat-skeleton {
  width: 64px;
  height: 22px;
  border-radius: 6px;
  background: var(--surface-2);
}
</style>