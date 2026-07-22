import request from '@/utils/request'
import type { ApiResponse, RealtimeData, HistoryData, DateListData } from '@/types'

// 获取实时数据
export const getRealtimeDataApi = (key?: string) => {
  const params = key ? `?key=${key}` : ''
  return request.get<ApiResponse<RealtimeData>>(`/data/realtime${params}`)
}

// 获取历史数据
export const getHistoryDataApi = (date: string, key?: string) => {
  const params = key ? `?date=${date}&key=${key}` : `?date=${date}`
  return request.get<ApiResponse<HistoryData>>(`/data/history${params}`)
}

// 获取有数据的日期列表
export const getDataDatesApi = (yearMonth: string, key?: string) => {
  const params = key ? `?yearMonth=${yearMonth}&key=${key}` : `?yearMonth=${yearMonth}`
  return request.get<ApiResponse<DateListData>>(`/data/dates${params}`)
}
