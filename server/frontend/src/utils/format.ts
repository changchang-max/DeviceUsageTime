// 格式化时长为 HH:MM:SS
export const formatDuration = (seconds: number): string => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 格式化数字,添加千分位分隔符
export const formatNumber = (num: number): string => {
  return num.toLocaleString('zh-CN')
}

// 格式化距离
export const formatDistance = (meters: number): string => {
  return `${meters.toFixed(2)}m`
}

// 格式化日期
export const formatDate = (date: string | Date): string => {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString('zh-CN')
}

// 本地时区的日期键(YYYY-MM-DD)。
// 不能使用 new Date().toISOString(): 它返回的是UTC日期,在非UTC时区会把
// 凌晨(00:00-07:59,东八区)的数据日期算到前一天,与后端归属日期不一致
export const toLocalDateKey = (date: Date = new Date()): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 本地时区的年月键(YYYY-MM)
export const toLocalYearMonthKey = (date: Date = new Date()): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

// 格式化日期时间
export const formatDateTime = (date: string | Date): string => {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleString('zh-CN')
}
