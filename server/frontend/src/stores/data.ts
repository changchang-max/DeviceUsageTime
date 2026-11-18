import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RealtimeData, HistoryData } from '@/types'
import { getRealtimeDataApi, getHistoryDataApi, getDataDatesApi } from '@/api'

export const useDataStore = defineStore('data', () => {
  // 状态
  const realtimeData = ref<RealtimeData | null>(null)
  const historyData = ref<HistoryData | null>(null)
  const dataDatesList = ref<string[]>([])
  const loading = ref(false)
  // 最近一次请求失败的原因(供页面展示空态/错误态文案)
  const errorMessage = ref<string | null>(null)

  // 获取实时数据
  const fetchRealtimeData = async (key?: string) => {
    loading.value = true
    errorMessage.value = null
    try {
      const res = await getRealtimeDataApi(key)
      realtimeData.value = res.data ?? null
    } catch (error) {
      console.error('获取实时数据失败:', error)
      realtimeData.value = null
      errorMessage.value = '实时数据加载失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  // 获取历史数据
  // 后端"该日期无数据"返回 code=404 且请求层标记了 ignore404，
  // 此时 res.data 为 null，属于正常空状态而不是异常
  const fetchHistoryData = async (date: string, key?: string) => {
    loading.value = true
    errorMessage.value = null
    try {
      const res = await getHistoryDataApi(date, key)
      historyData.value = res.data ?? null
    } catch (error) {
      console.error('获取历史数据失败:', error)
      historyData.value = null
      errorMessage.value = '历史数据加载失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  // 获取有数据的日期列表
  const fetchDataDates = async (yearMonth: string, key?: string) => {
    try {
      const res = await getDataDatesApi(yearMonth, key)
      dataDatesList.value = res.data?.dates ?? []
    } catch (error) {
      console.error('获取日期列表失败:', error)
      dataDatesList.value = []
    }
  }

  // 更新实时数据(WebSocket推送)
  const updateRealtimeData = (data: RealtimeData) => {
    realtimeData.value = data
  }

  return {
    realtimeData,
    historyData,
    dataDatesList,
    loading,
    errorMessage,
    fetchRealtimeData,
    fetchHistoryData,
    fetchDataDates,
    updateRealtimeData
  }
})