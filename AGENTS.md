# Repository Guidelines

本文档为贡献者提供项目开发指南，涵盖项目结构、开发规范、构建命令和提交要求。

## 项目结构

```
DeviceUsageTime/
├── client/              # Python 桌面客户端（PyQt5）
│   ├── main.py         # 程序入口
│   ├── mytools.py      # 工具函数库
│   ├── ui_*.py         # UI 界面文件
│   ├── package.txt     # Python 依赖列表
│   ├── config/         # 配置文件目录
│   ├── history_data/   # 历史数据存储
│   └── dist/           # 打包输出目录
├── server/             # Web 端（Spring Boot + Vue3）
│   ├── backend/        # Spring Boot 后端
│   │   └── src/main/java/top/primordialcode/backend/
│   │       ├── controller/  # 控制器层
│   │       ├── service/     # 业务逻辑层
│   │       └── entity/      # 实体类
│   └── frontend/       # Vue3 前端
│       ├── src/        # 源代码
│       └── package.json
└── docs/               # 项目文档
    ├── 项目总体概述.md
    ├── 功能需求文档.md
    ├── 前后端API文档.md
    └── 后端开发规范.md
```

## 构建与运行命令

### 客户端（Python）

```bash
# 进入客户端目录
cd client

# 安装依赖（需要 Python 3.10+）
pip install -r package.txt

# 运行程序
python main.py

# 打包为 exe（使用 PyInstaller）
pyinstaller main.spec
```

### 后端（Spring Boot）

```bash
# 进入后端目录
cd server/backend

# 编译并打包（跳过测试）
mvn clean package -DskipTests

# 运行项目
mvn spring-boot:run

# 或运行打包好的 jar
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 前端（Vue3 + Vite）

```bash
# 进入前端目录
cd server/frontend

# 安装依赖
npm ci

# 开发模式运行
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

## 编码规范

### Python 客户端

- **编码**：所有 Python 文件使用 UTF-8 编码
- **注释**：不要修改已有注释；新增注释使用中文，确保 UTF-8 格式
- **命名**：
  - 变量/函数：`snake_case`（如 `all_applications_dict`）
  - 类名：`PascalCase`（如 `MainWindow`）
- **格式化**：无特定工具，保持现有代码风格一致
- **环境**：
  - 运行程序时使用conda的`DeviceUsageTime`环境。
  - 切换命令：`conda activate DeviceUsageTime`

### Java 后端

- **包结构**：`top.primordialcode.backend.{模块}`
- **路径前缀**：
  - `controller.api` 包下的控制器自动添加 `/api` 前缀
  - `controller.index` 包下的控制器自动添加 `/index` 前缀
  - **不要**在 `@RequestMapping` 中重复写前缀
- **示例**：
  ```java
  // 位置：controller/api/auth/LoginController.java
  @RestController
  @RequestMapping("/auth")  // ✅ 正确，最终路径：/api/auth
  public class LoginController {
      @PostMapping("/login")  // 完整路径：/api/auth/login
  }
  ```
- **命名**：遵循 Spring Boot 标准（camelCase 方法名，PascalCase 类名）
- **详细规范**：参见 `docs/后端开发规范.md`

### Vue3 前端

- **TypeScript**：使用 TypeScript 编写组件
- **组件**：使用 Composition API（`<script setup>`）
- **样式**：使用 Element Plus 组件库
- **格式化**：运行 `npm run lint` 自动修复
- **命名**：
  - 组件文件：`PascalCase.vue`
  - 工具函数：`camelCase`

## 提交规范

### Commit Message 格式

遵循 **Conventional Commits** 规范：

```
<type>(<scope>): <subject>

[optional body]
```

**类型（type）**：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `refactor`: 代码重构
- `style`: 代码格式调整（不影响功能）
- `test`: 测试相关
- `chore`: 构建/工具链相关

**示例**：
```
feat(auth): 添加用户注册和登录时间戳记录

fix(client): 修复历史数据切换后表单未清空的问题

docs: 更新 API 文档和目录结构
```

### Pull Request 要求

1. **分支命名**：`feature/功能名` 或 `bugfix/问题描述`
2. **PR 标题**：遵循 commit message 格式
3. **描述内容**：
   - 简述改动内容
   - 关联的 Issue 编号（如有）
   - 测试方法说明
4. **代码审查**：所有 PR 需经过审查后合并

## CI/CD 流程

项目配置了 GitHub Actions，推送到 `main` 或 `web-LifeLog` 分支时自动执行：

1. 编译 Spring Boot 后端（Java 21 + Maven）
2. 安装前端依赖并构建（Node.js 22 + npm）
3. 构建失败时会阻止合并

详见 `.github/workflows/ci.yml`。

## 配置文件说明

### 敏感信息

