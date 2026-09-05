import request from '@/utils/request'
import type { ApiResponse, RealtimeData, HistoryData, DateListData } from '@/types'

// 获取实时数据
// 支持Token认证(请求拦截器自动附加)或秘钥认证(?key=, 走axios params编码)
export const getRealtimeDataApi = (key?: string) => {
  return request.get<ApiResponse<RealtimeData>>('/data/realtime', {
    params: key ? { key } : undefined
  })
}

// 获取历史数据
// 支持Token认证或秘钥认证; 该日期无数据(404)按空状态处理,不弹错误提示
export const getHistoryDataApi = (date: string, key?: string) => {
  return request.get<ApiResponse<HistoryData>>('/data/history', {
    params: key ? { date, key } : { date },
    ignore404: true
  })
}

// 获取有数据的日期列表
// 支持Token认证或秘钥认证
export const getDataDatesApi = (yearMonth: string, key?: string) => {
  return request.get<ApiResponse<DateListData>>('/data/dates', {
    params: key ? { yearMonth, key } : { yearMonth }
  })
}
