<template>
  <el-card class="app-duration-ranking" shadow="never">
    <template #header>
      <div class="ranking-card-header">
        <h3 class="ranking-card-title">{{ title }}</h3>
        <el-select
          v-model="sortMode"
          class="sort-select"
          size="small"
          aria-label="排序方式"
        >
          <el-option
            v-for="option in SORT_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </template>

    <!-- 无数据 -->
    <el-empty v-if="totalDuration <= 0" description="暂无数据" :image-size="90" />

    <template v-else>
      <!-- 总时长摘要 -->
      <div class="summary-block">
        <div class="summary-total">{{ formatDurationText(totalDuration) }}</div>
        <div
          v-if="trendInfo"
          class="summary-trend"
          :class="trendInfo.type"
        >
          {{ trendInfo.text }}
          <el-icon v-if="trendInfo.type !== 'same'" class="trend-icon">
            <component :is="trendInfo.type === 'more' ? Top : Bottom" />
          </el-icon>
        </div>
      </div>

      <el-divider class="summary-divider" />

      <!-- 运行状态图例(仅实时视图) -->
      <div v-if="showLegend" class="state-legend">
        <span class="legend-item">
          <i class="legend-dot dot-active"></i>顶端窗口
        </span>
        <span class="legend-item">
          <i class="legend-dot dot-running"></i>后台运行
        </span>
        <span class="legend-item">
          <i class="legend-dot dot-closed"></i>已关闭
        </span>
      </div>

      <!-- 列表列头 -->
      <div class="list-cols">
        <span class="col-name">应用</span>
        <span class="col-duration">使用时长</span>
      </div>

      <!-- 应用排行列表 -->
      <transition-group name="rank" tag="div" class="app-list">
        <div v-for="item in sortedItems" :key="item.name" class="app-item">
          <div class="app-item-main">
            <span
              class="app-name"
              :class="statusClassOf(item)"
              :title="item.name"
            >
              <el-icon class="app-play"><CaretRight /></el-icon>
              <span class="app-name-text">{{ item.name }}</span>
            </span>
            <span class="app-duration">{{ formatDurationText(item.duration) }}</span>
          </div>
          <div class="app-item-bar">
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidthOf(item) }" />
            </div>
            <span class="bar-percent">{{ percentOf(item) }}</span>
          </div>
        </div>
      </transition-group>

      <!-- 底部统计 -->
      <div class="ranking-footer">
        <div class="footer-row">
          <span class="footer-label">共使用</span>
          <span class="footer-value">{{ itemCount }} 个应用</span>
        </div>
        <div class="footer-row">
          <span class="footer-label">最常使用</span>
          <span class="footer-value">{{ mostUsedName || '—' }}</span>
        </div>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CaretRight, Top, Bottom } from '@element-plus/icons-vue'
import { formatDurationText } from '@/utils/format'

interface RankingItem {
  name: string
  duration: number
  // 是否为桌面最顶端的窗口(实时数据提供)
  isActive?: boolean | null
  // 是否仍在运行(实时数据提供; 历史/旧数据可能缺失)
  isRunning?: boolean | null
}

type SortMode = 'name-asc' | 'name-desc' | 'time-asc' | 'time-desc'

const SORT_OPTIONS: Array<{ value: SortMode; label: string }> = [
  { value: 'name-asc', label: '名称升序' },
  { value: 'name-desc', label: '名称降序' },
  { value: 'time-asc', label: '使用时长升序' },
  { value: 'time-desc', label: '使用时长降序' }
]

const props = withDefaults(defineProps<{
  // 卡片标题(如: 今日应用使用时长 / 7月3日 应用使用时长)
  title: string
  // 应用使用时长列表(时长单位: 秒, 未排序)
  items: RankingItem[]
  // 前一天总使用时长(秒); 为 null 时不显示"比昨天"对比行
  previousDuration?: number | null
  // 是否展示"顶端窗口/后台运行/已关闭"运行状态配色与图例(仅实时视图有意义)
  showStatus?: boolean
}>(), {
  previousDuration: null,
  showStatus: false
})

// 排序方式(默认: 按使用时长降序)
const sortMode = ref<SortMode>('time-desc')

// 仅保留有时长的应用
const positiveItems = computed<RankingItem[]>(() =>
  props.items.filter(item => item.duration > 0)
)

// 今日总使用时长
const totalDuration = computed<number>(() =>
  positiveItems.value.reduce((sum, item) => sum + item.duration, 0)
)

// 最常使用(应用时长最大者)
const mostUsedName = computed<string>(() => {
  let top: RankingItem | undefined
  for (const item of positiveItems.value) {
    if (!top || item.duration > top.duration) {
      top = item
    }
  }
  return top?.name || ''
})

// 应用数量
const itemCount = computed<number>(() => positiveItems.value.length)

// 进度条基准: 以最长使用应用为满格,条长只反映应用间的相对时长
const maxDuration = computed<number>(() => {
  let max = 0
  for (const item of positiveItems.value) {
    if (item.duration > max) {
      max = item.duration
    }
  }
  return max
})

