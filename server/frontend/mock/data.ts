import type { MockMethod } from 'vite-plugin-mock'

// 模拟实时数据
const mockRealtimeData = {
  userId: '12345',
  timestamp: new Date().toISOString(),
  date: new Date().toISOString().split('T')[0],
  applications: [
    {
      name: 'Chrome',
      windowTitle: 'Google搜索',
      duration: 3600,
      isActive: true
    },
    {
      name: 'VSCode',
      windowTitle: 'main.py',
      duration: 1800,
      isActive: false
    },
    {
      name: 'WeChat',
      windowTitle: '微信',
      duration: 1200,
      isActive: false
    },
    {
      name: 'QQ音乐',
      windowTitle: '正在播放: 周杰伦 - 晴天',
      duration: 900,
      isActive: false
    }
  ],
  statistics: {
    keyboardCount: 1250,
    mouseClickCount: 856,
    mouseDistance: 23.01
  }
}

export default [
  // 获取实时数据
  {
    url: '/api/data/realtime',
    method: 'get',
    response: () => {
      // 随机更新一些数据以模拟实时变化
      mockRealtimeData.timestamp = new Date().toISOString()
      mockRealtimeData.statistics.keyboardCount += Math.floor(Math.random() * 10)
      mockRealtimeData.statistics.mouseClickCount += Math.floor(Math.random() * 5)
      mockRealtimeData.applications[0].duration += 1
      
      return {
        success: true,
        code: 200,
        message: '获取成功',
        data: mockRealtimeData
      }
    }
  },
  
  // 获取历史数据
  {
    url: '/api/data/history',
    method: 'get',
    response: ({ query }: any) => {
      const { date } = query
      
      return {
        success: true,
        code: 200,
        message: '获取成功',
        data: {
          date,
          applications: [
            {
              name: 'Chrome',
              totalDuration: 10800,
              sessions: 5,
              windowTitles: ['Google搜索', 'GitHub', 'Stack Overflow']
            },
            {
              name: 'VSCode',
              totalDuration: 7200,
              sessions: 3,
              windowTitles: ['main.py', 'app.js', 'index.html']
            },
            {
              name: 'WeChat',
              totalDuration: 3600,
              sessions: 8,
              windowTitles: ['微信']
            }
          ],
          statistics: {
            keyboardCount: 15000,
            mouseClickCount: 8000,
            mouseDistance: 150.25
          }
        }
      }
    }
  },
  
  // 获取有数据的日期列表
  {
    url: '/api/data/dates',
    method: 'get',
    response: ({ query }: any) => {
      const { yearMonth } = query
      
      // 生成当月的一些随机日期
      const year = parseInt(yearMonth.split('-')[0])
      const month = parseInt(yearMonth.split('-')[1])
      const daysInMonth = new Date(year, month, 0).getDate()
      
      const dates: string[] = []
      for (let i = 1; i <= daysInMonth; i++) {
        // 随机生成一些有数据的日期
        if (Math.random() > 0.7) {
          const day = i.toString().padStart(2, '0')
          dates.push(`${yearMonth}-${day}`)
        }
      }
      
      return {
        success: true,
        code: 200,
        message: '获取成功',
        data: {
          yearMonth,
          dates
        }
      }
    }
  }
] as MockMethod[]
