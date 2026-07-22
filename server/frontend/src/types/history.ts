import { HistoryApplication, Statistics } from './data'

// 历史数据响应类型
export interface HistoryData {
  date: string
  applications: HistoryApplication[]
  statistics: Statistics
}

// 日期列表响应类型
export interface DateListData {
  yearMonth: string
  dates: string[]
}
