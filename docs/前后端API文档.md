# 设备使用时间追踪系统 - 前后端API文档

## 1. 文档说明

本文档定义了设备使用时间追踪系统的所有API接口规范,包括REST API和WebSocket通信协议。

## 2. 接口基础信息

### 2.1 基础URL

- **开发环境**: `http://localhost:8080/api`
- **生产环境**: `https://api.example.com/api`

### 2.2 认证方式

#### 2.2.1 Token认证✅️
- **适用场景**: 已登录用户
- **请求头**: `Authorization: Bearer {token}`
- **获取方式**: 登录接口返回

#### 2.2.2 秘钥认证
- **适用场景**: 查看者访问
- **请求参数**: `?key={secretKey}`
- **获取方式**: 由被查看用户提供

### 2.3 通用响应格式✅️

```json
{
  "code": 200,               // 状态码
  "message": "操作成功",      // 提示信息
  "data": {}                 // 响应数据
}
```

### 2.4 错误响应格式✅️

```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": {}
}
```

## 3. 用户认证接口

### 3.1 发送验证码✅️

**接口**: `GET /auth/sendcode`

**描述**: 发送验证码到指定邮箱,用于注册或登录验证

**请求参数**:
- `to`: 目标邮箱地址,必填

**请求示例**: `GET /auth/sendcode?to=user@example.com`

**成功响应** (200):
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**错误响应**:
- 400: 邮箱地址为空或格式错误

**说明**:
- 验证码为6位数字
- 验证码有效期为5分钟
- 验证码会发送到指定邮箱
- 请勿将验证码泄露给他人

---


### 3.2 用户注册✅️

**接口**: `POST /auth/register`

**描述**: 用户通过邮箱注册新账号（同时填写验证码）

**请求参数**:
```json
{
  "email": "user@example.com",     // 必填,邮箱格式
  "password": "Password123"        // 必填,至少8位,包含字母和数字
  "code": "123456"                 // 必填，固定为6位
}
```

**成功响应** (201):
```json
{
  "code": 201,
  "message": "注册成功",
  "data": {
    "email": "user@example.com",
    "user_key": "aB3$xY9zK2mN7pQ",  // 自动生成的秘钥
    "createdAt": "2026-07-12T10:30:45Z"
  }
}
```

**错误响应**:
- 400: 参数验证失败
- 409: 邮箱已存在

---

### 3.3 用户登录✅️

**接口**: `POST /auth/login`

**描述**: 用户登录获取Token

**请求参数**:
```json
{
  "email": "user@example.com",
  "password": "Password123"
}
```

