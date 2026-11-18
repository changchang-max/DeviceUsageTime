<template>
  <!-- 应用排行：横向条形为主表达，默认只展示前 N 条，展开看全部 -->
  <section class="ranking">
    <header class="ranking-head">
      <div class="head-left">
        <h2 class="ranking-title">{{ title }}</h2>
        <span v-if="!loading" class="ranking-count">共 {{ visibleCount }} / {{ totalCount }} 个应用</span>
      </div>

      <label class="sort-control">
        <span class="sort-label">排序</span>
        <select v-model="sortMode" class="sort-select" aria-label="排序方式">
          <option v-for="option in SORT_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </header>

    <!-- 加载态：3 条骨架，保持行高不跳动 -->
    <div v-if="loading" class="ranking-skeleton" aria-hidden="true">
      <div v-for="n in 3" :key="n" class="skeleton-row">
        <span class="skeleton-bar" :style="{ width: `${80 - n * 12}%` }"></span>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="sortedItems.length === 0" class="ranking-empty">
      <p class="empty-title">这一天还没有使用记录</p>
      <p class="empty-desc">客户端运行后 1 分钟内数据就会出现</p>
    </div>

    <!-- 列表 -->
    <ol v-else class="ranking-list">
      <li
        v-for="(item, index) in displayedItems"
        :key="item.name"
        class="ranking-item"
        :class="{ 'is-active': item.isActive === true }"
      >
        <div class="item-row">
          <span class="item-rank tabular">{{ String(index + 1).padStart(2, '0') }}</span>

          <span class="item-name" :title="item.name">
            <span class="status-dot" :class="statusClassOf(item)" aria-hidden="true"></span>
            <span class="item-name-text">{{ item.name }}</span>
            <span v-if="item.isActive === true" class="item-badge">正在使用</span>
          </span>

          <span class="item-duration tabular">{{ formatDurationText(item.duration) }}</span>
        </div>

        <div class="item-bar-row">
          <span class="item-track" aria-hidden="true">
            <span class="item-fill" :style="{ width: barWidthOf(item) }" />
          </span>
          <span class="item-percent tabular">{{ percentOf(item) }}</span>
        </div>
      </li>
    </ol>

    <footer v-if="sortedItems.length > collapsedLimit" class="ranking-footer">
      <button type="button" class="expand-button" @click="expanded = !expanded">
        {{ expanded ? '收起' : `展开全部 ${sortedItems.length} 个应用` }}
      </button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatDurationText } from '@/utils/format'

interface RankingItem {
  name: string
  duration: number
  /** 是否为桌面最顶端的窗口(实时数据提供) */
  isActive?: boolean | null
  /** 是否仍在运行(实时数据提供；历史/旧数据可能缺失) */
  isRunning?: boolean | null
}

type SortMode = 'name-asc' | 'name-desc' | 'time-asc' | 'time-desc'

const SORT_OPTIONS: Array<{ value: SortMode; label: string }> = [
  { value: 'time-desc', label: '时长 降序' },
  { value: 'time-asc', label: '时长 升序' },
  { value: 'name-asc', label: '名称 升序' },
  { value: 'name-desc', label: '名称 降序' }
]

const props = withDefaults(defineProps<{
  /** 卡片标题 */
  title: string
  /** 应用列表(时长单位: 秒，未排序) */
  items: RankingItem[]
  /** 是否加载中 */
  loading?: boolean
  /** 是否展示运行状态配色(仅实时视图有意义) */
  showStatus?: boolean
  /** 折叠前展示的条数 */
  collapsedLimit?: number
}>(), {
  loading: false,
  showStatus: true,
  collapsedLimit: 6
})

const sortMode = ref<SortMode>('time-desc')
const expanded = ref(false)

// 仅保留有时长的应用(0 时长的新应用等其产生时长后自然进入)
const positiveItems = computed(() => props.items.filter((item) => item.duration > 0))

const totalCount = computed(() => positiveItems.value.length)

const totalDuration = computed(() =>
  positiveItems.value.reduce((sum, item) => sum + item.duration, 0)
)

// 进度条以最长应用为满格，只反映应用之间的相对时长
const maxDuration = computed(() => {
  let max = 0
  for (const item of positiveItems.value) {
    if (item.duration > max) max = item.duration
  }
  return max
})

