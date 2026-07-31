# 项目编码规范

本文档记录了项目开发过程中需要遵循的编码规范和设计原则。

## 0. 基本开发规范

### 0.1 文件修改通知规范

**规则**: 每次对项目文件进行修改后,必须明确告知修改了哪些文件。

**说明**:
- 在完成代码修改或文档更新后,需要列出所有被修改的文件路径
- 帮助团队成员了解变更范围
- 便于代码审查和版本控制
- 提高协作效率

**示例**:
```
修改的文件:
1. src/main/java/controller/UserController.java
2. src/main/java/service/UserService.java
3. docs/API文档.md
```

### 0.2 Commit 文档整理规范

**规则**: 当用户要求整理 commit 文档时,应遵循以下原则:

1. **尽量简短**: commit 信息应简洁明了,避免冗长描述
2. **符合 commit 规范**: 遵循 Conventional Commits 规范
   - 格式: `<type>(<scope>): <subject>`
   - 常用 type: `feat`(新功能), `fix`(修复), `docs`(文档), `refactor`(重构), `style`(格式), `test`(测试)
3. **直接输出在聊天窗**: 将 commit 信息直接输出在对话中,而不是生成独立的文档文件

**输出格式**:
```
feat(auth): 添加用户注册和登录时间戳记录

- 新增 created_at 和 last_login_at 字段
- 注册时记录创建时间
- 登录时更新最后登录时间
```

---

## 1. 角色与权限命名规范

### 1.1 角色字符一致性原则

**规则**: 在定义和使用角色时,应在整个系统中保持字符格式一致,不应使用大小写转换方法。

**说明**: 
- 角色字符串在生成JWT token时的格式,应与验证权限时使用的格式完全一致
- 避免使用 `toUpperCase()`, `toLowerCase()` 等方法进行字符转换
- 允许使用字符串拼接(如添加前缀),但原始角色字符应保持不变

**正确示例**:
```java
// 生成token时
String token = jwtUtil.generateWithRole(email, "ROLE_USER");

// 验证权限时
authorities = Collections.singletonList(
    new SimpleGrantedAuthority("ROLE_USER")
);
```

**错误示例**:
```java
// 生成token时使用小写
String token = jwtUtil.generateWithRole(email, "user");

// 验证时转换为大写
new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
```

**优势**:
- 减少因大小写转换导致的权限匹配错误
- 提高代码可维护性和可读性
- 便于调试和追踪权限问题

---

## 2. 分层架构职责划分

### 2.1 Controller层职责

**规则**: Controller层只负责接收请求参数和调用Service层,不应包含任何业务逻辑判断。

**职责范围**:
- 接收HTTP请求参数
- 参数基础校验(如非空校验,可通过注解实现)
- 调用Service层方法
- 返回统一的响应格式

**禁止行为**:
- 不得进行业务逻辑判断
- 不得直接调用Mapper层
- 不得进行数据处理和转换(应由Service层完成)
- 不得进行复杂的条件判断

**正确示例**:
```java
@GetMapping("/verify-key")
public Result verifyKey(@RequestParam("key") String key) {
    VerifyKeyVO vo = verifyKeyService.verifyKeyAndGenerateToken(key);
    return Result.success("秘钥有效", vo);
}
```

**错误示例**:
```java
@GetMapping("/verify-key")
public Result verifyKey(@RequestParam("key") String key) {
    // ❌ 在Controller中进行业务判断
    UserAuthEntity user = userAuthMapper.selectByKey(key);
    if (user == null) {
        return Result.error(401, "秘钥无效或已作废", null);
    }
    // ❌ 在Controller中进行数据处理
    String token = verifyKeyService.verifyKeyAndGenerateToken(key);
    Map<String, Object> data = new HashMap<>();
    data.put("userName", user.getUser_name());
    data.put("token", token);
    return Result.success("秘钥有效", data);
}
```

### 2.2 Service层职责

