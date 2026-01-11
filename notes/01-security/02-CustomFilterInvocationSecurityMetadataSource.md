# 动态 URL 权限元数据源（CustomFilterInvocationSecurityMetadataSource）

## 1️⃣ 组件定位

- 模块位置：`vhr-web / config`
- 类名：`CustomFilterInvocationSecurityMetadataSource`
- 技术点：
  - `FilterInvocationSecurityMetadataSource`
  - URL → 角色 动态映射
  - `AntPathMatcher`
  - 数据库驱动的权限模型

> 这是一个 **Spring Security 中"根据请求 URL 解析所需角色"的权限元数据提供组件**。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- 权限规则只能写死在 Security 配置中
- URL 与角色强耦合在代码里
- 菜单 / 权限变更必须重启服务
- 无法实现「菜单 + 角色」动态权限控制

### 有了它之后
- URL 对应的访问角色来自数据库
- 支持通配路径（如 `/admin/**`）
- 权限规则可动态调整
- 为权限决策提供准确的数据来源

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 当前 Spring Boot 应用
- 所有被 Spring Security 保护的请求

### 执行时机（重点）
```text
请求进入 Spring Security 过滤器链
            ↓
FilterSecurityInterceptor
            ↓
CustomFilterInvocationSecurityMetadataSource.getAttributes()
            ↓
解析出该 URL 所需的角色列表
            ↓
交给 AccessDecisionManager 决策
```

---

## 4️⃣ 最小完整示例

```java
@Component
public class CustomFilterInvocationSecurityMetadataSource
        implements FilterInvocationSecurityMetadataSource {

    // 访问数据库，获取菜单与角色的对应关系
    @Autowired
    MenuService menuService;

    // 用于匹配请求 URL 和菜单中配置的 URL（支持 /xx/**）
    AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 根据当前请求 URL，解析出访问该资源所需的角色列表
     */
    @Override
    public Collection<ConfigAttribute> getAttributes(Object object) {
        // object 实际类型是 FilterInvocation，封装了当前 HTTP 请求
        FilterInvocation fi = (FilterInvocation) object;

        // 获取当前请求的 URL（不包含域名）
        String requestUrl = fi.getRequestUrl();

        // 从数据库中查询所有菜单及其对应的角色
        List<Menu> menus = menuService.getAllMenusWithRole();

        // 遍历菜单，查找与当前请求 URL 匹配的配置
        for (Menu menu : menus) {
            // 判断请求 URL 是否符合菜单中配置的 URL 规则
            if (antPathMatcher.match(menu.getUrl(), requestUrl)) {

                // 提取该菜单所允许访问的角色名称
                String[] roles = menu.getRoles()
                        .stream()
                        .map(Role::getName)
                        .toArray(String[]::new);

                // 将角色列表封装为 Spring Security 识别的权限对象
                return SecurityConfig.createList(roles);
            }
        }

        // 如果没有任何菜单匹配该 URL：
        // 返回 ROLE_LOGIN，表示"只要登录即可访问"
        return SecurityConfig.createList("ROLE_LOGIN");
    }

    /**
     * Spring Security 会先调用 supports 判断该类是否支持当前类型
     * 这里统一返回 true，表示支持所有受保护资源
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}
```

---

## 5️⃣ 核心逻辑流程图

```text
┌──────────────────────────────────────────────────────────┐
│              getAttributes(Object object)                │
├──────────────────────────────────────────────────────────┤
│  1. 获取请求URL: /employee/basic/                        │
│                     ↓                                    │
│  2. 查询数据库所有菜单及角色关系                          │
│     menus = menuService.getAllMenusWithRole()            │
│                     ↓                                    │
│  3. 遍历菜单，AntPathMatcher 匹配URL                      │
│     /employee/**  ←→  /employee/basic/  ✓ 匹配          │
│                     ↓                                    │
│  4. 返回该菜单关联的角色列表                              │
│     return ["ROLE_ADMIN", "ROLE_PERSONNEL"]             │
│                     ↓                                    │
│  5. 若无匹配，返回 ROLE_LOGIN（仅需登录）                 │
└──────────────────────────────────────────────────────────┘
```

---

## 6️⃣ 关键点说明

### AntPathMatcher 匹配规则

| 模式 | 说明 | 示例 |
|------|------|------|
| `?` | 匹配单个字符 | `/user/?` 匹配 `/user/a` |
| `*` | 匹配0个或多个字符（不含路径分隔符） | `/user/*` 匹配 `/user/abc` |
| `**` | 匹配0个或多个路径段 | `/user/**` 匹配 `/user/a/b/c` |

### 返回值含义

| 返回值 | 含义 |
|--------|------|
| `["ROLE_ADMIN"]` | 只有 ROLE_ADMIN 角色可访问 |
| `["ROLE_ADMIN", "ROLE_USER"]` | ROLE_ADMIN 或 ROLE_USER 均可访问 |
| `["ROLE_LOGIN"]` | 只需登录即可访问（自定义标识） |

---

## 7️⃣ 与其他组件的协作

```text
                    请求进入
                       ↓
    CustomFilterInvocationSecurityMetadataSource
         ↓ 返回所需角色列表
    CustomUrlDecisionManager.decide()
         ↓ 比对用户角色
    允许访问 / 抛出 AccessDeniedException
```
