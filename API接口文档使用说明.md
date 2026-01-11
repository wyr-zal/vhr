# VHR人力资源管理系统 API 接口文档

## 一、项目概述

VHR（微人事）是一个前后端分离的人力资源管理系统，基于以下技术栈：
- **后端**: Spring Boot 2.4.0 + Spring Security + MyBatis + MySQL
- **前端**: Vue.js + Element UI
- **中间件**: RabbitMQ + Redis + FastDFS

---

## 二、API接口导入说明

### 2.1 导入到ApiFox

1. 打开ApiFox客户端
2. 选择目标项目或创建新项目
3. 点击左上角 **项目设置** -> **导入数据**
4. 选择 **OpenAPI/Swagger** 格式
5. 上传 `vhr-apifox-import.json` 文件
6. 确认导入即可

### 2.2 配置环境变量

导入后，在ApiFox中配置环境：

| 变量名 | 开发环境值 | 说明 |
|--------|-----------|------|
| baseUrl | http://localhost:8081 | 后端服务地址 |

---

## 三、接口分类汇总

### 3.1 认证管理（4个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| POST | /doLogin | 用户登录 | 无 |
| GET | /login | 登录状态检查 | 无 |
| GET | /verifyCode | 获取验证码 | 无 |
| POST | /logout | 用户登出 | 需登录 |

### 3.2 HR个人信息（4个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /hr/info | 获取当前HR信息 | 需登录 |
| PUT | /hr/info | 更新当前HR信息 | 需登录 |
| PUT | /hr/pass | 修改密码 | 需登录 |
| POST | /hr/userface | 更新头像 | 需登录 |

### 3.3 在线聊天（1个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /chat/hrs | 获取HR列表（聊天用） | 需登录 |

### 3.4 系统配置（1个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /system/config/menu | 获取当前用户菜单 | 需登录 |

### 3.5 员工管理（12个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /employee/basic/ | 分页查询员工 | 需登录+权限 |
| POST | /employee/basic/ | 添加员工 | 需登录+权限 |
| PUT | /employee/basic/ | 更新员工 | 需登录+权限 |
| DELETE | /employee/basic/{id} | 删除员工 | 需登录+权限 |
| GET | /employee/basic/nations | 获取所有民族 | 需登录 |
| GET | /employee/basic/politicsstatus | 获取所有政治面貌 | 需登录 |
| GET | /employee/basic/joblevels | 获取所有职称 | 需登录 |
| GET | /employee/basic/positions | 获取所有职位 | 需登录 |
| GET | /employee/basic/maxWorkID | 获取下一个工号 | 需登录 |
| GET | /employee/basic/deps | 获取所有部门（树结构） | 需登录 |
| GET | /employee/basic/export | 导出员工数据 | 需登录+权限 |
| POST | /employee/basic/import | 导入员工数据 | 需登录+权限 |

### 3.6 薪资账套（4个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /salary/sob/ | 获取所有薪资账套 | 需登录+权限 |
| POST | /salary/sob/ | 添加薪资账套 | 需登录+权限 |
| PUT | /salary/sob/ | 更新薪资账套 | 需登录+权限 |
| DELETE | /salary/sob/{id} | 删除薪资账套 | 需登录+权限 |

### 3.7 员工账套（3个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /salary/sobcfg/ | 分页查询员工薪资配置 | 需登录+权限 |
| GET | /salary/sobcfg/salaries | 获取薪资账套列表 | 需登录 |
| PUT | /salary/sobcfg/ | 更新员工薪资账套 | 需登录+权限 |

### 3.8 HR管理（5个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /system/hr/ | 查询HR列表 | 需登录+权限 |
| PUT | /system/hr/ | 更新HR信息 | 需登录+权限 |
| DELETE | /system/hr/{id} | 删除HR | 需登录+权限 |
| GET | /system/hr/roles | 获取所有角色 | 需登录 |
| PUT | /system/hr/role | 更新HR角色 | 需登录+权限 |

### 3.9 部门管理（3个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /system/basic/department/ | 获取部门树 | 需登录+权限 |
| POST | /system/basic/department/ | 添加部门 | 需登录+权限 |
| DELETE | /system/basic/department/{id} | 删除部门 | 需登录+权限 |

### 3.10 职称管理（5个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /system/basic/joblevel/ | 获取所有职称 | 需登录+权限 |
| POST | /system/basic/joblevel/ | 添加职称 | 需登录+权限 |
| PUT | /system/basic/joblevel/ | 更新职称 | 需登录+权限 |
| DELETE | /system/basic/joblevel/{id} | 删除职称 | 需登录+权限 |
| DELETE | /system/basic/joblevel/ | 批量删除职称 | 需登录+权限 |

### 3.11 权限管理（6个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /system/basic/permiss/ | 获取所有角色 | 需登录+权限 |
| GET | /system/basic/permiss/menus | 获取所有菜单 | 需登录+权限 |
| GET | /system/basic/permiss/mids/{rid} | 获取角色的菜单ID列表 | 需登录+权限 |
| PUT | /system/basic/permiss/ | 更新角色菜单权限 | 需登录+权限 |
| POST | /system/basic/permiss/role | 添加角色 | 需登录+权限 |
| DELETE | /system/basic/permiss/role/{rid} | 删除角色 | 需登录+权限 |