// 排序后的应用列表
const sortedItems = computed<RankingItem[]>(() => {
  const list = [...positiveItems.value]
  const collator = new Intl.Collator('zh-Hans-CN', { numeric: true })
  switch (sortMode.value) {
    case 'name-asc':
      list.sort((a, b) => collator.compare(a.name, b.name))
      break
    case 'name-desc':
      list.sort((a, b) => collator.compare(b.name, a.name))
      break
    case 'time-asc':
      list.sort((a, b) => a.duration - b.duration || collator.compare(a.name, b.name))
      break
    case 'time-desc':
      list.sort((a, b) => b.duration - a.duration || collator.compare(a.name, b.name))
      break
  }
  return list
})

// 运行状态对应的应用名称样式类(仅实时视图开启状态配色时生效)
const statusClassOf = (item: RankingItem): string => {
  if (!props.showStatus) {
    return ''
  }
  if (item.isRunning === true) {
    // 仍在运行: 是桌面最顶端窗口时为绿色(特例), 否则为后台运行的蓝色
    return item.isActive === true ? 'status-active' : 'status-running'
  }
  // isRunning为false或缺失(历史/旧数据)时一律按"已关闭"灰色处理
  return 'status-closed'
}

// 运行状态图例: 仅实时视图且列表非空时展示
const showLegend = computed<boolean>(() =>
  props.showStatus && sortedItems.value.length > 0
)

// 与前一天对比信息(less: 比昨天少, more: 比昨天多, same: 持平)
const trendInfo = computed<{ type: 'less' | 'more' | 'same'; text: string } | null>(() => {
  if (props.previousDuration === null || props.previousDuration === undefined) {
    return null
  }
  const diff = totalDuration.value - props.previousDuration
  if (diff > 0) {
    return { type: 'more', text: `比昨天多 ${formatDurationText(diff)}` }
  }
  if (diff < 0) {
    return { type: 'less', text: `比昨天少 ${formatDurationText(-diff)}` }
  }
  return { type: 'same', text: '与昨天持平' }
})

// 单个应用占总时长的百分比(如 26.9%)
const percentOf = (item: RankingItem): string => {
  if (totalDuration.value <= 0) {
    return '0%'
  }
  const fixed = ((item.duration / totalDuration.value) * 100).toFixed(1)
  return fixed.replace(/\.0$/, '') + '%'
}

// 进度条宽度(相对最长应用)
const barWidthOf = (item: RankingItem): string => {
  if (maxDuration.value <= 0) {
    return '0%'
  }
  return `${Math.min(100, (item.duration / maxDuration.value) * 100)}%`
}
</script>

<style scoped>
.app-duration-ranking {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.ranking-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ranking-card-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.sort-select {
  width: 140px;
}

/* 运行状态图例 */
.state-legend {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  padding: 0 4px 10px;
  font-size: 12px;
  color: #909399;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot-active {
  background: #67c23a;
}

.dot-running {
  background: #409eff;
}

.dot-closed {
  background: #c0c4cc;
}

/* 摘要区 */
.summary-block {
  text-align: center;
  padding: 4px 0 8px;
}

.summary-total {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  line-height: 1.3;
}

.summary-trend {
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.summary-trend .trend-icon {
  font-size: 13px;
}

.summary-trend.more {
  color: #e6a23c;
}

.summary-trend.less {
  color: #67c23a;
}

.summary-trend.same {
  color: #909399;
}

.summary-divider {
  margin: 10px 0 4px;
}

/* 列头 */
.list-cols {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  font-size: 13px;
  color: #909399;
}

/* 应用列表 */
.app-list {
  display: flex;
  flex-direction: column;
}

.app-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f0f2f5;
}

.app-item:last-child {
  border-bottom: none;
}

.app-item-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.app-name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-size: 15px;
  color: #303133;
}

.app-play {
  color: #409eff;
  font-size: 14px;
  flex-shrink: 0;
}

.app-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 运行状态配色: 顶端窗口=绿加粗, 后台运行=蓝, 已关闭=灰 */
.app-name.status-active {
  color: #67c23a;
  font-weight: 600;
}

.app-name.status-running {
  color: #409eff;
}

.app-name.status-closed {
  color: #c0c4cc;
}

/* 开启状态配色后 ▶ 图标跟随应用名称颜色 */
.app-name.status-active .app-play,
.app-name.status-running .app-play,
.app-name.status-closed .app-play {
  color: inherit;
}

.app-duration {
  flex-shrink: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}

.app-item-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #ebeef5;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #409eff, #79bbff);
  transition: width 0.4s ease;
}

.bar-percent {
  width: 46px;
  flex-shrink: 0;
  text-align: right;
  font-size: 12px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}

/* 排序变化时的位移过渡 */
.rank-move {
  transition: transform 0.3s ease;
}

/* 底部统计 */
.ranking-footer {
  margin-top: 12px;
  padding: 12px 4px 2px;
  border-top: 1px dashed #dcdfe6;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.footer-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-label {
  color: #909399;
}

.footer-value {
  color: #303133;
  font-weight: 600;
}
</style>
