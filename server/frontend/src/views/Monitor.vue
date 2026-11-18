<template>
  <AppShell>
    <!-- 侧栏：日历 + 当日摘要，桌面端吸附在顶部 -->
    <template #sidebar>
      <Calendar
        v-model="selectedDate"
        :data-dates="dataStore.dataDatesList"
        @date-change="handleDateChange"
      />

      <section class="side-summary">
        <h2 class="side-title">今日排行</h2>
        <ol v-if="topThree.length > 0" class="side-list">
          <li v-for="(item, index) in topThree" :key="item.name" class="side-item">
            <span class="side-rank tabular">{{ index + 1 }}</span>
            <span class="side-name" :title="item.name">{{ item.name }}</span>
            <span class="side-duration tabular">{{ formatDurationText(item.duration) }}</span>
          </li>
        </ol>
        <p v-else class="side-empty">暂无数据</p>
      </section>
    </template>

    <!-- 主内容 -->
    <HeroMetric
      :date-label="dateLabel"
      :total-seconds="statistics.totalDuration ?? 0"
      :previous-seconds="isRealtime ? yesterdayTotal : null"
      :loading="dataStore.loading && !hasAnyData"
      :status-text="statusText"
      :status-type="statusType"
    >
    </HeroMetric>

    <HourTimeline
      title="一天的时间分布"
      :hint="isRealtime ? '实时更新中' : '按所选日期'"
      :applications="rankedApps"
      :total-seconds="timelineTotal"
    />

    <section class="stats-grid" aria-label="输入统计">
      <StatsCard
        type="keyboard"
        :value="statistics.keyboardCount ?? 0"
        :display-value="formatNumber(statistics.keyboardCount ?? 0)"
        unit="次"
        label="键盘敲击"
        :loading="dataStore.loading"
      />
      <StatsCard
        type="mouse-click"
        :value="statistics.mouseClickCount ?? 0"
        :display-value="formatNumber(statistics.mouseClickCount ?? 0)"
        unit="次"
        label="鼠标点击"
        :loading="dataStore.loading"
      />
      <StatsCard
        type="mouse-distance"
        :value="statistics.mouseDistance ?? 0"
        :display-value="distanceText.value"
        :unit="distanceText.unit"
        label="鼠标移动"
        :loading="dataStore.loading"
      />
    </section>

    <AppDurationRanking
      :title="rankingTitle"
      :items="rankingItems"
      :loading="dataStore.loading"
      :show-status="isRealtime"
    />

    <!-- 连接状态提示：断线时给出可操作的一句话，而不是只在角落变红点 -->
    <div v-if="connectionHint" class="connection-banner" :class="`is-${statusType}`" role="status">
      <span class="banner-dot" aria-hidden="true"></span>
      <span class="banner-text">{{ connectionHint }}</span>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import Calendar from '@/components/Calendar.vue'
