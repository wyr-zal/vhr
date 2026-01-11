# 自定义 URL 权限决策管理器（CustomUrlDecisionManager）

## 1️⃣ 组件定位

- 模块位置：`vhr-web / config`
- 类名：`CustomUrlDecisionManager`
- 技术点：
  - `AccessDecisionManager` 接口
  - 权限决策逻辑
  - `GrantedAuthority` 权限比对
  - 匿名用户检测

> 这是 **Spring Security 的权限决策组件**，负责判断当前用户是否有权限访问目标资源。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- 只能使用 Spring Security 内置的投票器（如 AffirmativeBased）
- 无法实现「ROLE_LOGIN」仅需登录即可访问的逻辑
- 无法自定义权限不足时的异常提示
- 权限判断逻辑不够灵活

### 有了它之后
- 自定义权限判断逻辑
- 支持「仅需登录」和「需要特定角色」两种模式
- 可以返回友好的中文错误提示
- 与 `CustomFilterInvocationSecurityMetadataSource` 完美配合

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 当前 Spring Boot 应用
- 所有被 Spring Security 保护的请求

### 执行时机（重点）
```text
CustomFilterInvocationSecurityMetadataSource.getAttributes()
            ↓
返回当前 URL 所需的角色列表 (configAttributes)
            ↓
CustomUrlDecisionManager.decide()
            ↓
比对用户权限 vs 所需权限
            ↓
通过 → 继续处理请求
拒绝 → 抛出 AccessDeniedException
```

---

## 4️⃣ 最小完整示例

```java
@Component
public class CustomUrlDecisionManager implements AccessDecisionManager {

    /**
     * 核心决策方法：判断当前用户是否有权限访问目标资源
     * @param authentication  当前用户的认证信息（包含用户拥有的权限列表）
     * @param object          被访问的资源（通常是 FilterInvocation，包含请求URL等）
     * @param configAttributes 访问该资源所需的权限列表（由元数据源提供）
     */
    @Override
    public void decide(Authentication authentication, Object object,
                       Collection<ConfigAttribute> configAttributes)
            throws AccessDeniedException, InsufficientAuthenticationException {

        // 遍历访问当前资源所需的所有权限
        for (ConfigAttribute configAttribute : configAttributes) {
            String needRole = configAttribute.getAttribute();

            // 场景1：ROLE_LOGIN 表示只需要登录即可访问
            if ("ROLE_LOGIN".equals(needRole)) {
                // 检查是否为匿名用户（未登录）
                if (authentication instanceof AnonymousAuthenticationToken) {
                    throw new AccessDeniedException("尚未登录，请登录!");
                } else {
                    return;  // 已登录，直接放行
                }
            }

            // 场景2：需要特定角色，遍历用户拥有的权限进行匹配
            Collection<? extends GrantedAuthority> authorities =
                authentication.getAuthorities();
            for (GrantedAuthority authority : authorities) {
                if (authority.getAuthority().equals(needRole)) {
                    return;  // 匹配成功，放行
                }
            }
        }

        // 遍历完所有所需权限，仍无匹配，拒绝访问
        throw new AccessDeniedException("权限不足，请联系管理员!");
    }

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;  // 支持所有权限配置项
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;  // 支持所有资源类型
    }
}
```

---

## 5️⃣ 决策逻辑流程图

```text
┌─────────────────────────────────────────────────────────────┐
│                    decide() 方法                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  输入: configAttributes = ["ROLE_ADMIN", "ROLE_PERSONNEL"]  │
│  用户权限: ["ROLE_PERSONNEL"]                               │
│                                                             │
│  遍历所需权限:                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ needRole = "ROLE_ADMIN"                             │   │
│  │   ↓                                                 │   │
│  │ 是否等于 "ROLE_LOGIN"? → 否                         │   │
│  │   ↓                                                 │   │
│  │ 用户是否拥有 "ROLE_ADMIN"? → 否                      │   │
│  │   ↓                                                 │   │
│  │ 继续下一个...                                        │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ needRole = "ROLE_PERSONNEL"                         │   │
│  │   ↓                                                 │   │
│  │ 是否等于 "ROLE_LOGIN"? → 否                         │   │
│  │   ↓                                                 │   │
│  │ 用户是否拥有 "ROLE_PERSONNEL"? → 是 ✓               │   │
│  │   ↓                                                 │   │
│  │ return; // 放行                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ 两种访问场景

### 场景1：ROLE_LOGIN（仅需登录）

当 `CustomFilterInvocationSecurityMetadataSource` 返回 `ROLE_LOGIN` 时：

```text
用户状态          | 结果
-----------------|------------------
未登录(匿名用户)  | 抛出异常："尚未登录，请登录!"
已登录           | 直接放行
```

### 场景2：需要特定角色

当返回具体角色如 `["ROLE_ADMIN", "ROLE_USER"]` 时：

```text
用户权限                  | 所需权限                    | 结果
-------------------------|---------------------------|--------
["ROLE_ADMIN"]           | ["ROLE_ADMIN","ROLE_USER"] | ✓ 放行
["ROLE_USER"]            | ["ROLE_ADMIN","ROLE_USER"] | ✓ 放行
["ROLE_GUEST"]           | ["ROLE_ADMIN","ROLE_USER"] | ✗ 拒绝
[]                       | ["ROLE_ADMIN","ROLE_USER"] | ✗ 拒绝
```

---

## 7️⃣ 与其他组件的协作关系

```text
┌────────────────────────────────────────────────────────────┐
│                     权限校验完整流程                         │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  1. 用户请求 → /employee/basic/                            │
│                    ↓                                       │
│  2. FilterSecurityInterceptor 拦截                         │
│                    ↓                                       │
│  3. CustomFilterInvocationSecurityMetadataSource           │
│     → 返回 ["ROLE_ADMIN", "ROLE_PERSONNEL"]               │
│                    ↓                                       │
│  4. CustomUrlDecisionManager.decide()                      │
│     → 比对用户权限 ["ROLE_PERSONNEL"]                       │
│     → 匹配成功，放行                                        │
│                    ↓                                       │
│  5. 请求到达 Controller                                    │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 8️⃣ 关键概念说明

| 概念 | 说明 |
|------|------|
| `Authentication` | 当前用户的认证信息，包含用户名、密码（已清空）、权限列表 |
| `ConfigAttribute` | 访问资源所需的权限配置，如 "ROLE_ADMIN" |
| `GrantedAuthority` | 用户拥有的权限/角色 |
| `AnonymousAuthenticationToken` | 匿名用户的认证令牌（未登录状态） |
| `AccessDeniedException` | 访问被拒绝异常，会被 Security 异常处理器捕获 |