### 3.12 职位管理（5个接口）

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| GET | /system/basic/pos/ | 获取所有职位 | 需登录+权限 |
| POST | /system/basic/pos/ | 添加职位 | 需登录+权限 |
| PUT | /system/basic/pos/ | 更新职位 | 需登录+权限 |
| DELETE | /system/basic/pos/{id} | 删除职位 | 需登录+权限 |
| DELETE | /system/basic/pos/ | 批量删除职位 | 需登录+权限 |

---

## 四、认证与权限说明

### 4.1 认证机制

系统采用 **Spring Security + Session** 认证机制：

1. 调用 `/verifyCode` 获取验证码图片
2. 调用 `/doLogin` 进行登录（POST表单提交）
3. 登录成功后，服务端返回 `JSESSIONID` Cookie
4. 后续请求自动携带该Cookie进行身份验证

### 4.2 登录流程

```
1. GET /verifyCode  -> 获取验证码图片，同时Session中存储验证码文本
2. POST /doLogin    -> 提交 username、password、code
3. 成功返回用户信息，失败返回错误提示
```

### 4.3 权限控制

系统使用 **基于URL的动态权限控制**：
- 每个菜单URL绑定了可访问的角色
- 用户访问接口时，系统自动检查用户角色是否有权限
- 权限配置在数据库 `menu_role` 表中

### 4.4 默认账户

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| admin | 123 | 系统管理员 | 拥有所有权限 |
| libai | 123 | 人事专员 | 部分权限 |
| hanyu | 123 | 招聘主管 | 部分权限 |

---

## 五、测试数据设计说明

### 5.1 测试数据设计原则

1. **符合SQL约束**: 所有测试数据严格遵循数据库字段类型和长度限制
2. **枚举值匹配**: 如性别（男/女）、婚姻状况（已婚/未婚/离异）等使用指定值
3. **关联ID有效**: 外键ID使用数据库中已存在的记录
4. **业务逻辑合理**: 日期逻辑正确（入职日期<转正日期<合同结束日期）

### 5.2 关键字段约束

#### Employee（员工）表
| 字段 | 类型 | 约束 | 示例 |
|------|------|------|------|
| name | VARCHAR(10) | 非空 | 张三 |
| gender | CHAR(4) | 男/女 | 男 |
| idCard | CHAR(18) | 身份证号 | 610122199506151234 |
| phone | VARCHAR(11) | 手机号 | 13812345678 |
| email | VARCHAR(20) | 邮箱 | test@qq.com |
| workID | CHAR(8) | 工号 | 00000102 |
| wedlock | ENUM | 已婚/未婚/离异 | 未婚 |
| engageForm | VARCHAR(8) | 劳动合同/劳务合同 | 劳动合同 |
| tiptopDegree | ENUM | 博士/硕士/本科/大专/高中/初中/小学/其他 | 本科 |
| workState | VARCHAR(8) | 在职/离职 | 在职 |

#### JobLevel（职称）表
| 字段 | 类型 | 约束 | 示例 |
|------|------|------|------|
| name | VARCHAR(32) | 非空 | 高级工程师 |
| titleLevel | ENUM | 正高级/副高级/中级/初级/员级 | 正高级 |

#### Salary（薪资账套）表
| 字段 | 类型 | 约束 | 示例 |
|------|------|------|------|
| basicSalary | INT | 基本工资 | 12000 |
| pensionPer | FLOAT | 0.00-1.00 | 0.07 |
| medicalPer | FLOAT | 0.00-1.00 | 0.02 |
| accumulationFundPer | FLOAT | 0.00-1.00 | 0.12 |

### 5.3 有效ID参考（基于SQL初始化数据）

| 实体 | 有效ID范围 | 说明 |
|------|-----------|------|
| Nation（民族） | 1-56 | 56个民族 |
| Politicsstatus（政治面貌） | 1-13 | 13种政治面貌 |
| Department（部门） | 1-93+ | 树形结构 |
| Position（职位） | 29-34+ | 自增 |
| JobLevel（职称） | 9-14+ | 自增 |
| Salary（薪资账套） | 10-15+ | 自增 |
| Role（角色） | 1-22 | 预置22个角色 |

---

## 六、调试注意事项

### 6.1 环境准备

1. **启动MySQL**: 确保数据库已初始化（执行 vhr.sql）
2. **启动Redis**: 用于Session共享（可选）
3. **启动RabbitMQ**: 用于入职邮件发送（可选）
4. **启动后端服务**: 默认端口8081

### 6.2 常见问题

#### Q: 登录返回401错误
A: 检查验证码是否正确，验证码区分大小写

#### Q: 接口返回"请求失败，请联系管理员"
A: 用户没有该接口的访问权限，需要使用有权限的账户或添加权限

#### Q: 添加员工失败
A: 检查工号是否重复，必填字段是否完整

#### Q: 删除部门失败
A: 部门下有子部门或员工时无法删除，需先处理关联数据

### 6.3 ApiFox调试技巧

1. **自动携带Cookie**: 开启"Cookie自动管理"功能
2. **登录前置脚本**: 可配置前置操作自动登录
3. **环境切换**: 支持开发/测试/生产环境快速切换
4. **Mock数据**: 可配置Mock规则生成测试数据

---

## 七、接口变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| 1.0.0 | 2026-01-05 | 初始版本，包含53个接口 |

---

## 八、文件清单

| 文件名 | 说明 |
|--------|------|
| vhr-apifox-import.json | ApiFox/OpenAPI 3.0导入文件 |
| API接口文档使用说明.md | 本说明文档 |

---

## 九、联系方式

- 项目地址: https://github.com/lenve/vhr
- 作者: 江南一点雨
- 公众号: 江南一点雨
