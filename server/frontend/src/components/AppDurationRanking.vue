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

    <!-- 无数据: 没有任何有时长的应用(此时即使有0时长的新活跃应用也不展示, 等其产生时长后自然进入列表) -->
    <el-empty v-if="itemsTotalDuration <= 0" description="暂无数据" :image-size="90" />

    <template v-else>
      <!-- 总时长摘要(展示客户端程序运行时长, 而非应用时长总和) -->
      <div class="summary-block">
        <div class="summary-total">{{ formatDurationText(displayTotalDuration) }}</div>
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
        <div
          v-for="item in sortedItems"
          :key="item.name"
          class="app-item"
          :class="{ 'is-active': isItemPinned(item) }"
        >
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
  // 今日总时长(秒): 客户端程序今日运行时长(≈设备总使用时长), 由统计数据totalDuration提供。
  // 缺失或为0(旧客户端/旧历史数据未采集该指标)时, 组件回退为应用时长总和
  totalDuration?: number | null
  // 前一天总时长(秒)(与totalDuration同口径: 前一日客户端运行时长); 为 null 时不显示"比昨天"对比行
  previousDuration?: number | null
  // 是否展示"顶端窗口/后台运行/已关闭"运行状态配色与图例(仅实时视图有意义)
  showStatus?: boolean
}>(), {
  totalDuration: null,
  previousDuration: null,
  showStatus: false
})

// 排序方式(默认: 按使用时长降序)
const sortMode = ref<SortMode>('time-desc')

// 仅保留有时长的应用
const positiveItems = computed<RankingItem[]>(() =>
  props.items.filter(item => item.duration > 0)
)

// 各应用时长总和(秒): 仅用于"单个应用占比"等相对计算,
// 不再作为卡片顶部"总时长"展示(多应用并发运行时会成倍虚高)
const itemsTotalDuration = computed<number>(() =>
  positiveItems.value.reduce((sum, item) => sum + item.duration, 0)
)

// 卡片顶部展示的总时长: 优先使用客户端程序运行时长(统计数据totalDuration),
// 旧数据缺失或为0时回退为应用时长总和, 避免历史页面显示异常
const displayTotalDuration = computed<number>(() =>
  props.totalDuration != null && props.totalDuration > 0
    ? props.totalDuration
    : itemsTotalDuration.value
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

// 是否为"当前正在使用"的应用(桌面最顶端窗口), 实时视图下才存在该状态
const isItemPinned = (item: RankingItem): boolean =>
  props.showStatus && item.isActive === true

// 排序后的应用列表。
// "当前正在使用"的应用(isActive=true)始终置顶, 其余应用仍按所选排序方式排列。
// 新切换的应用即使时长尚为0也立即置顶展示, 保证切换瞬间在列表中最先可见。
const sortedItems = computed<RankingItem[]>(() => {
  const rest = positiveItems.value.filter(item => !isItemPinned(item))
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

  const active = props.items.find(item => isItemPinned(item))
  if (active) {
    rest.unshift(active)
  }
  return rest
})

// 应用数量(以实际展示的行数为准: 含置顶的"当前正在使用"应用)
const itemCount = computed<number>(() => sortedItems.value.length)

// 运行状态对应的应用名称样式类(仅实时视图开启状态配色时生效)
const statusClassOf = (item: RankingItem): string => {
  if (!props.showStatus) {
    return ''
  }
  if (item.isActive === true) {
    // 桌面最顶端窗口(当前正在使用): 绿色加粗(最优先)
    return 'status-active'
  }
  if (item.isRunning === true) {
    // 仍在运行(非最顶端): 后台运行的蓝色
    return 'status-running'
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
  const diff = displayTotalDuration.value - props.previousDuration
  if (diff > 0) {
    return { type: 'more', text: `比昨天多 ${formatDurationText(diff)}` }
  }
  if (diff < 0) {
    return { type: 'less', text: `比昨天少 ${formatDurationText(-diff)}` }
  }
  return { type: 'same', text: '与昨天持平' }
})

// 单个应用占全部应用时长的百分比(如 26.9%)
const percentOf = (item: RankingItem): string => {
  if (itemsTotalDuration.value <= 0) {
    return '0%'
  }
  const fixed = ((item.duration / itemsTotalDuration.value) * 100).toFixed(1)
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
  position: relative;
}

.app-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f0f2f5;
  /* 置顶/取消置顶时, 高亮底色与边框光效平滑过渡(布局位移交由FLIP动画处理) */
  transition:
    background-color 0.45s ease,
    border-color 0.45s ease,
    box-shadow 0.45s ease;
}

.app-item:last-child {
  border-bottom: none;
}

/* "当前正在使用"的应用(置顶行): 绿色柔光边框 + 柔和浅绿底色, 与其他行明显区分。
   app-active-in负责置顶瞬间的"点亮"效果, app-active-glow负责常驻的呼吸光效 */
.app-item.is-active {
  margin: 8px 0;
  padding: 12px 4px;
  border: 1px solid rgba(103, 194, 58, 0.55);
  border-radius: 10px;
  background-color: rgba(103, 194, 58, 0.1);
  animation:
    app-active-in 0.6s cubic-bezier(0.22, 1, 0.36, 1),
    app-active-glow 3s ease-in-out 0.6s infinite;
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

/* 排序/置顶变化时的位移过渡(FLIP): 略带回弹缓动, 切换明显且流畅 */
.rank-move {
  transition: transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

/* 新应用进入/退出列表 */
.rank-enter-active {
  transition: opacity 0.4s ease, transform 0.4s ease;
}

.rank-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}

.rank-leave-active {
  position: absolute;
  left: 0;
  right: 0;
  transition: opacity 0.25s ease;
}

.rank-leave-to {
  opacity: 0;
}

/* 置顶行点亮动画: 短暂增强的光晕后回落到常态, 让"正在使用的应用"切换一目了然 */
@keyframes app-active-in {
  0% {
    background-color: rgba(103, 194, 58, 0.03);
    border-color: rgba(103, 194, 58, 0.2);
    box-shadow: 0 0 0 rgba(103, 194, 58, 0);
  }
  60% {
    background-color: rgba(103, 194, 58, 0.24);
    border-color: rgba(103, 194, 58, 1);
    box-shadow: 0 0 22px rgba(103, 194, 58, 0.65), 0 1px 6px rgba(103, 194, 58, 0.3);
  }
  100% {
    background-color: rgba(103, 194, 58, 0.1);
    border-color: rgba(103, 194, 58, 0.55);
    box-shadow: 0 0 8px rgba(103, 194, 58, 0.25);
  }
}

/* 置顶行常驻呼吸光效: 边框与柔光在高低强度间平缓变化 */
@keyframes app-active-glow {
  0%,
  100% {
    border-color: rgba(103, 194, 58, 0.55);
    box-shadow: 0 0 8px rgba(103, 194, 58, 0.25), 0 1px 4px rgba(103, 194, 58, 0.1);
  }
  50% {
    border-color: rgba(103, 194, 58, 0.95);
    box-shadow: 0 0 16px rgba(103, 194, 58, 0.5), 0 1px 6px rgba(103, 194, 58, 0.22);
  }
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
