# 项目编码规范

## 0. 基本开发规范

### 0.1 文件修改通知
每次修改后必须明确列出所有被修改的文件路径。

### 0.2 Commit 整理规范
遵循 Conventional Commits 规范,格式: `<type>(<scope>): <subject>`,直接输出在聊天窗。

---

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

---

## 5. 版本历史

- **2026-07-30**: 初始版本,添加角色命名、分层职责、DTO/VO、安全配置规范
- **2026-07-31**: 新增基本开发规范、Commit整理规范