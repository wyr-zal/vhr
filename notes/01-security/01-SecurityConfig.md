# Spring Security 核心配置类（SecurityConfig）

## 1️⃣ 组件定位

- 模块位置：`vhr-web / config`
- 类名：`SecurityConfig`
- 技术点：
  - `WebSecurityConfigurerAdapter`
  - `BCryptPasswordEncoder` 密码加密
  - `SessionRegistry` 会话管理
  - `ObjectPostProcessor` 自定义权限组件注入
  - JSON格式的认证响应

> 这是 **Spring Security 的核心配置类**，负责整合认证、授权、会话管理、自定义登录/登出等所有安全相关配置。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- 无法自定义认证流程（如JSON登录）
- 登录成功/失败只能跳转页面，无法返回JSON
- 无法集成自定义的权限决策组件
- 无法控制并发登录（同一账号多端登录）
- 静态资源也会被安全拦截

### 有了它之后
- 前后端分离项目可以返回JSON格式响应
- 集成自定义的 `CustomFilterInvocationSecurityMetadataSource` 和 `CustomUrlDecisionManager`
- 支持验证码校验、并发登录控制
- 静态资源和验证码接口可以放行
- 统一的异常处理机制

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 当前 Spring Boot 应用
- 所有 HTTP 请求（除放行的静态资源外）

### 执行时机（重点）
```text
Spring 容器启动
        ↓
@Configuration 加载 SecurityConfig
        ↓
注册各种 Bean（PasswordEncoder、LoginFilter、SessionRegistry）
        ↓
configure(WebSecurity) → 配置放行路径
        ↓
configure(HttpSecurity) → 配置安全规则、过滤器链
        ↓
HTTP 请求进入 → 按配置的过滤器链依次处理
```

---

## 4️⃣ 核心配置解析

### 4.1 密码加密器配置

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

- **BCrypt 特点**：自带随机盐值，同一明文每次加密结果不同
- **使用场景**：注册时加密存储、登录时验证密码

### 4.2 认证管理器配置

```java
@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(hrService);
}
```

- 指定 `HrService` 作为用户详情服务
- Spring Security 会调用 `hrService.loadUserByUsername()` 加载用户信息

### 4.3 静态资源放行

```java
@Override
public void configure(WebSecurity web) throws Exception {
    web.ignoring().antMatchers(
        "/css/**", "/js/**", "/index.html",
        "/img/**", "/fonts/**", "/favicon.ico", "/verifyCode"
    );
}
```

- 这些路径不经过 Security 过滤器链
- 验证码接口 `/verifyCode` 必须放行，否则未登录无法获取

### 4.4 自定义权限组件注入

```java
.authorizeRequests()
.withObjectPostProcessor(new ObjectPostProcessor<FilterSecurityInterceptor>() {
    @Override
    public <O extends FilterSecurityInterceptor> O postProcess(O object) {
        // 注入自定义权限决策管理器
        object.setAccessDecisionManager(customUrlDecisionManager);
        // 注入自定义权限元数据源
        object.setSecurityMetadataSource(customFilterInvocationSecurityMetadataSource);
        return object;
    }
})
```

- 通过 `ObjectPostProcessor` 将自定义组件注入到 `FilterSecurityInterceptor`
- 实现动态 URL 权限控制

### 4.5 登录成功/失败处理（JSON响应）

```java
// 登录成功
loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
    response.setContentType("application/json;charset=utf-8");
    Hr hr = (Hr) authentication.getPrincipal();
    hr.setPassword(null);  // 清空密码，避免泄露
    RespBean ok = RespBean.ok("登录成功!", hr);
    out.write(new ObjectMapper().writeValueAsString(ok));
});

// 登录失败（区分异常类型）
loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
    RespBean respBean = RespBean.error(exception.getMessage());
    if (exception instanceof LockedException) {
        respBean.setMsg("账户被锁定，请联系管理员!");
    } else if (exception instanceof BadCredentialsException) {
        respBean.setMsg("用户名或者密码输入错误，请重新输入!");
    }
    // ... 其他异常处理
});
```

### 4.6 并发会话控制

```java
// 会话注册表
@Bean
SessionRegistryImpl sessionRegistry() {
    return new SessionRegistryImpl();
}

// 并发会话策略：限制同一用户最多1个会话
ConcurrentSessionControlAuthenticationStrategy sessionStrategy =
    new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry());
sessionStrategy.setMaximumSessions(1);

// 并发会话过滤器：已在其他设备登录时踢出
http.addFilterAt(new ConcurrentSessionFilter(sessionRegistry(), event -> {
    // 返回 "您已在另一台设备登录，本次登录已下线!" 提示
}), ConcurrentSessionFilter.class);
```

---

## 5️⃣ 完整配置流程图

```text
┌─────────────────────────────────────────────────────────────┐
│                    SecurityConfig                           │
├─────────────────────────────────────────────────────────────┤
│  1. PasswordEncoder        → BCryptPasswordEncoder          │
│  2. UserDetailsService     → HrService                      │
│  3. 静态资源放行            → /css/**, /js/**, /verifyCode  │
│  4. 权限元数据源            → CustomFilterInvocation...     │
│  5. 权限决策管理器          → CustomUrlDecisionManager      │
│  6. 登录过滤器              → LoginFilter (JSON登录)        │
│  7. 会话管理                → SessionRegistry + 并发控制    │
│  8. 异常处理                → 401 JSON响应                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ 关键依赖关系

| 组件 | 依赖 | 作用 |
|------|------|------|
| SecurityConfig | HrService | 提供用户认证数据 |
| SecurityConfig | CustomFilterInvocationSecurityMetadataSource | 获取URL所需权限 |
| SecurityConfig | CustomUrlDecisionManager | 判断用户是否有权限 |
| LoginFilter | SessionRegistry | 管理用户会话 |