**成功响应** (200):
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  }
}
```

**错误响应**:
- 400: 参数验证失败
- 401: 邮箱或密码错误

---

### 3.5 退出登录✅️

**接口**: `POST /auth/logout`

**描述**: 用户退出登录,使当前Token失效

**请求头**: `Authorization: Bearer {token}`

**成功响应** (200):
```json
{
  "code": 200,
  "message": "退出成功"
  "data": null
}
```

**错误响应**:
- 401: Token无效

**说明**:
- 退出登录后,当前Token将被加入黑名单立即失效
- 用户需要重新登录获取新Token


---

### 3.7 验证秘钥

**接口**: `GET /auth/verify-key`

**描述**: 验证秘钥有效性并返回用户ID

**请求参数**: `?key={secretKey}`

**成功响应** (200):
```json
{
  "code": 200,
  "message": "秘钥有效",
  "data": {
    "userId": "12345",
    "userName": "张三"          // 可选,用户昵称
  }
}
```

**错误响应**:
- 401: 秘钥无效或已作废

## 4. 用户管理接口

### 4.1 获取用户信息

**接口**: `GET /user/profile`

**描述**: 获取当前登录用户的个人信息

**请求头**: `Authorization: Bearer {token}`

**成功响应** (200):
```json
{
  "code": 200,
  "data": {
    "userId": "12345",
    "email": "user@example.com",
    "secretKey": "aB3$xY9zK2mN7pQ",
    "createdAt": "2026-01-01T00:00:00Z",
    "lastLoginAt": "2026-07-12T10:30:45Z"
  }
}
```

---

### 4.2 重新生成秘钥

**接口**: `POST /user/regenerate-key`

**描述**: 重新生成用户秘钥,旧秘钥立即失效

**请求头**: `Authorization: Bearer {token}`

**成功响应** (200):
```json
{
  "success": true,
  "code": 200,
  "message": "秘钥已重新生成",
  "data": {
    "secretKey": "xY9zK2mN7pQaB3$"   // 新秘钥
  }
}
```

**错误响应**:
- 401: Token无效

---

### 4.3 作废秘钥

**接口**: `POST /user/revoke-key`

**描述**: 作废当前秘钥,不生成新秘钥

**请求头**: `Authorization: Bearer {token}`

**成功响应** (200):
```json
{
  "success": true,
  "code": 200,
  "message": "秘钥已作废"
}
```

**错误响应**:
- 401: Token无效

## 5. 数据上传接口

### 5.1 上传增量数据

**接口**: `POST /data/upload`

**描述**: 客户端上传增量数据

**请求头**: `Authorization: Bearer {token}`

**请求参数**:
```json
{
  "userEmail": "1234567@qq.com",
  "timestamp": "2026-07-12T10:30:45Z",
  "applications": [                   // 仅包含有变化的应用
    {
      "name": "Chrome",
      "windowTitle": "Google搜索",
      "duration": 3600,               // 累计时长(秒)
      "isActive": true                // 是否当前活跃
    },
    {
      "name": "VSCode",
      "windowTitle": "main.py",
      "duration": 1800,
      "isActive": false
    }
  ],
  "statistics": {
    "keyboardCount": 1250,            // 键盘敲击累计次数
    "mouseClickCount": 856,           // 鼠标点击累计次数
    "mouseDistance": 23.01            // 鼠标移动累计距离(米)
  }
}
```

**成功响应** (200):
```json
{
  "code": 200,
  "message": "数据上传成功",
  "data": {
    "received": true,
    "timestamp": "2026-07-12T10:30:45Z"
  }
}
```

**错误响应**:
- 400: 数据格式错误
- 401: Token无效
- 413: 数据量过大

**说明**:
- 每秒上传一次
- 只上传有变化的数据
- 已关闭应用不包含在数组中
- 服务端收到数据后立即推送给所有订阅该用户的查看者

## 6. 数据查询接口

### 6.1 获取实时数据

**接口**: `GET /data/realtime`

**描述**: 获取用户当前实时数据

**认证方式**: 
- Token认证: `Authorization: Bearer {token}`
- 或秘钥认证: `?key={secretKey}`

**成功响应** (200):
```json
{
  "code": 200,
  "data": {
    "userId": "12345",
    "timestamp": "2026-07-12T10:30:45Z",
    "date": "2026-07-12",
    "applications": [
      {
        "name": "Chrome",
        "windowTitle": "Google搜索",
        "duration": 3600,
        "isActive": true
      },
      {
        "name": "VSCode",
        "windowTitle": "main.py",
        "duration": 1800,
        "isActive": false               // 已关闭应用
      }
    ],
    "statistics": {
      "keyboardCount": 1250,
      "mouseClickCount": 856,
      "mouseDistance": 23.01
    }
  }
}
```

**错误响应**:
- 401: Token或秘钥无效
- 404: 用户不存在

---

### 6.2 获取历史数据

**接口**: `GET /data/history`

**描述**: 获取指定日期的历史数据

**认证方式**: 
- Token认证: `Authorization: Bearer {token}`
- 或秘钥认证: `?key={secretKey}`

**请求参数**:
- `date`: 日期,格式 `YYYY-MM-DD`,如 `2026-07-01`

**请求示例**: `GET /data/history?date=2026-07-01&key=aB3$xY9zK2mN7pQ`

**成功响应** (200):
```json
{
  "success": true,
  "code": 200,
  "data": {
    "date": "2026-07-01",
    "applications": [
      {
        "name": "Chrome",
        "totalDuration": 10800,        // 该应用当天总时长(秒)
        "sessions": 5,                 // 使用次数
        "windowTitles": [              // 使用过的窗口标题列表
          "Google搜索",
          "GitHub",
          "Stack Overflow"
        ]
      },
      {
        "name": "VSCode",
        "totalDuration": 7200,
        "sessions": 3,
        "windowTitles": [
          "main.py",
          "app.js"
        ]
      }
    ],
    "statistics": {
      "keyboardCount": 15000,
      "mouseClickCount": 8000,
      "mouseDistance": 150.25
    }
  }
}
```

**错误响应**:
- 400: 日期格式错误
- 401: Token或秘钥无效
- 404: 该日期无数据

---

### 6.3 获取有数据的日期列表

**接口**: `GET /data/dates`

**描述**: 获取用户有数据记录的日期列表,用于前端日历标记

**认证方式**: 
- Token认证: `Authorization: Bearer {token}`
- 或秘钥认证: `?key={secretKey}`

**请求参数**:
- `yearMonth`: 年月,格式 `YYYY-MM`,如 `2026-07`

**请求示例**: `GET /data/dates?yearMonth=2026-07&key=aB3$xY9zK2mN7pQ`

**成功响应** (200):
```json
{
  "success": true,
  "code": 200,
  "data": {
    "yearMonth": "2026-07",
    "dates": [
      "2026-07-01",
      "2026-07-02",
      "2026-07-05",
      "2026-07-08",
      "2026-07-12"
    ]
  }
}
```

**错误响应**:
- 400: 年月格式错误
- 401: Token或秘钥无效

## 7. WebSocket通信协议

### 7.1 WebSocket连接

**连接URL**: `ws://localhost:8080/ws` 或 `wss://api.example.com/ws`

