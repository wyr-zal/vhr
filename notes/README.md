# VHR 项目学习笔记

本目录包含微人事（VHR）项目核心类的学习笔记，按照模块分类整理。

## 目录结构

```
notes/
├── README.md                    # 本文件
├── 00-业务流程图总览.md          # 所有业务流程图汇总
├── 01-security/                 # Spring Security 模块
│   ├── 01-SecurityConfig.md              # 安全核心配置
│   ├── 02-CustomFilterInvocationSecurityMetadataSource.md  # 动态URL权限元数据源
│   ├── 03-CustomUrlDecisionManager.md    # 自定义权限决策管理器
│   └── 04-LoginFilter.md                 # 自定义登录过滤器
├── 02-service/                  # Service 业务层
│   ├── 01-HrService.md                   # HR用户服务（含Security认证）
│   ├── 02-MenuService.md                 # 菜单服务（含Redis缓存）
│   └── 03-EmployeeService.md             # 员工服务（含MQ消息发送）
├── 03-rabbitmq/                 # RabbitMQ 消息队列模块
│   ├── 01-RabbitConfig.md               # MQ配置（队列/交换机/绑定）
│   └── 02-MailReceiver.md               # 邮件消费者
└── 04-websocket/                # WebSocket 实时通信模块
    ├── 01-WebSocketConfig.md            # WebSocket配置
    └── 02-WsController.md               # 消息处理控制器
```

## 笔记模板说明

每篇笔记按照统一模板编写，包含以下部分：

1. **组件定位** - 模块位置、类名、核心技术点
2. **解决了什么问题** - 有/无该组件的对比
3. **生效范围 & 执行时机** - 组件的作用域和触发时机
4. **核心代码/配置解析** - 带详细注释的代码示例
5. **流程图** - ASCII 艺术图解核心逻辑
6. **与其他组件的关系** - 组件协作说明

## 模块概览

### 01-Security 模块

Spring Security 安全框架配置，实现：
- 动态 URL 权限控制
- JSON 格式登录响应
- 验证码校验
- 并发登录控制

**核心流程**：
```
请求 → LoginFilter → HrService认证 → SecurityMetadataSource获取权限 → DecisionManager决策 → 放行/拒绝
```

### 02-Service 模块

业务服务层，核心功能：
- HrService：用户认证 + HR管理
- MenuService：动态菜单 + Redis缓存
- EmployeeService：员工CRUD + MQ消息发送

### 03-RabbitMQ 模块

消息队列实现异步邮件发送：
- RabbitConfig：定义队列/交换机/绑定 + 确认回调
- MailReceiver：消费消息 + 发送邮件 + Redis幂等

**消息流**：
```
新增员工 → MQ消息 → mailserver消费 → 发送邮件
```

### 04-WebSocket 模块

实时聊天功能：
- WebSocketConfig：配置STOMP端点和消息代理
- WsController：处理和转发聊天消息

**通信流**：
```
用户A发送 → WsController处理 → 转发给用户B
```

## 推荐阅读顺序

1. **Security 模块**：先理解权限控制体系
   - SecurityConfig → LoginFilter → CustomFilterInvocationSecurityMetadataSource → CustomUrlDecisionManager

2. **Service 模块**：理解业务逻辑
   - HrService（认证核心）→ MenuService → EmployeeService

3. **RabbitMQ 模块**：理解异步处理
   - RabbitConfig → MailReceiver

4. **WebSocket 模块**：理解实时通信
   - WebSocketConfig → WsController

## 相关文件路径

| 笔记 | 源码路径 |
|------|----------|
| SecurityConfig | `vhr/vhrserver/vhr-web/.../config/SecurityConfig.java` |
| HrService | `vhr/vhrserver/vhr-service/.../service/HrService.java` |
| EmployeeService | `vhr/vhrserver/vhr-service/.../service/EmployeeService.java` |
| RabbitConfig | `vhr/vhrserver/vhr-service/.../config/RabbitConfig.java` |
| MailReceiver | `vhr/mailserver/.../receiver/MailReceiver.java` |
| WebSocketConfig | `vhr/vhrserver/vhr-web/.../config/WebSocketConfig.java` |
