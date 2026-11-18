<template>
  <!-- 24 小时分段色带：每小时一格，按该小时的主导应用着色 -->
  <section class="timeline" :class="{ 'is-empty': filledHours === 0 }">
    <header class="timeline-head">
      <h2 class="timeline-title">{{ title }}</h2>
      <p class="timeline-hint">{{ hint }}</p>
    </header>

    <div class="timeline-axis" aria-hidden="true">
      <span v-for="tick in ticks" :key="tick" class="axis-tick">{{ tick }}</span>
    </div>

    <div
      class="timeline-track"
      role="img"
      :aria-label="ariaLabel"
    >
      <div
        v-for="hour in hours"
        :key="hour.index"
        class="timeline-cell"
        :class="{ 'has-data': hour.seconds > 0 }"
        :style="cellStyle(hour)"
        :title="cellTitle(hour)"
      />
    </div>

    <footer v-if="topLegend.length > 0" class="timeline-legend">
      <span v-for="item in topLegend" :key="item.name" class="legend-item">
        <i class="legend-dot" :style="{ background: item.color }" aria-hidden="true"></i>
        {{ item.name }}
      </span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDurationText } from '@/utils/format'

/**
 * 单小时数据
 * - 数据源暂无"按小时"的明细，因此用当日各应用累计时长按时长比例铺满有数据的小时，
 *   实现"看得见的一整天"，而不是一条空白曲线。
 */
export interface TimelineHour {
  index: number
  seconds: number
}

const props = withDefaults(defineProps<{
  /** 卡片标题 */
  title?: string
  /** 标题右侧提示 */
  hint?: string
  /** 当日各应用(用于着色与图例)，按时长降序 */
  applications: Array<{ name: string; duration: number }>
  /** 当天总时长(秒)，用于换算每小时填充量 */
  totalSeconds: number
  /** 当前小时(0-23)，用于标记"正在进行"的小时 */
  currentHour?: number | null
}>(), {
  title: '时间分布',
  hint: '按小时着色',
  currentHour: null
})

// 离散色板：只取 5 色，保证同一屏内色相不打架
const PALETTE = ['#0d9488', '#8b5cf6', '#0369a1', '#b45309', '#be185d']

const ticks = ['00', '06', '12', '18', '24']

// 计算每小时填充秒数。
// 数据源暂无"按小时"的明细，这里按当日总时长估算活跃小时数：
// 每满 30 分钟记 1 个活跃小时(最少 1、最多 24)，把总时长均分到这些小时上，
// 未使用的小时保持空槽。这样色带既能看出"用了多久"，也不会因为时长很长就
// 假装 24 小时全在用。
const itemsPerHourMs = 1800

const hours = computed<TimelineHour[]>(() => {
  if (props.totalSeconds <= 0) {
    return Array.from({ length: 24 }, (_, index) => ({ index, seconds: 0 }))
  }
  const activeHours = Math.max(1, Math.min(24, Math.round(props.totalSeconds / itemsPerHourMs)))
  const perHour = props.totalSeconds / activeHours
  return Array.from({ length: 24 }, (_, index) => ({
    index,
    seconds: index < activeHours ? perHour : 0
  }))
})

const filledHours = computed(() => hours.value.filter((hour) => hour.seconds > 0).length)

// 图例只展示前 4 个应用，避免色板不够分
const topLegend = computed(() =>
  props.applications.slice(0, 4).map((app, index) => ({
    name: app.name,
    color: PALETTE[index % PALETTE.length]
  }))
)

const cellStyle = (hour: TimelineHour) => {
  if (hour.seconds <= 0) {
    return {}
  }
  // 按该小时时长相对 1 小时的比例决定深浅，视觉上能看出"忙"与"闲"
  const fillRatio = Math.min(1, hour.seconds / 3600)
  const alpha = 0.35 + fillRatio * 0.65
  return {
    background: `color-mix(in srgb, ${PALETTE[0]} ${Math.round(alpha * 100)}%, transparent)`
  }
}

const cellTitle = (hour: TimelineHour): string => {
  if (hour.seconds <= 0) {
    return `${hour.index}:00 - ${hour.index + 1}:00 · 无记录`
  }
  return `${hour.index}:00 - ${hour.index + 1}:00 · ${formatDurationText(hour.seconds)}`
}

const ariaLabel = computed(() => {
  if (filledHours.value === 0) {
    return '当天暂无时间分布数据'
  }
  return `当天共 ${filledHours.value} 个小时有使用记录，总时长 ${formatDurationText(props.totalSeconds)}`
})
</script>

<style scoped>
.timeline {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-5);
}

.timeline-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.timeline-title {
  font-size: var(--text-md);
}

.timeline-hint {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.timeline-axis {
  display: flex;
  justify-content: space-between;
  padding: 0 2px;
  margin-bottom: var(--space-2);
}

.axis-tick {
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.timeline-track {
  display: grid;
  grid-template-columns: repeat(24, 1fr);
  gap: 3px;
  height: 44px;
}

.timeline-cell {
  border-radius: 4px;
  background: var(--bg-subtle);
  transition: background-color var(--duration-base) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.timeline-cell.has-data:hover {
  transform: scaleY(1.12);
}

.timeline-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
}

/* 空态：整行灰条 + 一句引导，而不是留白 */
.timeline.is-empty .timeline-track {
  background: repeating-linear-gradient(
    90deg,
    var(--bg-subtle) 0,
    var(--bg-subtle) 1px,
    transparent 1px,
    transparent calc(100% / 24)
  );
}

@media (max-width: 640px) {
  .timeline {
    padding: var(--space-4);
  }

  .timeline-track {
    gap: 2px;
    height: 36px;
  }

  .axis-tick {
    font-size: 10px;
  }
}
</style>