**连接参数**:
- Token认证: `?token={token}`
- 或秘钥认证: `?key={secretKey}`

**连接示例**: 
- `ws://localhost:8080/ws?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
- `ws://localhost:8080/ws?key=aB3$xY9zK2mN7pQ`

### 7.2 订阅用户数据

**消息类型**: 客户端发送订阅消息

**消息格式**:
```json
{
  "type": "subscribe",
  "userId": "12345"          // 要订阅的用户ID
}
```

**说明**:
- 查看者连接后需发送订阅消息
- 订阅后才能接收该用户的实时数据推送
- 可以同时订阅多个用户(发送多条订阅消息)

### 7.3 取消订阅

**消息类型**: 客户端发送取消订阅消息

**消息格式**:
```json
{
  "type": "unsubscribe",
  "userId": "12345"
}
```

### 7.4 实时数据推送

**消息类型**: 服务端推送实时数据

**消息格式**:
```json
{
  "type": "realtime_update",
  "userId": "12345",
  "timestamp": "2026-07-12T10:30:45Z",
  "data": {
    "applications": [
      {
        "name": "Chrome",
        "windowTitle": "Google搜索",
        "duration": 3600,
        "isActive": true
      },
      {
        "name": "VSCode",
        "windowTitle": "main.py",
        "duration": 1800,
        "isActive": false
      }
    ],
    "statistics": {
      "keyboardCount": 1250,
      "mouseClickCount": 856,
      "mouseDistance": 23.01
    }
  }
}
```

**推送时机**:
- 客户端每次上传数据后立即推送
- 推送给所有订阅该用户的查看者
- 推送延迟 < 2秒

### 7.5 心跳机制

**消息类型**: 心跳消息

**客户端发送**:
```json
{
  "type": "ping"
}
```

**服务端响应**:
```json
{
  "type": "pong",
  "timestamp": "2026-07-12T10:30:45Z"
}
```

**说明**:
- 每30秒发送一次心跳
- 超过60秒未收到心跳响应则断开连接
- 断线后客户端应自动重连

### 7.6 错误消息

**消息类型**: 错误消息

**消息格式**:
```json
{
  "type": "error",
  "code": 401,
  "message": "Token无效或已过期"
}
```

