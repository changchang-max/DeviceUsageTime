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
  
  // 获取实时数据
  const fetchRealtimeData = async (key?: string) => {
    loading.value = true
    try {
      const res = await getRealtimeDataApi(key)
      realtimeData.value = res.data
    } catch (error) {
      console.error('获取实时数据失败:', error)
    } finally {
      loading.value = false
    }
  }
  
  // 获取历史数据
  const fetchHistoryData = async (date: string, key?: string) => {
    loading.value = true
    try {
      const res = await getHistoryDataApi(date, key)
      historyData.value = res.data
    } catch (error) {
      console.error('获取历史数据失败:', error)
    } finally {
      loading.value = false
    }
  }
  
  // 获取有数据的日期列表
  const fetchDataDates = async (yearMonth: string, key?: string) => {
    try {
      const res = await getDataDatesApi(yearMonth, key)
      dataDatesList.value = res.data.dates
    } catch (error) {
      console.error('获取日期列表失败:', error)
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
    fetchRealtimeData,
    fetchHistoryData,
    fetchDataDates,
    updateRealtimeData
  }
})
