# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

微人事 (VHR) 是一个前后端分离的人力资源管理系统，采用 **Spring Boot 2.4.0 + Vue 2 + Element UI** 技术栈。

## 项目结构

```
vhr/
├── vhr/                    # 后端 (Maven多模块)
│   ├── vhrserver/          # 核心服务
│   │   ├── vhr-web/        # Web控制层 (端口8081)
│   │   ├── vhr-service/    # 业务服务层
│   │   ├── vhr-mapper/     # MyBatis数据访问层
│   │   └── vhr-model/      # 实体模型
│   └── mailserver/         # 邮件微服务 (端口8082，RabbitMQ消费者)
├── vuehr/                  # 前端 Vue项目 (端口8080)
└── vhr.sql                 # 数据库脚本
```

**模块依赖链**: vhr-web → vhr-service → vhr-mapper → vhr-model

## 常用命令

### 后端构建与运行
```bash
# 构建整个后端项目
cd vhr && mvn clean package -DskipTests

# 运行主服务 (vhr-web)
cd vhr/vhrserver/vhr-web && mvn spring-boot:run

# 运行邮件服务
cd vhr/mailserver && mvn spring-boot:run
```

### 前端构建与运行
```bash
cd vuehr

# 安装依赖
npm install

# 开发服务器 (http://localhost:8080，自动代理到后端8081)
npm run serve

# 生产构建
npm run build
```

## 核心架构要点

### 权限系统 (Spring Security)
- **SecurityConfig.java**: 主配置类
- **CustomFilterInvocationSecurityMetadataSource**: 从数据库动态获取URL所需权限
- **CustomUrlDecisionManager**: 运行时权限判决
- **LoginFilter**: 自定义登录过滤器，支持验证码校验

### 动态菜单
- 后端API: `GET /system/config/menu` 返回当前用户有权限的菜单树
- 前端 `vuehr/src/utils/menus.js` 的 `initMenu()` 将菜单转换为Vue Router路由
- 菜单数据缓存在Redis中 (cache名: `menus_cache`)

### 消息队列 (RabbitMQ)
- 员工添加成功后，消息发送到队列
- `mailserver` 模块的 `MailReceiver` 消费消息并发送邮件
- 使用手动ACK确保可靠性，Redis记录发送状态防重复

### 实时聊天 (WebSocket)
- 后端端点: `/ws/ep`
- 消息订阅: `/user/queue/chat`
- 消息发送: `/app/chat`
- Vuex管理WebSocket连接状态

## 关键配置

### 后端配置文件
- `vhr/vhrserver/vhr-web/src/main/resources/application.yml`: 主服务配置
- `vhr/mailserver/src/main/resources/application.properties`: 邮件服务配置

需要配置的外部服务:
- MySQL数据库
- Redis (端口6379)
- RabbitMQ
- FastDFS (文件存储)
- SMTP邮件服务器

### 前端代理配置
`vuehr/vue.config.js` 配置了开发服务器代理:
- HTTP请求代理到 `http://localhost:8081`
- WebSocket代理到 `ws://localhost:8081`

## 数据库初始化

使用 **Flyway** 自动迁移:
1. 创建空数据库: `CREATE DATABASE vhr CHARACTER SET utf8mb4;`
2. 配置 `application.yml` 中的数据库连接
3. 启动应用，Flyway自动执行 `vhr-mapper/src/main/resources/db/migration/` 下的脚本

或直接导入 `vhr.sql`

## 代码位置索引

| 功能 | 路径 |
|------|------|
| Spring Security配置 | `vhr/vhrserver/vhr-web/.../config/SecurityConfig.java` |
| 员工管理Controller | `vhr/vhrserver/vhr-web/.../controller/emp/EmpBasicController.java` |
| 员工服务(含Excel导入导出) | `vhr/vhrserver/vhr-service/.../service/EmployeeService.java` |
| 菜单Mapper | `vhr/vhrserver/vhr-mapper/.../mapper/MenuMapper.xml` |
| 邮件接收处理 | `vhr/mailserver/.../receiver/MailReceiver.java` |
| 前端路由守卫 | `vuehr/src/main.js` |
| 动态菜单加载 | `vuehr/src/utils/menus.js` |
| Axios请求封装 | `vuehr/src/utils/api.js` |
| Vuex状态管理 | `vuehr/src/store/index.js` |

## 技术栈速查

**后端**: Spring Boot 2.4.0, Spring Security, MyBatis, Redis, RabbitMQ, WebSocket, POI, FastDFS, Flyway, Druid

**前端**: Vue 2.6.10, Element UI 2.12.0, Vue Router, Vuex, Axios, Stomp.js