**常见错误**:
- 401: Token或秘钥无效
- 403: 无权限订阅该用户
- 404: 用户不存在

### 7.7 连接状态消息

**消息类型**: 连接成功消息

**消息格式**:
```json
{
  "type": "connected",
  "message": "WebSocket连接成功",
  "userId": "12345"          // 如果是用户自己连接
}
```

## 8. 状态码定义

### 8.1 HTTP状态码

| 状态码 | 说明 | 常见场景 |
|--------|------|----------|
| 200 | 成功 | 请求成功 |
| 201 | 已创建 | 注册成功 |
| 204 | 无内容 | 作废秘钥成功 |
| 400 | 请求参数错误 | 参数验证失败 |
| 401 | 未授权 | Token或秘钥无效 |
| 403 | 禁止访问 | 无权限访问 |
| 404 | 资源不存在 | 用户或数据不存在 |
| 409 | 冲突 | 邮箱已存在 |
| 413 | 请求体过大 | 数据上传量过大 |
| 429 | 请求过于频繁 | 触发限流 |
| 500 | 服务器内部错误 | 服务器异常 |
| 503 | 服务不可用 | 服务维护中 |

### 8.2 WebSocket消息类型

| 类型 | 方向 | 说明 |
|------|------|------|
| subscribe | 客户端→服务端 | 订阅用户数据 |
| unsubscribe | 客户端→服务端 | 取消订阅 |
| realtime_update | 服务端→客户端 | 推送实时数据 |
| ping | 客户端→服务端 | 心跳请求 |
| pong | 服务端→客户端 | 心跳响应 |
| error | 服务端→客户端 | 错误消息 |
| connected | 服务端→客户端 | 连接成功消息 |

## 9. 数据字段详细说明

### 9.1 应用数据字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 应用名称 |
| windowTitle | String | 是 | 窗口标题 |
| duration | Integer | 是 | 累计使用时长(秒) |
| isActive | Boolean | 是 | 是否当前活跃窗口 |
| totalDuration | Integer | 是 | 历史数据中的总时长(秒) |
| sessions | Integer | 否 | 使用次数(历史数据) |
| windowTitles | Array | 否 | 窗口标题列表(历史数据) |

### 9.2 统计数据字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyboardCount | Integer | 是 | 键盘敲击累计次数 |
| mouseClickCount | Integer | 是 | 鼠标点击累计次数 |
| mouseDistance | Float | 是 | 鼠标移动累计距离(米),保留两位小数 |

### 9.3 用户数据字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | String | 是 | 用户ID,全局唯一 |
| email | String | 是 | 用户邮箱,作为登录凭证 |
| secretKey | String | 是 | 用户秘钥,不超过32位 |
| createdAt | DateTime | 是 | 注册时间 |
| lastLoginAt | DateTime | 否 | 最后登录时间 |

## 10. 接口调用示例

### 10.1 发送验证码并注册

```bash
# 1. 发送验证码
curl -X GET "http://localhost:8080/api/auth/register?to=user@example.com"

# 响应:
# {
#   "code": 200,
#   "message": "操作成功",
#   "data": null
# }

# 2. 验证码校验(可选,注册时会自动校验)
curl -X GET "http://localhost:8080/api/auth/verify?email=user@example.com&code=123456"

# 响应:
# {
#   "code": 200,
#   "message": "验证成功",
#   "data": null
# }

# 3. 注册
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "Password123"
  }'

# 响应:
# {
#   "success": true,
#   "code": 201,
#   "data": {
#     "userId": "12345",
#     "secretKey": "aB3$xY9zK2mN7pQ",
#     ...
#   }
# }

# 4. 登录获取Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "Password123"
  }'

# 响应:
# {
#   "success": true,
#   "data": {
#     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#     ...
#   }
# }

# 5. 退出登录
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 响应:
# {
#   "success": true,
#   "code": 200,
#   "message": "退出成功"
# }
```

### 10.2 客户端上传数据

