# 菜单服务类（MenuService）

## 1️⃣ 组件定位

- 模块位置：`vhr-service / service`
- 类名：`MenuService`
- 技术点：
  - Spring Cache 缓存（`@Cacheable`）
  - Redis 缓存存储
  - 动态菜单查询
  - 事务管理

> 这是 **菜单管理的核心服务类**，负责提供动态菜单数据和菜单-角色关系管理。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- 每次权限校验都要查询数据库
- 菜单数据无法缓存，性能低下
- 动态菜单功能无法实现
- 菜单与角色的关系无法统一管理

### 有了它之后
- 菜单数据缓存到 Redis，提升性能
- 为前端提供动态菜单数据
- 为权限校验提供 URL-角色 映射
- 统一管理菜单-角色关系

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 前端动态菜单加载
- 权限校验（`CustomFilterInvocationSecurityMetadataSource`）

### 缓存配置
```java
@Service
@CacheConfig(cacheNames = "menus_cache")  // 缓存名称
public class MenuService {
    // ...
}
```

---

## 4️⃣ 核心方法解析

### 4.1 getMenusByHrId - 获取用户菜单

```java
public List<Menu> getMenusByHrId() {
    // 从 Security 上下文获取当前登录用户
    Hr hr = (Hr) SecurityContextHolder.getContext()
            .getAuthentication()
            .getPrincipal();

    // 根据用户ID查询其有权限访问的菜单
    return menuMapper.getMenusByHrId(hr.getId());
}
```

**使用场景**：前端获取动态菜单（`GET /system/config/menu`）

**查询逻辑**（MenuMapper.xml）：
```sql
-- 根据用户ID → 用户角色 → 角色菜单 → 菜单列表
SELECT DISTINCT m.*
FROM menu m
JOIN menu_role mr ON m.id = mr.mid
JOIN hr_role hr ON mr.rid = hr.rid
WHERE hr.hrid = #{hrId}
  AND m.enabled = true
ORDER BY m.id
```

### 4.2 getAllMenusWithRole - 获取所有菜单（带缓存）

```java
@Cacheable  // 自动缓存，key默认为方法名
public List<Menu> getAllMenusWithRole() {
    return menuMapper.getAllMenusWithRole();
}
```

**使用场景**：权限校验时获取 URL-角色 映射

**缓存机制**：
```text
第一次调用
    ↓
查询数据库
    ↓
结果存入 Redis (key: menus_cache::getAllMenusWithRole)
    ↓
后续调用直接从缓存获取

数据结构:
[
    Menu {
        url: "/employee/**",
        roles: [ROLE_ADMIN, ROLE_PERSONNEL]
    },
    Menu {
        url: "/salary/**",
        roles: [ROLE_ADMIN, ROLE_FINANCE]
    }
    ...
]
```

### 4.3 updateMenuRole - 更新菜单角色关系

```java
@Transactional
public boolean updateMenuRole(Integer rid, Integer[] mids) {
    // 1. 删除该角色原有的所有菜单权限
    menuRoleMapper.deleteByRid(rid);

    // 2. 如果没有新菜单，直接返回（清空权限）
    if (mids == null || mids.length == 0) {
        return true;
    }

    // 3. 批量添加新的菜单权限
    Integer result = menuRoleMapper.insertRecord(rid, mids);
    return result == mids.length;
}
```

**事务保证**：删除和新增操作原子性执行

---

## 5️⃣ 缓存配置（application.yml）

```yaml
spring:
  cache:
    cache-names: menus_cache  # 缓存名称
  redis:
    host: 192.168.100.128
    port: 6379
    database: 1
```

---

## 6️⃣ 菜单数据结构

```java
public class Menu {
    private Integer id;
    private String url;        // 菜单对应的URL规则（如 /employee/**）
    private String path;       // 前端路由路径
    private String component;  // 前端组件名
    private String name;       // 菜单名称
    private String iconCls;    // 图标样式
    private Integer parentId;  // 父菜单ID（支持多级）
    private Boolean enabled;   // 是否启用
    private List<Role> roles;  // 关联的角色列表
    private List<Menu> children; // 子菜单
}
```

---

## 7️⃣ 前后端菜单交互流程

```text
┌─────────────────────────────────────────────────────────────┐
│                    动态菜单加载流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 用户登录成功                                            │
│                       ↓                                     │
│  2. 前端 main.js 路由守卫检测到已登录                        │
│                       ↓                                     │
│  3. 调用 initMenu() 方法 (menus.js)                         │
│                       ↓                                     │
│  4. 发起请求: GET /system/config/menu                       │
│                       ↓                                     │
│  5. 后端 MenuService.getMenusByHrId()                       │
│     → 查询当前用户有权限的菜单                               │
│                       ↓                                     │
│  6. 返回菜单树形结构                                        │
│     [{ name:"员工管理", path:"/emp", children:[...] }, ...]  │
│                       ↓                                     │
│  7. 前端 formatRoutes() 将菜单转为 Vue Router 格式           │
│                       ↓                                     │
│  8. router.addRoutes() 动态添加路由                          │
│                       ↓                                     │
│  9. store.commit('initRoutes') 保存到 Vuex                  │
│                       ↓                                     │
│  10. Home.vue 读取 $store.state.routes 渲染侧边栏菜单        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 8️⃣ 与权限校验的关系

```text
┌────────────────────────────────────────────────────────────┐
│               权限校验中的 MenuService                      │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  请求: GET /employee/basic/                                │
│                    ↓                                       │
│  CustomFilterInvocationSecurityMetadataSource              │
│                    ↓                                       │
│  menuService.getAllMenusWithRole()  ← 从缓存获取           │
│                    ↓                                       │
│  返回所有菜单及其角色关系                                   │
│  [                                                         │
│    { url: "/employee/**", roles: [ROLE_ADMIN, ROLE_HR] },  │
│    { url: "/salary/**", roles: [ROLE_ADMIN, ROLE_FINANCE]} │
│  ]                                                         │
│                    ↓                                       │
│  AntPathMatcher 匹配 /employee/** ← /employee/basic/       │
│                    ↓                                       │
│  返回 ["ROLE_ADMIN", "ROLE_HR"]                            │
│                    ↓                                       │
│  CustomUrlDecisionManager 决策                             │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 9️⃣ 方法清单

| 方法 | 功能 | 缓存 |
|------|------|------|
| `getMenusByHrId` | 获取当前用户的菜单 | 无 |
| `getAllMenusWithRole` | 获取所有菜单（含角色） | ✓ Redis |
| `getAllMenus` | 获取所有菜单 | 无 |
| `getMidsByRid` | 获取角色拥有的菜单ID | 无 |
| `updateMenuRole` | 更新角色的菜单权限 | 无（需手动清缓存） |

---

## 🔟 注意事项

### 缓存更新
当菜单-角色关系发生变化时，需要手动清除缓存：

```java
@Autowired
CacheManager cacheManager;

public void clearMenuCache() {
    Cache cache = cacheManager.getCache("menus_cache");
    if (cache != null) {
        cache.clear();
    }
}
```

否则权限校验会使用旧数据！
