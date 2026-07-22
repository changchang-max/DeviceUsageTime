// 应用数据类型
export interface Application {
  name: string
  windowTitle: string
  duration: number
  isActive: boolean
}

// 历史应用数据类型
export interface HistoryApplication {
  name: string
  totalDuration: number
  sessions: number
  windowTitles: string[]
}

// 统计数据类型
export interface Statistics {
  keyboardCount: number
  mouseClickCount: number
  mouseDistance: number
}

// 实时数据类型
export interface RealtimeData {
  userId: string
  timestamp: string
  date: string
  applications: Application[]
  statistics: Statistics
}