// "正在使用"的应用置顶，其余按所选排序
const sortedItems = computed<RankingItem[]>(() => {
  const pinned = props.showStatus ? positiveItems.value.filter((item) => item.isActive === true) : []
  const rest = positiveItems.value.filter((item) => !pinned.includes(item))
  const collator = new Intl.Collator('zh-Hans-CN', { numeric: true })

  switch (sortMode.value) {
    case 'name-asc':
      rest.sort((a, b) => collator.compare(a.name, b.name))
      break
    case 'name-desc':
      rest.sort((a, b) => collator.compare(b.name, a.name))
      break
    case 'time-asc':
      rest.sort((a, b) => a.duration - b.duration || collator.compare(a.name, b.name))
      break
    case 'time-desc':
      rest.sort((a, b) => b.duration - a.duration || collator.compare(a.name, b.name))
      break
  }
  return [...pinned, ...rest]
})

const visibleCount = computed(() => sortedItems.value.length)

const displayedItems = computed(() =>
  expanded.value ? sortedItems.value : sortedItems.value.slice(0, props.collapsedLimit)
)

const statusClassOf = (item: RankingItem): string => {
  if (!props.showStatus) return 'is-closed'
  if (item.isActive === true) return 'is-active'
  if (item.isRunning === true) return 'is-running'
  return 'is-closed'
}

const percentOf = (item: RankingItem): string => {
  if (totalDuration.value <= 0) return '0%'
  return `${((item.duration / totalDuration.value) * 100).toFixed(1).replace(/\.0$/, '')}%`
}

const barWidthOf = (item: RankingItem): string => {
  if (maxDuration.value <= 0) return '0%'
  return `${Math.min(100, (item.duration / maxDuration.value) * 100)}%`
}
</script>

<style scoped>
.ranking {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-5);
}

.ranking-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}

.head-left {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
}

.ranking-title {
  font-size: var(--text-md);
}

.ranking-count {
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

/* 原生 select：可键盘操作、无 JS 依赖、样式可控 */
.sort-control {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.sort-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.sort-select {
  height: 32px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
}

.sort-select:hover {
  border-color: var(--border-strong);
}

.ranking-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.ranking-item {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border);
}

.ranking-item:last-child {
  border-bottom: none;
}

.item-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: 6px;
}

.item-rank {
  width: 20px;
  flex-shrink: 0;
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.item-name {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);
  color: var(--text);
}

.item-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: var(--radius-pill);
}

.status-dot.is-active {
  background: var(--success);
}

.status-dot.is-running {
  background: var(--info);
}

.status-dot.is-closed {
  background: var(--border-strong);
}

.item-badge {
  flex-shrink: 0;
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--success-soft);
  color: var(--success);
  font-size: 11px;
  font-weight: 500;
}

.item-duration {
  flex-shrink: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text);
}

.item-bar-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-left: 32px;
}

.item-track {
  flex: 1;
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  overflow: hidden;
}

.item-fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--primary) 82%, var(--secondary));
  transition: width var(--duration-base) var(--ease-out);
}

.item-percent {
  width: 48px;
  flex-shrink: 0;
  text-align: right;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

/* 正在使用的那一行：只有它允许有强调色，避免整页花 */
.ranking-item.is-active .item-name-text {
  font-weight: 600;
}

.ranking-item.is-active .item-fill {
  background: var(--success);
}

/* 展开按钮 */
.ranking-footer {
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--border);
  display: flex;
  justify-content: center;
}

.expand-button {
  min-height: 36px;
  padding: 0 var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.expand-button:hover {
  border-color: var(--primary);
  color: var(--primary);
}

/* 空态 */
.ranking-empty {
  padding: var(--space-6) 0;
  text-align: center;
}

.empty-title {
  font-size: var(--text-base);
  color: var(--text-secondary);
  margin-bottom: var(--space-2);
}

.empty-desc {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

/* 骨架 */
.ranking-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-2) 0;
}

.skeleton-row {
  height: 28px;
  display: flex;
  align-items: center;
}

.skeleton-bar {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--surface-2) 25%, var(--bg-subtle) 37%, var(--surface-2) 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

@media (max-width: 640px) {
  .ranking {
    padding: var(--space-4);
  }

  .item-bar-row {
    padding-left: 0;
  }

  .item-rank {
    width: 16px;
  }
}
</style>