- `.env` 文件包含数据库密码、JWT 密钥等，已被 `.gitignore` 忽略
- 参考 `.env.example` 创建本地配置
- **禁止**将 `.env` 文件提交到仓库

### 客户端配置

- `client/config/config.json`：用户配置（开机自启等状态）
- `client/history_data/`：用户历史数据，已被忽略

## 架构说明

项目采用**客户端 + Web 服务器**双端架构：

- **客户端**：Windows 桌面程序，监控本地应用使用时长
- **后端**：Spring Boot + MyBatis + MySQL + Redis，提供 RESTful API 和 WebSocket
- **前端**：Vue3 + Vite + Element Plus，展示数据可视化

WebSocket 连接：`ws://localhost:8080/ws/device?token=xxx`

## 测试指南

- **后端测试**：暂无自动化测试，手动测试 API 接口
- **前端测试**：暂无单元测试，使用 Mock 数据开发调试
- **客户端测试**：手动运行并验证功能

## 注意事项

1. **不创建文档**：除非明确要求，否则不要创建 README、设计文档等（优先修改代码）
2. **Python 版本**：必须使用 Python 3.10+
3. **Java 版本**：必须使用 Java 17+
4. **日志文件**：`server/backend/logs` 已被忽略，禁止提交
5. **单实例运行**：客户端程序同时只允许一个实例运行

## 相关链接

- **项目文档**：查看 `docs/` 目录获取详细设计文档
- **作者博客**：http://primordialblog.publicnote.top/
- **问题反馈**：primordial@qq.com


# 项目编码规范

## 0. 基本开发规范

### 0.1 文件修改通知
每次修改后必须明确列出所有被修改的文件路径。

### 0.2 Commit 整理规范
遵循 Conventional Commits 规范,格式: `<type>(<scope>): <subject>`,直接输出在聊天窗。
每一次修改文件都对我输出一次commit。

### 0.3 变量命名
变量命名时尽量与entity数据库实体的变量名保持一致。如`UserAuthEntity.java`定义了`public String user_email`，则在定义相关变量时，不会命名冲突或产生歧义的情况下，也应命名为`user_email`，而非`email`，这样可以有效预防命名不统一导致后端无法接收前端请求和格式混乱的问题

### 0.4 数据库相关规范
- 数据表的注释用英文编写，尽量言简意赅
- 数据库在改动后需检查相关代码是否需要同步修改。如：`DeregisterServer.java`的删除用户关联数据

### 0.5 git管理规范
每次执行一个大任务前，都要新开一个分支来执行命令。执行这个大任务衍生的小任务和对这个大任务的后续修改，只需要在这个新开的分支上执行即可，无需再次创建新的分支。代码编写完毕后不要自主提交，在我审查完毕后，会自行提交代码并将新开的分支合并到原分支。


## 1. 角色与权限命名规范

角色字符串在整个系统中保持字符格式一致,禁止使用大小写转换方法。

**正确**: 生成token和验证权限时使用相同的 `"ROLE_USER"` 格式

---

## 2. 分层架构职责划分

### 2.1 Controller层
- 接收请求参数、基础校验、调用Service、返回统一响应
- 禁止: 业务逻辑判断、直接调用Mapper、数据处理转换

### 2.2 Service层
- 实现业务逻辑、数据校验判断、调用Mapper、数据转换、异常处理

### 2.3 utils包
- 存放常用工具类。将常用的代码提取为工具类，调用工具类可以提升代码复用率和可读性，并减少代码量。

---

## 3. 数据传输对象规范

### 3.1 VO (View Object)
Service返回给Controller的数据必须封装为VO,位于 `vo` 包,只包含前端需要的字段。

### 3.2 DTO (Data Transfer Object)
Controller传递给Service的复杂参数应封装为DTO,位于 `dto` 包,可包含校验注解。

---

## 4. 安全配置规范

接口权限按角色分组配置,同一角色的所有接口放在一个 `requestMatchers()` 中集中管理。

**配置结构**:
```java
.authorizeHttpRequests(auth -> auth
    // 公开接口
    .requestMatchers("/api/auth/login", "/ws/**").permitAll()
    // ROLE_USER角色专属接口
    .requestMatchers("/api/data/**", "/api/user/**").hasAuthority("ROLE_USER")
    // 其他未匹配的接口
    .anyRequest().denyAll()
)
```

## 5. 代码编写规范
1. 在使用新的包时，先检查maven中是否已经含有该包的依赖。如已存在，则忽略。如不存在，则添加符合当前项目版本环境的maven依赖，并同步依赖到项目。

2. 对于数据库操作代码，一定要考虑数据库操作失败的情况，要求能正确接收异常并做出合适的处理且记录日志。

3. 编写代码前先检查`utils`包内是否有对应工具类，如果有，则直接调用工具类，减少代码量并增加可读性。如果没有，思考该操作是否是一个出现比较频繁的操作，如果是，则提取为工具类。