# 设备使用时间追踪系统 - Web前端

✅ 项目构建完成!

## 📦 项目结构

```
web/
├── src/
│   ├── api/              # API接口封装
│   │   ├── auth.ts       # 认证接口
│   │   ├── user.ts       # 用户接口
│   │   ├── data.ts       # 数据接口
│   │   └── index.ts      # 导出
│   ├── assets/           # 静态资源
│   ├── components/       # 通用组件
│   │   ├── AppHeader.vue # 导航栏
│   │   ├── Calendar.vue  # 日历组件
│   │   ├── PieChart.vue  # 饼图
│   │   ├── BarChart.vue  # 条形图
│   │   └── StatsCard.vue # 统计卡片
│   ├── views/            # 页面组件
│   │   ├── Login.vue     # 登录页
│   │   ├── Register.vue  # 注册页
│   │   ├── Monitor.vue   # 监控页
│   │   ├── Profile.vue   # 个人中心
│   │   └── KeyView.vue   # 秘钥查看
│   ├── router/           # 路由配置
│   │   └── index.ts
│   ├── stores/           # Pinia状态管理
│   │   ├── user.ts       # 用户状态
│   │   ├── data.ts       # 数据状态
│   │   └── websocket.ts  # WebSocket状态
│   ├── types/            # TypeScript类型
│   │   ├── index.ts      # 通用类型
│   │   ├── data.ts       # 数据类型
│   │   ├── websocket.ts  # WebSocket类型
│   │   └── history.ts    # 历史数据类型
│   ├── utils/            # 工具函数
│   │   ├── request.ts    # Axios封装
│   │   └── format.ts     # 格式化工具
│   ├── App.vue           # 根组件
│   └── main.ts           # 应用入口
├── public/               # 公共资源
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── .env                  # 开发环境变量
├── .env.production       # 生产环境变量
└── README.md
```

## 🚀 快速开始

### 0. 启动后端(必需)

前端所有接口均对接真实后端,请先启动 Spring Boot 后端(`http://localhost:8080`),并保证 MySQL、Redis 可用:

```bash
cd server/backend
mvn spring-boot:run
```

### 1. 安装依赖

```bash
cd web
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

应用将在 http://localhost:3000 启动,`/api` 与 `/ws` 请求由 Vite 自动代理到后端 8080。

### 3. 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist/` 目录

## ✨ 功能特性

### 用户认证
- ✅ 邮箱注册(含验证码验证)
- ✅ 用户登录/退出
- ✅ Token自动刷新
- ✅ 路由守卫

### 实时监控
- ✅ 实时数据展示
- ✅ 当前活跃应用显示
- ✅ WebSocket实时推送
- ✅ 断线自动重连
- ✅ 心跳保活机制

### 历史数据
- ✅ 日历选择日期
- ✅ 历史数据查询
- ✅ 有数据日期标记
- ✅ 快捷日期选择(今天、昨天)

### 数据可视化
- ✅ 饼图: 应用使用时长占比
- ✅ 条形图: 应用使用时长排行
- ✅ 统计卡片: 键盘、鼠标数据

### 秘钥管理
- ✅ 秘钥查看与复制
- ✅ 重新生成秘钥
- ✅ 作废秘钥
- ✅ 分享链接生成

### 秘钥访问
- ✅ 无需注册查看
- ✅ 秘钥验证
- ✅ 只读权限

## 🔌 后端对接

开发模式下通过 Vite 代理直接对接真实后端(Spring Boot,默认 `http://localhost:8080`):

- **REST API**: `/api/**` 由 Vite 代理转发到后端,支持 Token 认证(`Authorization: Bearer`)与秘钥认证(`?key=`)
- **WebSocket**: `/ws/**` 由 Vite 代理转发到后端,用于实时数据推送
- 认证/数据/用户等所有接口均需后端可用,不再使用 Mock 数据
- 接口定义与响应结构详见仓库根目录 `docs/前后端API文档.md`

## 🔧 技术栈

- **框架**: Vue 3 (Composition API)
- **语言**: TypeScript
- **UI组件**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router
- **图表**: Chart.js + vue-chartjs
- **HTTP**: Axios
- **构建**: Vite

## 🎨 页面路由

| 路径 | 组件 | 说明 | 权限 |
|------|------|------|------|
| `/login` | Login.vue | 登录页 | 公开 |
| `/register` | Register.vue | 注册页 | 公开 |
| `/monitor/:userId?` | Monitor.vue | 监控页 | 公开(支持秘钥访问) |
| `/profile` | Profile.vue | 个人中心 | 需登录 |
| `/view` | KeyView.vue | 秘钥查看 | 公开 |

## 🔐 环境变量

### 开发环境 (.env)
```
VITE_API_BASE_URL=/api
```

### 生产环境 (.env.production)
```
VITE_API_BASE_URL=https://api.example.com/api
```

## 📝 开发说明

### 添加新的API接口
1. 在 `src/types/` 定义类型
2. 在 `src/api/` 创建接口函数
3. 由 Vite 代理转发到后端,确保后端已实现对应接口

### 添加新的页面
1. 在 `src/views/` 创建页面组件
2. 在 `src/router/index.ts` 添加路由
3. 根据需要添加路由守卫

### 状态管理
使用 Pinia store:
- `useUserStore`: 用户状态
- `useDataStore`: 数据状态
- `useWebSocketStore`: WebSocket状态

## 🐛 调试技巧

### 查看接口请求
打开浏览器控制台 → Network 面板,可查看 `/api/**` 请求与响应。请求错误提示由 `src/utils/request.ts` 统一弹出。

### WebSocket调试
在控制台查看WebSocket连接状态:
```javascript
// 在Vue Devtools中查看
wsStore.connected
wsStore.reconnecting
wsStore.error
```

## 🚢 部署

### 生产构建
```bash
npm run build
```

### 部署到Nginx
将 `dist/` 目录内容复制到Nginx静态文件目录:
```nginx
server {
    listen 80;
    server_name example.com;
    
    location / {
        root /path/to/dist;
        try_files $uri $uri/ /index.html;
    }
    
    location /api {
        proxy_pass http://backend:8080;
    }
    
    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## 📄 License

MIT

## 👨‍💻 开发者

设备使用时间追踪系统前端团队