**职责范围**:
- 实现业务逻辑
- 进行数据校验和判断
- 调用Mapper层进行数据操作
- 进行数据转换(Entity → VO, DTO → Entity)
- 处理异常并抛出有意义的业务异常

---

## 3. 数据传输对象规范

### 3.1 VO (View Object) 使用规范

**规则**: 从Service层返回给Controller层,最终传递给前端的数据,必须封装为VO类。

**说明**:
- VO类位于 `vo` 包中
- VO类只包含前端需要的字段
- VO类字段命名应符合前端约定
- 避免直接返回Entity或使用Map

**示例**:
```java
// VO类定义
@Data
public class VerifyKeyVO {
    private String userName;
    private String token;
}

// Service层返回VO
public VerifyKeyVO verifyKey(String secretKey) {
    // 业务逻辑...
    VerifyKeyVO vo = new VerifyKeyVO();
    vo.setUserName(user.getUser_name());
    vo.setToken(token);
    return vo;
}
```

### 3.2 DTO (Data Transfer Object) 使用规范

**规则**: 从Controller层传递给Service层的复杂参数,应封装为DTO类。

**说明**:
- DTO类位于 `dto` 包中
- DTO类用于接收和传递请求参数
- 可以包含校验注解(如 `@NotNull`, `@Email` 等)

---

## 4. 安全配置规范

### 4.1 接口权限配置规范

**规则**: 在SecurityConfig中配置接口权限时,应按角色分组配置,同一角色的所有接口应放在一个 `requestMatchers()` 中集中管理。

**核心原则**:
1. **按角色分组**: 属于同一角色的所有接口必须放在同一个 `requestMatchers()` 配置块中
2. **明确列出**: 不使用 `anyRequest()` 进行统一配置(除了最后的 `denyAll()`)
3. **分类清晰**: 使用注释明确标注每个配置块的用途

**配置结构**:
```java
.authorizeHttpRequests(auth -> auth
    // ========== 公开接口(无需认证) ==========
    .requestMatchers(
        "/api/auth/login",
        "/api/auth/register",
        "/ws/**"
    ).permitAll()

    // ========== ROLE_USER角色专属接口 ==========
    .requestMatchers(
        "/api/auth/logout",
        "/api/data/**",
        "/home",
        "/api/user/**"
    ).hasAuthority("ROLE_USER")

    // ========== ROLE_VISITOR角色专属接口 ==========
    .requestMatchers(
        "/api/visitor/**"
    ).hasAuthority("ROLE_VISITOR")
    
    // ========== 多角色共享接口 ==========
    .requestMatchers(
        "/api/public/**"
    ).hasAnyAuthority("ROLE_USER", "ROLE_VISITOR")

    // ========== 其他未匹配的接口 ==========
    .anyRequest().denyAll()
)
```

**正确示例**:
```java
// ✅ 同一角色的接口集中在一起
.requestMatchers(
    "/api/auth/logout",
    "/api/data/**",
    "/home",
    "/api/user/**"
).hasAuthority("ROLE_USER")
```

**错误示例**:
```java
// ❌ 同一角色的接口分散配置
.requestMatchers("/api/auth/logout").hasAuthority("ROLE_USER")
.requestMatchers("/api/data/**").hasAuthority("ROLE_USER")
.requestMatchers("/home", "/api/user/**").hasAuthority("ROLE_USER")
```

**优势**:
- 一眼就能看出某个角色能访问哪些接口
- 新增接口时只需在对应角色块中添加
- 避免遗漏或重复配置
- 便于代码审查和权限审计

---

## 5. 版本历史

- **2026-07-30**: 初始版本,添加角色命名规范、分层职责规范、数据传输对象规范、安全配置规范
- **2026-07-30**: 更新安全配置规范,强调按角色分组配置接口权限
- **2026-07-30**: 新增基本开发规范,要求每次修改后明确告知修改的文件列表
- **2026-07-31**: 新增 Commit 文档整理规范,要求简短、符合规范、直接输出在聊天窗