import HeroMetric from '@/components/HeroMetric.vue'
import HourTimeline from '@/components/HourTimeline.vue'
import StatsCard from '@/components/StatsCard.vue'
import AppDurationRanking from '@/components/AppDurationRanking.vue'
import { useDataStore } from '@/stores/data'
import { useWebSocketStore } from '@/stores/websocket'
import { useUserStore } from '@/stores/user'
import { getHistoryDataApi } from '@/api'
import type { WebSocketMessage } from '@/types/websocket'
import {
  formatChineseDate,
  formatDurationText,
  formatNumber,
  splitDistance,
  toLocalDateKey,
  toLocalYearMonthKey
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const dataStore = useDataStore()
const wsStore = useWebSocketStore()
const userStore = useUserStore()

// 默认选中本地时区的今天(与 Calendar、后端 Asia/Shanghai 归属日期保持一致)
const selectedDate = ref(toLocalDateKey())
const isRealtime = ref(true)
const userId = ref('')
const secretKey = ref('')

const dateLabel = computed(() => formatChineseDate(selectedDate.value))

const hasAnyData = computed(() =>
  isRealtime.value ? Boolean(dataStore.realtimeData) : Boolean(dataStore.historyData)
)

// 当前视图的应用列表(实时/历史字段不同，统一成 name + duration)
const currentApps = computed(() => {
  const apps = isRealtime.value
    ? dataStore.realtimeData?.applications || []
    : dataStore.historyData?.applications || []

  return apps.map((app) => {
    const isRealtimeApp = 'isActive' in app
    return {
      name: app.name,
      duration: isRealtimeApp ? app.duration || 0 : app.totalDuration || 0,
      isActive: isRealtimeApp ? app.isActive : null,
      isRunning: isRealtimeApp ? app.isRunning === true : null
    }
  })
})

const rankedApps = computed(() =>
  [...currentApps.value].sort((a, b) => b.duration - a.duration)
)

const rankingItems = computed(() => currentApps.value)

const statistics = computed(() => {
  const data = isRealtime.value ? dataStore.realtimeData : dataStore.historyData
  return (
    data?.statistics || {
      keyboardCount: 0,
      mouseClickCount: 0,
      mouseDistance: 0,
      totalDuration: 0
    }
  )
})

// 色带使用的总时长：优先用客户端运行时长，缺失时回退为应用时长之和
const timelineTotal = computed(() => {
  const total = statistics.value.totalDuration
  if (total && total > 0) return total
  return rankedApps.value.reduce((sum, app) => sum + app.duration, 0)
})

const topThree = computed(() => rankedApps.value.filter((app) => app.duration > 0).slice(0, 3))

// 鼠标移动距离拆成"数值 + 单位"两段，便于大字排版(1240.5 m / 1.24 km)
const distanceText = computed(() => splitDistance(statistics.value.mouseDistance ?? 0))

const rankingTitle = computed(() =>
  isRealtime.value ? '今天用得最多的应用' : `${formatChineseDate(selectedDate.value)} 的应用`
)

// 连接状态：文案直接给用户看，所以区分「实时/历史/重连中」
const statusText = computed(() => {
  if (!isRealtime.value) return '历史数据'
  if (wsStore.connected) return '实时同步中'
  if (wsStore.reconnecting) return '重连中'
  return '已离线'
})

const statusType = computed<'live' | 'history' | 'offline'>(() => {
  if (!isRealtime.value) return 'history'
  return wsStore.connected ? 'live' : 'offline'
})

const connectionHint = computed(() => {
  if (!isRealtime.value || wsStore.connected) return ''
  if (wsStore.reconnecting) return '连接已断开，正在自动重连，页面数据可能不是最新的'
  return '未连接到服务器，当前显示的是最后一次拉取的数据'
})

// 昨日总时长(秒)：仅实时视图用于"比昨天"对比
const yesterdayTotal = ref<number | null>(null)

const loadYesterdayTotal = async () => {
  if (!isRealtime.value) {
    yesterdayTotal.value = null
    return
  }

  const date = new Date()
  date.setDate(date.getDate() - 1)
  const yesterdayKey = toLocalDateKey(date)

  try {
    const res = await getHistoryDataApi(yesterdayKey, secretKey.value || undefined)
    const prevTotal = res.data?.statistics?.totalDuration
    yesterdayTotal.value = prevTotal && prevTotal > 0 ? prevTotal : null
  } catch {
    // 昨日无数据或请求失败都视为无法对比，不打断页面主流程
    yesterdayTotal.value = null
  }
}

const handleDateChange = async (date: string) => {
  isRealtime.value = date === toLocalDateKey()

  if (isRealtime.value) {
    await dataStore.fetchRealtimeData(secretKey.value)
  } else {
    await dataStore.fetchHistoryData(date, secretKey.value)
  }

  await loadYesterdayTotal()
}

const handleWsMessage = (message: WebSocketMessage) => {
  if (message.type === 'realtime_update' && isRealtime.value) {
    // 合并推送的实时数据(推送仅含 applications/statistics，保留原有 userId 等信息)
    const merged = {
      ...(dataStore.realtimeData || {}),
      userId: message.userId,
      timestamp: message.timestamp,
      applications: message.data?.applications || [],
      statistics: message.data?.statistics
    } as any
    dataStore.updateRealtimeData(merged)
  }
}

onMounted(async () => {
  const authToken = userStore.token || undefined

  // 已登录但尚未获取用户信息时先拉取(用于获取邮箱作为订阅标识)
  if (authToken && !userStore.userInfo) {
    await userStore.fetchUserInfo()
  }

  userId.value = (route.params.userId as string) || userStore.userInfo?.email || ''
  secretKey.value = (route.query.key as string) || ''
  const authKey = secretKey.value || undefined

  // 监控页必须有访问凭证(Token 或秘钥)，否则跳转秘钥入口页
  if (!authToken && !authKey) {
    router.replace('/login')
    return
  }

  await Promise.all([
    dataStore.fetchDataDates(toLocalYearMonthKey(), authKey),
    dataStore.fetchRealtimeData(authKey)
  ])
  await loadYesterdayTotal()

  if (authKey || authToken) {
    wsStore.connect(authKey ? '' : userId.value, authToken, authKey)
    wsStore.setMessageHandler(handleWsMessage)
  }
})

onUnmounted(() => {
  wsStore.setMessageHandler(null)
  wsStore.disconnect()
})

watch(selectedDate, (newDate) => {
  dataStore.fetchDataDates(newDate.slice(0, 7), secretKey.value)
})
</script>

<style scoped>
/* 侧栏摘要 */
.side-summary {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-4) var(--space-5);
}

.side-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--space-3);
}

.side-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.side-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
}

.side-rank {
  width: 14px;
  flex-shrink: 0;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}

.side-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}

.side-duration {
  flex-shrink: 0;
  color: var(--text);
  font-weight: 600;
}

.side-empty {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

/* 三宫格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

/* 连接状态条：有信息才出现，固定在内容末尾不遮挡操作 */
.connection-banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}

.connection-banner.is-offline {
  background: var(--warning-soft);
  color: var(--warning);
}

.connection-banner.is-history {
  background: var(--surface-2);
  color: var(--text-muted);
}

.banner-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: currentColor;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>