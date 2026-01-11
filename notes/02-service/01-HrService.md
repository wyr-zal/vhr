# HR用户服务类（HrService）

## 1️⃣ 组件定位

- 模块位置：`vhr-service / service`
- 类名：`HrService`
- 技术点：
  - `UserDetailsService` 接口实现
  - Spring Security 用户认证集成
  - `BCryptPasswordEncoder` 密码加密/验证
  - MyBatis 数据访问
  - 事务管理

> 这是 **HR用户管理的核心服务类**，同时也是 Spring Security 用户认证的数据提供者。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- Spring Security 无法从数据库加载用户信息
- 登录认证无法正常工作
- 用户角色信息无法关联
- 密码校验、修改等操作无法统一处理

### 有了它之后
- 实现 `UserDetailsService`，为 Security 提供用户数据
- 统一管理 HR 用户的 CRUD 操作
- 角色分配、密码修改等业务集中处理
- 自动排除当前登录用户（避免自己管理自己）

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 所有与 HR 用户相关的业务操作
- Spring Security 用户认证

### 执行时机（核心方法）
```text
用户登录请求
       ↓
Spring Security 调用 AuthenticationManager
       ↓
AuthenticationManager 调用 HrService.loadUserByUsername()
       ↓
查询用户基本信息 + 角色信息
       ↓
返回 UserDetails（Hr对象）
       ↓
Security 进行密码比对和状态校验
```

---

## 4️⃣ 核心方法解析

### 4.1 loadUserByUsername - 认证核心方法

```java
@Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // 1. 从数据库查询用户基础信息
    Hr hr = hrMapper.loadUserByUsername(username);

    // 2. 用户不存在抛出异常
    if (hr == null) {
        throw new UsernameNotFoundException("用户名不存在!");
    }

    // 3. 加载用户关联的角色列表
    hr.setRoles(hrMapper.getHrRolesById(hr.getId()));

    // 4. 返回包含角色的 Hr 对象（Hr 实现了 UserDetails 接口）
    return hr;
}
```

**执行流程**：
```text
输入: username = "admin"
        ↓
查询 hr 表: SELECT * FROM hr WHERE username = 'admin'
        ↓
查询角色: SELECT r.* FROM role r
          JOIN hr_role hr ON r.id = hr.rid
          WHERE hr.hrid = #{id}
        ↓
返回: Hr {
    id: 1,
    username: "admin",
    password: "$2a$10$...",  // BCrypt加密
    roles: [ROLE_ADMIN, ROLE_MANAGER]
}
```

### 4.2 updateHrRole - 角色分配（事务）

```java
@Transactional(rollbackFor = Exception.class)
public boolean updateHrRole(Integer hrid, Integer[] rids) {
    // 第一步：删除原有角色
    hrRoleMapper.deleteByHrid(hrid);

    // 第二步：批量添加新角色
    return hrRoleMapper.addRole(hrid, rids) == rids.length;
}
```

**事务保证**：
- 删除成功但新增失败时，自动回滚
- 保证角色数据的完整性

### 4.3 updateHrPasswd - 密码修改

```java
public boolean updateHrPasswd(String oldpass, String pass, Integer hrid) {
    // 1. 查询当前密码（加密后的）
    Hr hr = hrMapper.selectByPrimaryKey(hrid);

    // 2. 验证原密码
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    if (encoder.matches(oldpass, hr.getPassword())) {

        // 3. 加密新密码
        String encodePass = encoder.encode(pass);

        // 4. 更新数据库
        return hrMapper.updatePasswd(hrid, encodePass) == 1;
    }

    return false;  // 原密码错误
}
```

**安全设计**：
- 必须验证原密码才能修改
- 新密码使用 BCrypt 加密存储
- BCrypt 每次加密结果不同（自带随机盐）

---

## 5️⃣ Hr 实体类与 UserDetails

Hr 类必须实现 `UserDetails` 接口：

```java
public class Hr implements UserDetails {

    private Integer id;
    private String username;
    private String password;
    private List<Role> roles;  // 角色列表

    // UserDetails 接口方法
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 将角色列表转换为 GrantedAuthority 列表
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        }
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return enabled; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }

    // getter/setter ...
}
```

---

## 6️⃣ 方法清单

| 方法 | 功能 | 关键点 |
|------|------|--------|
| `loadUserByUsername` | 根据用户名加载用户 | Security 认证核心 |
| `getAllHrs` | 查询所有HR（带搜索） | 排除当前登录用户 |
| `updateHr` | 更新HR信息 | 选择性更新非null字段 |
| `updateHrRole` | 分配角色 | 事务保证 |
| `deleteHrById` | 删除HR | 物理删除 |
| `getAllHrsExceptCurrentHr` | 获取除自己外的所有HR | 用于分配任务等场景 |
| `updateHrPasswd` | 修改密码 | 验证原密码 + BCrypt加密 |
| `updateUserface` | 更新头像URL | |

---

## 7️⃣ 与其他组件的关系

```text
┌─────────────────────────────────────────────────────────┐
│                   认证流程中的 HrService                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  SecurityConfig                                         │
│       ↓                                                 │
│  auth.userDetailsService(hrService)  // 注册           │
│       ↓                                                 │
│  LoginFilter 触发认证                                   │
│       ↓                                                 │
│  AuthenticationManager.authenticate()                   │
│       ↓                                                 │
│  HrService.loadUserByUsername()  ← 自动调用            │
│       ↓                                                 │
│  返回 Hr 对象（含角色）                                 │
│       ↓                                                 │
│  BCryptPasswordEncoder.matches()  // 密码比对          │
│       ↓                                                 │
│  认证成功/失败                                          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 8️⃣ HrUtils 工具类

获取当前登录用户：

```java
public class HrUtils {
    public static Hr getCurrentHr() {
        return (Hr) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
```

使用场景：
- 查询时排除当前用户
- 获取当前用户ID进行权限判断
- 日志记录操作人
