<template>
  <!-- 主数字：整页最重要的一个数字(今日总时长)，字号最大、独占一行 -->
  <section class="hero-metric">
    <header class="hero-head">
      <h1 class="hero-date">{{ dateLabel }}</h1>
      <span class="hero-tag" :class="statusClass">
        <i class="tag-dot" aria-hidden="true"></i>
        {{ statusText }}
      </span>
    </header>

    <p v-if="loading" class="hero-value is-skeleton" aria-hidden="true">
      <span class="skeleton-box skeleton-value"></span>
    </p>
    <p v-else class="hero-value" :aria-label="ariaValue">
      <template v-for="(segment, index) in segments" :key="`${segment.unit}-${index}`">
        <span class="hero-number tabular">{{ segment.value }}</span>
        <span class="hero-unit">{{ segment.unit }}</span>
      </template>
    </p>

    <p v-if="!loading && trendText" class="hero-trend" :class="trendType">
      {{ trendText }}
    </p>

    <!-- 目标进度(可选)：不设置目标时不渲染，避免无意义进度条 -->
    <div v-if="goalSeconds && goalSeconds > 0" class="hero-goal">
      <div class="goal-track" role="progressbar" :aria-valuenow="goalPercent" aria-valuemin="0" aria-valuemax="100">
        <div class="goal-fill" :style="{ width: `${goalPercent}%` }" />
      </div>
      <div class="goal-labels">
        <span>已用 {{ goalPercent }}%</span>
        <span>目标 {{ goalLabel }}</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDurationText, splitDurationText } from '@/utils/format'

// 首屏主指标卡：只放"今天用了多久"这一件事
const props = withDefaults(defineProps<{
  /** 日期标题(如 11月18日 周二) */
  dateLabel: string
  /** 总时长(秒) */
  totalSeconds: number
  /** 昨日总时长(秒)，为 null 时不显示对比 */
  previousSeconds?: number | null
  /** 目标时长(秒)，为 0/null 时不显示进度条 */
  goalSeconds?: number | null
  /** 是否加载中 */
  loading?: boolean
  /** 状态文案(如 实时同步中 / 历史数据) */
  statusText: string
  /** 状态类型 */
  statusType?: 'live' | 'history' | 'offline'
}>(), {
  previousSeconds: null,
  goalSeconds: null,
  loading: false,
  statusType: 'live'
})

const segments = computed(() => splitDurationText(props.totalSeconds))

const ariaValue = computed(() => `总时长 ${formatDurationText(props.totalSeconds)}`)

const statusClass = computed(() => `is-${props.statusType}`)

const goalPercent = computed(() => {
  if (!props.goalSeconds || props.goalSeconds <= 0) return 0
  return Math.min(100, Math.round((props.totalSeconds / props.goalSeconds) * 100))
})

const goalLabel = computed(() => formatDurationText(props.goalSeconds || 0))

const trendType = computed(() => {
  if (props.previousSeconds === null || props.previousSeconds === undefined) return 'neutral'
  const diff = props.totalSeconds - props.previousSeconds
  if (diff > 0) return 'up'
  if (diff < 0) return 'down'
  return 'neutral'
})

const trendText = computed(() => {
  if (props.previousSeconds === null || props.previousSeconds === undefined) {
    return ''
  }
  const diff = props.totalSeconds - props.previousSeconds
  if (diff === 0) {
    return '与昨天持平'
  }
  const abs = formatDurationText(Math.abs(diff))
  return diff > 0 ? `比昨天多 ${abs}` : `比昨天少 ${abs}`
})
</script>

<style scoped>
.hero-metric {
  padding: var(--space-5) 0 0;
}

.hero-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.hero-date {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
  font-weight: 500;
}

.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: currentColor;
}

/* 实时：主色脉冲；历史：中性；离线：警示色 */
.hero-tag.is-live {
  color: var(--primary);
  background: var(--primary-soft);
}

.hero-tag.is-live .tag-dot {
  animation: live-pulse 2s var(--ease-out) infinite;
}

.hero-tag.is-history {
  color: var(--text-muted);
  background: var(--surface-2);
}

.hero-tag.is-offline {
  color: var(--warning);
  background: var(--warning-soft);
}

@keyframes live-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(0.8); }
}

.hero-value {
  margin-top: var(--space-3);
  font-size: var(--text-hero);
  font-weight: 650;
  line-height: 1.05;
  letter-spacing: -0.03em;
  color: var(--text);
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}

.hero-number {
  font-size: inherit;
}

.hero-unit {
  font-size: 0.36em;
  font-weight: 500;
  color: var(--text-muted);
  margin-right: var(--space-2);
}

.hero-trend {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

.hero-trend.up {
  color: var(--warning);
}

.hero-trend.down {
  color: var(--secondary);
}

.hero-trend.neutral {
  color: var(--text-muted);
}

.hero-goal {
  margin-top: var(--space-5);
}

.goal-track {
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  overflow: hidden;
}

.goal-fill {
  height: 100%;
  border-radius: var(--radius-pill);
  background: var(--primary);
  transition: width var(--duration-base) var(--ease-out);
}

.goal-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

/* 加载骨架：三块灰条，保持高度不跳动 */
.is-skeleton {
  display: block;
}

.skeleton-box {
  display: block;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--surface-2) 25%, var(--bg-subtle) 37%, var(--surface-2) 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

.skeleton-value {
  width: 320px;
  max-width: 70%;
  height: 52px;
}

@keyframes skeleton-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

@media (max-width: 640px) {
  .hero-value {
    font-size: 40px;
  }

  .skeleton-value {
    height: 38px;
  }
}
</style>