```bash
curl -X POST http://localhost:8080/api/data/upload \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{
    "userId": "12345",
    "timestamp": "2026-07-12T10:30:45Z",
    "applications": [
      {
        "name": "Chrome",
        "windowTitle": "Google搜索",
        "duration": 3600,
        "isActive": true
      }
    ],
    "statistics": {
      "keyboardCount": 1250,
      "mouseClickCount": 856,
      "mouseDistance": 23.01
    }
  }'
```

### 10.3 查看者通过秘钥访问

```bash
# 1. 验证秘钥
curl -X GET "http://localhost:8080/api/auth/verify-key?key=aB3\$xY9zK2mN7pQ"

# 2. 获取实时数据
curl -X GET "http://localhost:8080/api/data/realtime?key=aB3\$xY9zK2mN7pQ"

# 3. 获取历史数据
curl -X GET "http://localhost:8080/api/data/history?date=2026-07-01&key=aB3\$xY9zK2mN7pQ"
```

### 10.4 WebSocket连接示例

```javascript
// 前端Vue3代码示例
const ws = new WebSocket('ws://localhost:8080/ws?key=aB3$xY9zK2mN7pQ')

ws.onopen = () => {
  console.log('WebSocket已连接')
  
  // 订阅用户数据
  ws.send(JSON.stringify({
    type: 'subscribe',
    userId: '12345'
  }))
}

ws.onmessage = (event) => {
  const message = JSON.parse(event.data)
  
  switch(message.type) {
    case 'realtime_update':
      // 更新实时数据
      updateRealtimeData(message.data)
      break
    case 'pong':
      // 心跳响应
      console.log('心跳正常')
      break
    case 'error':
      // 错误处理
      console.error('WebSocket错误:', message.message)
      break
  }
}

// 心跳定时器
setInterval(() => {
  ws.send(JSON.stringify({ type: 'ping' }))
}, 30000)
```

## 11. 接口限流策略

### 11.1 限流规则

| 接口类型 | 限流规则 | 说明 |
|----------|----------|------|
| 数据上传 | 每秒1次 | 客户端应严格控制上传频率 |
| 登录接口 | 每分钟5次 | 防止暴力破解 |
| 注册接口 | 每小时3次 | 防止恶意注册 |
| 其他接口 | 每秒10次 | 正常使用不受影响 |

### 11.2 限流响应

**HTTP状态码**: 429

**响应格式**:
```json
{
  "success": false,
  "code": 429,
  "message": "请求过于频繁,请稍后再试",
  "retryAfter": 60             // 多少秒后可重试
}
```

## 12. 安全机制

### 12.1 Token机制

- **JWT签名**: 使用RS256算法签名
- **有效期**: 7天
- **刷新机制**: Token过期前自动刷新
- **黑名单**: Token可被加入黑名单强制失效

### 12.2 秘钥机制

- **长度限制**: 不超过32位
- **字符集**: 大小写字母、数字、常见符号
- **唯一性**: 全局唯一,不可重复
- **有效期**: 永久有效,直到重新生成或作废

### 12.3 数据安全

- **HTTPS**: 生产环境强制HTTPS
- **密码加密**: BCrypt加密存储
- **敏感数据**: 不记录密码明文
- **日志脱敏**: 日志中不记录Token和秘钥

## 13. 附录

### 13.1 常见错误处理

1. **Token过期**:
   - 前端检测到401错误
   - 自动调用刷新Token接口
   - 刷新失败则跳转登录页

2. **秘钥失效**:
   - 显示"秘钥已失效"提示
   - 引导用户联系秘钥提供者

3. **WebSocket断线**:
   - 自动重连机制
   - 指数退避策略
   - 重连失败提示用户刷新

### 13.2 版本管理

- **当前版本**: v1.1
- **版本控制**: URL中包含版本号,如 `/api/v1/...`
- **兼容性**: 保持向下兼容

### 13.3 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-07-13 | v1.1 | 新增验证码发送接口(3.1)、验证码校验接口(3.2)、退出登录接口(3.5) |
| 2026-07-12 | v1.0 | 初始版本,定义所有基础接口 |
