# 自定义登录过滤器（LoginFilter）

## 1️⃣ 组件定位

- 模块位置：`vhr-web / config`
- 类名：`LoginFilter`
- 技术点：
  - `UsernamePasswordAuthenticationFilter` 扩展
  - JSON 格式登录参数解析
  - 验证码校验
  - `SessionRegistry` 会话注册
  - 前后端分离认证

> 这是 **自定义的登录认证过滤器**，扩展了 Spring Security 默认的用户名密码认证，支持 JSON 登录和验证码校验。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- 只能使用表单提交（form-data）方式登录
- 无法在登录时校验验证码
- 前后端分离项目的 JSON 登录参数无法解析
- 无法在登录时注册会话（并发登录控制失效）

### 有了它之后
- 同时支持 JSON 和表单两种登录方式
- 登录时自动校验验证码
- 与 SessionRegistry 集成，支持并发登录控制
- 可自定义登录接口地址

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 仅作用于登录接口 `/doLogin`
- 替换默认的 `UsernamePasswordAuthenticationFilter`

### 执行时机（重点）
```text
用户发起登录请求 POST /doLogin
            ↓
LoginFilter.attemptAuthentication()
            ↓
1. 校验请求方法（必须POST）
            ↓
2. 获取Session中的验证码
            ↓
3. 判断请求类型（JSON / 表单）
            ↓
4. 解析用户名、密码、验证码
            ↓
5. 校验验证码
            ↓
6. 构建 UsernamePasswordAuthenticationToken
            ↓
7. 注册会话到 SessionRegistry
            ↓
8. 交给 AuthenticationManager 认证
```

---

## 4️⃣ 最小完整示例

```java
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    @Autowired
    SessionRegistry sessionRegistry;  // 会话注册表，用于并发登录控制

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                 HttpServletResponse response)
            throws AuthenticationException {

        // 1. 仅支持 POST 请求
        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException(
                "Authentication method not supported: " + request.getMethod());
        }

        // 2. 获取 Session 中的验证码（由验证码接口生成并存入）
        String verify_code = (String) request.getSession().getAttribute("verify_code");

        // 3. 判断请求内容类型：JSON 或 表单
        if (request.getContentType().contains(MediaType.APPLICATION_JSON_VALUE)) {

            // ========== JSON 格式登录 ==========
            Map<String, String> loginData = new HashMap<>();
            try {
                // 从请求体读取 JSON 并解析
                loginData = new ObjectMapper().readValue(
                    request.getInputStream(), Map.class);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // 校验验证码
                String code = loginData.get("code");
                checkCode(response, code, verify_code);
            }

            // 提取用户名和密码
            String username = loginData.get(getUsernameParameter());
            String password = loginData.get(getPasswordParameter());

            // 空值处理
            username = (username != null) ? username.trim() : "";
            password = (password != null) ? password : "";

            // 构建未认证的令牌
            UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(username, password);
            setDetails(request, authRequest);

            // 注册会话（用于并发登录控制）
            Hr principal = new Hr();
            principal.setUsername(username);
            sessionRegistry.registerNewSession(
                request.getSession(true).getId(), principal);

            // 交给 AuthenticationManager 认证
            return this.getAuthenticationManager().authenticate(authRequest);

        } else {
            // ========== 表单格式登录 ==========
            checkCode(response, request.getParameter("code"), verify_code);
            return super.attemptAuthentication(request, response);
        }
    }

    /**
     * 验证码校验
     */
    public void checkCode(HttpServletResponse resp, String code, String verify_code) {
        // 开发环境跳过（输入 "dev" 即可绕过）
        if (code != null && "dev".equalsIgnoreCase(code)) return;

        // 校验验证码（忽略大小写）
        if (code == null || verify_code == null || "".equals(code) ||
                !verify_code.toLowerCase().equals(code.toLowerCase())) {
            throw new AuthenticationServiceException("验证码不正确");
        }
    }
}
```

---

## 5️⃣ 登录流程图

```text
┌─────────────────────────────────────────────────────────────┐
│                      登录请求处理流程                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  前端发起请求                                                │
│  POST /doLogin                                              │
│  Content-Type: application/json                             │
│  Body: {"username":"admin","password":"123","code":"abcd"}  │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 1. 校验请求方法                                      │   │
│  │    request.getMethod() == "POST" ?                  │   │
│  │    否 → 抛出异常                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 2. 获取 Session 验证码                               │   │
│  │    verify_code = session.getAttribute("verify_code") │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 3. 解析 JSON 请求体                                  │   │
│  │    loginData = ObjectMapper.readValue(...)          │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 4. 校验验证码                                        │   │
│  │    code vs verify_code (忽略大小写)                  │   │
│  │    不匹配 → 抛出 "验证码不正确"                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 5. 构建认证令牌并提交认证                            │   │
│  │    authManager.authenticate(authRequest)            │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  认证成功 → SecurityConfig 中的 successHandler 处理         │
│  认证失败 → SecurityConfig 中的 failureHandler 处理         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ 请求格式对比

### JSON 格式（前后端分离推荐）

```http
POST /doLogin HTTP/1.1
Content-Type: application/json

{
    "username": "admin",
    "password": "123456",
    "code": "abcd"
}
```

### 表单格式（传统方式）

```http
POST /doLogin HTTP/1.1
Content-Type: application/x-www-form-urlencoded

username=admin&password=123456&code=abcd
```

---

## 7️⃣ 验证码机制说明

```text
┌──────────────────────────────────────────────────────────┐
│                    验证码流程                             │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  1. 前端请求验证码图片                                    │
│     GET /verifyCode                                      │
│                    ↓                                     │
│  2. 后端生成验证码，存入 Session                          │
│     session.setAttribute("verify_code", "abcd")          │
│                    ↓                                     │
│  3. 返回验证码图片给前端                                  │
│                    ↓                                     │
│  4. 用户登录时提交验证码                                  │
│     {"code": "abcd", ...}                                │
│                    ↓                                     │
│  5. LoginFilter 比对验证码                               │
│     输入的 code vs Session 中的 verify_code              │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 8️⃣ 与 SecurityConfig 的关系

在 `SecurityConfig` 中注册 LoginFilter：

```java
@Bean
LoginFilter loginFilter() throws Exception {
    LoginFilter loginFilter = new LoginFilter();

    // 设置成功/失败处理器
    loginFilter.setAuthenticationSuccessHandler(...);
    loginFilter.setAuthenticationFailureHandler(...);

    // 设置认证管理器
    loginFilter.setAuthenticationManager(authenticationManagerBean());

    // 设置登录接口地址
    loginFilter.setFilterProcessesUrl("/doLogin");

    // 设置会话策略（并发登录控制）
    loginFilter.setSessionAuthenticationStrategy(...);

    return loginFilter;
}

// 替换默认的 UsernamePasswordAuthenticationFilter
http.addFilterAt(loginFilter(), UsernamePasswordAuthenticationFilter.class);
```

---

## 9️⃣ 开发调试技巧

验证码跳过机制（仅开发环境）：

```java
// 输入 code=dev 可以跳过验证码校验
if (code != null && "dev".equalsIgnoreCase(code)) return;
```

**注意**：生产环境务必删除此代码！
