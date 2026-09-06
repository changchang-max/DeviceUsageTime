// 应用数据类型
export interface Application {
  name: string
  windowTitle: string
  duration: number
  isActive: boolean
  // 是否仍在运行(当前是否仍在持续上报)。true=运行中, false=已关闭。
  // 与isActive配合: isActive且isRunning=true=桌面最顶端窗口; 仅isRunning=true=后台运行。
  // 历史/旧数据可能缺失该字段, 故为可选
  isRunning?: boolean | null
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
