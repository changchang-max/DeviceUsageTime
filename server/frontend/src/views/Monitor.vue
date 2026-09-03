<template>
  <div class="monitor-page">
    <AppHeader />
    
    <div class="monitor-container">
      <div class="sidebar">
        <Calendar
          v-model="selectedDate"
          :data-dates="dataStore.dataDatesList"
          @date-change="handleDateChange"
        />
      </div>
      
      <div class="main-content">
        <!-- 实时状态展示 -->
        <div class="status-bar">
          <div class="current-app">
            <span class="label">当前使用:</span>
            <span class="app-name">{{ currentApp }}</span>
            <el-tag v-if="isRealtime" type="success" size="small">实时</el-tag>
            <el-tag v-else type="info" size="small">历史数据</el-tag>
          </div>
          
          <div class="connection-status">
            <el-icon v-if="wsStore.connected" color="#67c23a">
              <SuccessFilled />
            </el-icon>
            <el-icon v-else-if="wsStore.reconnecting" color="#e6a23c">
              <Loading />
            </el-icon>
            <el-icon v-else color="#f56c6c">
              <CircleClose />
            </el-icon>
            <span v-if="wsStore.error" class="error-text">{{ wsStore.error }}</span>
            <span v-else>{{ connectionStatus }}</span>
          </div>
        </div>
        
        <!-- 图表展示 -->
        <div class="charts-section">
          <el-card class="chart-card">
            <template #header>
              <h3>应用使用时长占比</h3>
            </template>
            <PieChart v-if="pieChartData.length > 0" :data="pieChartData" />
            <el-empty v-else description="暂无数据" />
          </el-card>
          
          <el-card class="chart-card">
            <template #header>
              <h3>应用使用时长排行</h3>
            </template>
            <BarChart v-if="barChartData.length > 0" :data="barChartData" />
            <el-empty v-else description="暂无数据" />
          </el-card>
        </div>
        
        <!-- 统计数据卡片 -->
        <div class="stats-section">
          <StatsCard
            type="keyboard"
            :value="statistics.keyboardCount"
            label="键盘敲击"
          />
          <StatsCard
            type="mouse-click"
            :value="statistics.mouseClickCount"
            label="鼠标点击"
          />
          <StatsCard
            type="mouse-distance"
            :value="statistics.mouseDistance"
            label="鼠标移动距离"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useDataStore } from '@/stores/data'
import { useWebSocketStore } from '@/stores/websocket'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import Calendar from '@/components/Calendar.vue'
import PieChart from '@/components/PieChart.vue'
import BarChart from '@/components/BarChart.vue'
import StatsCard from '@/components/StatsCard.vue'
import { SuccessFilled, Loading, CircleClose } from '@element-plus/icons-vue'
import type { WebSocketMessage } from '@/types/websocket'

const route = useRoute()
const dataStore = useDataStore()
const wsStore = useWebSocketStore()
const userStore = useUserStore()

const selectedDate = ref(new Date().toISOString().split('T')[0])
const isRealtime = ref(true)
const userId = ref<string>('')
const secretKey = ref<string>('')

const currentApp = computed(() => {
  if (isRealtime.value && dataStore.realtimeData) {
    const activeApp = dataStore.realtimeData.applications.find(app => app.isActive)
    if (activeApp) {
      return `${activeApp.name} (${activeApp.windowTitle})`
    }
  }
  return '无活跃应用'
})

const connectionStatus = computed(() => {
  if (wsStore.connected) return '已连接'
  if (wsStore.reconnecting) return '重连中...'
  return '未连接'
})

const pieChartData = computed(() => {
  const apps = isRealtime.value
    ? dataStore.realtimeData?.applications || []
    : dataStore.historyData?.applications || []
  
  return apps.map(app => ({
    name: app.name,
    value: 'duration' in app ? app.duration : app.totalDuration
  })).sort((a, b) => b.value - a.value)
})

const barChartData = computed(() => {
  return pieChartData.value
})

const statistics = computed(() => {
  const data = isRealtime.value ? dataStore.realtimeData : dataStore.historyData
  return data?.statistics || {
    keyboardCount: 0,
    mouseClickCount: 0,
    mouseDistance: 0
  }
})

const handleDateChange = async (date: string) => {
  const today = new Date().toISOString().split('T')[0]
  isRealtime.value = date === today
  
  if (isRealtime.value) {
    await dataStore.fetchRealtimeData(secretKey.value)
  } else {
    await dataStore.fetchHistoryData(date, secretKey.value)
  }
}

const handleWsMessage = (message: WebSocketMessage) => {
  if (message.type === 'realtime_update' && isRealtime.value) {
    // 合并推送的实时数据(推送仅含applications/statistics，保留原有userId等信息)
    const merged = {
      ...(dataStore.realtimeData || {}),
      userId: message.userId,
      timestamp: message.timestamp,
      applications: message.data?.applications || [],
      statistics: message.data?.statistics
    } as any
    dataStore.updateRealtimeData(merged)
  } else if (message.type === 'error') {
    console.error('WebSocket错误:', message.code, message.message)
  }
}

onMounted(async () => {
  const authToken = userStore.token || undefined

  // 已登录但尚未获取用户信息时先拉取(用于获取邮箱作为订阅标识)
  if (authToken && !userStore.userInfo) {
    await userStore.fetchUserInfo()
  }

  // 获取userId(本系统中为用户邮箱)和secretKey
  userId.value = (route.params.userId as string) || userStore.userInfo?.email || ''
  secretKey.value = route.query.key as string || ''
  const authKey = secretKey.value || undefined

  // 获取当前月份有数据的日期
  const yearMonth = new Date().toISOString().slice(0, 7)
  await dataStore.fetchDataDates(yearMonth, authKey)

  // 获取实时数据
  await dataStore.fetchRealtimeData(authKey)

  // 连接WebSocket(登录用户用Token，秘钥查看者用秘钥)。
  // 秘钥查看者不知道被查看用户标识，但仍建立连接，服务端会自动订阅秘钥持有者
  if (userId.value || authToken || authKey) {
    wsStore.connect(userId.value, authToken, authKey)
    wsStore.setMessageHandler(handleWsMessage)
  }
})

onUnmounted(() => {
  wsStore.setMessageHandler(null)
  wsStore.disconnect()
})

watch(() => selectedDate.value, (newDate) => {
  const yearMonth = newDate.slice(0, 7)
  dataStore.fetchDataDates(yearMonth, secretKey.value)
})
</script>

<style scoped>
.monitor-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.monitor-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
}

.sidebar {
  position: sticky;
  top: 80px;
  height: fit-content;
}

.main-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.status-bar {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.current-app {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-app .label {
  color: #909399;
  font-size: 14px;
}

.current-app .app-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
}

.connection-status .error-text {
  color: #f56c6c;
  font-size: 13px;
}

.charts-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.chart-card {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.chart-card h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

@media (max-width: 1200px) {
  .monitor-container {
    grid-template-columns: 1fr;
  }
  
  .sidebar {
    position: static;
  }
}

@media (max-width: 768px) {
  .charts-section {
    grid-template-columns: 1fr;
  }
  
  .stats-section {
    grid-template-columns: 1fr;
  }
}
</style>
