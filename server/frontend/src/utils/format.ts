// 格式化时长为 HH:MM:SS
export const formatDuration = (seconds: number): string => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 格式化为中文时长文本(如: 6小时42分钟 / 1小时48分钟 / 52分钟 / 45秒)
export const formatDurationText = (seconds: number): string => {
  const totalSeconds = Math.max(0, Math.round(seconds))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const secs = totalSeconds % 60

  const parts: string[] = []
  if (hours > 0) {
    parts.push(`${hours}小时`)
  }
  if (minutes > 0) {
    parts.push(`${minutes}分钟`)
  }
  // 不足1分钟时精确到秒
  if (hours === 0 && minutes === 0 && secs > 0) {
    parts.push(`${secs}秒`)
  }
  return parts.length > 0 ? parts.join('') : '0秒'
}

// 将时长拆成"数值 + 单位"两段，便于大字展示(如 8 小时 12 分钟 → [8, '小时', 12, '分钟'])
export const splitDurationText = (seconds: number): Array<{ value: string; unit: string }> => {
  const totalSeconds = Math.max(0, Math.round(seconds))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const secs = totalSeconds % 60

  if (hours > 0) {
    return [
      { value: String(hours), unit: '小时' },
      ...(minutes > 0 ? [{ value: String(minutes), unit: '分钟' }] : [])
    ]
  }
  if (minutes > 0) {
    return [{ value: String(minutes), unit: '分钟' }]
  }
  return [{ value: String(secs), unit: '秒' }]
}

// 格式化数字,添加千分位分隔符
export const formatNumber = (num: number): string => {
  return num.toLocaleString('zh-CN')
}

// 格式化距离
export const formatDistance = (meters: number): string => {
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(2)}km`
  }
  return `${meters.toFixed(2)}m`
}

// 距离拆成"数值 + 单位"，供大字卡片排版使用(1240.5 → { value: '1.24', unit: 'km' })
export const splitDistance = (meters: number): { value: string; unit: string } => {
  if (meters >= 1000) {
    return { value: (meters / 1000).toFixed(2), unit: 'km' }
  }
  return { value: meters.toFixed(1), unit: 'm' }
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

// 中文日期(11月18日 周二)，日历与页头标题共用
export const formatChineseDate = (dateKey: string): string => {
  const parts = dateKey.split('-')
  if (parts.length !== 3) {
    return dateKey
  }
  const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
  const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
  return `${Number(parts[1])}月${Number(parts[2])}日 ${weekday}`
}

// 邮箱脱敏(与后端 MaskUtil 规则一致)：abc123@qq.com → a***@qq.com
export const maskEmail = (email: string): string => {
  if (!email) {
    return ''
  }
  const at = email.indexOf('@')
  if (at <= 0) {
    return email.length <= 4 ? '****' : `${email.slice(0, 4)}****`
  }
  const local = email.slice(0, at)
  const domain = email.slice(at)
  return local.length <= 2 ? `${'*'.repeat(local.length)}${domain}` : `${local[0]}***${domain}`
}

// 时间戳(ISO)转"刚刚 / 12 秒前 / 3 分钟前"，用于"最后更新"提示
export const formatRelativeTime = (timestamp: string | null | undefined): string => {
  if (!timestamp) {
    return '暂无更新'
  }
  const target = new Date(timestamp).getTime()
  if (Number.isNaN(target)) {
    return '暂无更新'
  }
  const diffSeconds = Math.max(0, Math.round((Date.now() - target) / 1000))
  if (diffSeconds < 5) {
    return '刚刚'
  }
  if (diffSeconds < 60) {
    return `${diffSeconds} 秒前`
  }
  if (diffSeconds < 3600) {
    return `${Math.floor(diffSeconds / 60)} 分钟前`
  }
  return `${Math.floor(diffSeconds / 3600)} 小时前`
}