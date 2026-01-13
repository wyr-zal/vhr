# VHR 项目模块通信机制

## 概述

项目采用三种主要通信方式：
1. **Maven 模块间** - Spring 依赖注入
2. **微服务间** - RabbitMQ 消息队列
3. **前后端间** - HTTP API + WebSocket

## 一、Maven 模块间通信

### 依赖链

```
vhr-web (控制层)
    ↓ @Autowired
vhr-service (业务层)
    ↓ @Autowired
vhr-mapper (数据层)
    ↓ 实体引用
vhr-model (模型层)
```

### 示例：员工管理流程

```java
// 1. Controller 层 (vhr-web)
@RestController
public class EmpBasicController {
    @Autowired
    EmployeeService employeeService;  // 注入 service

    @PostMapping("/")
    public RespBean addEmp(@RequestBody Employee employee) {
        return employeeService.addEmp(employee);
    }
}

// 2. Service 层 (vhr-service)
@Service
public class EmployeeService {
    @Autowired
    EmployeeMapper employeeMapper;  // 注入 mapper

    public Integer addEmp(Employee employee) {
        return employeeMapper.insertSelective(employee);
    }
}

// 3. Mapper 层 (vhr-mapper)
public interface EmployeeMapper {
    int insertSelective(Employee employee);  // MyBatis 映射
}
```

## 二、vhrserver ↔ mailserver 通信 (RabbitMQ)

### 消息队列配置

| 配置项 | 值 |
|-------|-----|
| Exchange | `javaboy.mail.exchange` |
| Queue | `javaboy.mail.queue` |
| Routing Key | `javaboy.mail.routing.key` |
| 端口 | 5672 |

### 生产者 (vhrserver)

**文件**: `vhr-service/.../service/EmployeeService.java`

```java
public Integer addEmp(Employee employee) {
    int result = employeeMapper.insertSelective(employee);
    if (result == 1) {
        Employee emp = employeeMapper.getEmployeeById(employee.getId());
        String msgId = UUID.randomUUID().toString();

        // 记录发送日志
        mailSendLogService.insert(new MailSendLog(msgId, emp.getId()));

        // 发送到 RabbitMQ
        rabbitTemplate.convertAndSend(
            MailConstants.MAIL_EXCHANGE_NAME,
            MailConstants.MAIL_ROUTING_KEY_NAME,
            emp,
            new CorrelationData(msgId)
        );
    }
    return result;
}
```

### 消费者 (mailserver)

**文件**: `mailserver/.../receiver/MailReceiver.java`

```java
@RabbitListener(queues = MailConstants.MAIL_QUEUE_NAME)
public void handler(Message message, Channel channel) throws IOException {
    Employee employee = (Employee) message.getPayload();
    String msgId = (String) headers.get("spring_returned_message_correlation");
    Long tag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);

    // 1. 检查 Redis 防重复消费
    if (redisTemplate.opsForHash().entries("mail_log").containsKey(msgId)) {
        channel.basicAck(tag, false);
        return;
    }

    try {
        // 2. 发送邮件
        MimeMessage msg = javaMailSender.createMimeMessage();
        helper.setTo(employee.getEmail());
        helper.setSubject("入职欢迎");
        helper.setText(templateEngine.process("mail", context), true);
        javaMailSender.send(msg);

        // 3. 记录已处理
        redisTemplate.opsForHash().put("mail_log", msgId, "done");

        // 4. 手动 ACK
        channel.basicAck(tag, false);
    } catch (Exception e) {
        // 5. 失败重新入队
        channel.basicNack(tag, false, true);
    }
}
```

### 消息重试机制

**文件**: `vhr-service/.../task/MailSendTask.java`

```java
@Scheduled(cron = "0/10 * * * * ?")  // 每10秒执行
public void mailResendTask() {
    List<MailSendLog> logs = mailSendLogService.getMailSendLogsByStatus();
    logs.forEach(log -> {
        if (log.getCount() >= 3) {
            // 达到最大重试次数，标记失败
            mailSendLogService.updateMailSendLogStatus(log.getMsgId(), 2);
        } else {
            // 重新发送
            mailSendLogService.updateCount(log.getMsgId(), new Date());
            Employee emp = employeeService.getEmployeeById(log.getEmpId());
            rabbitTemplate.convertAndSend(...);
        }
    });
}
```

### 可靠性保障

```
┌─────────────────────────────────────────────────────────────┐
│                      消息可靠性机制                          │
├─────────────────────────────────────────────────────────────┤
│ 1. 发送前记录日志 (mail_send_log 表)                        │
│ 2. RabbitMQ 发布确认 (publisher-confirm)                    │
│ 3. Redis 记录已消费消息ID (防重复)                          │
│ 4. 手动 ACK (确保处理成功才确认)                            │
│ 5. 定时任务重试 (最多3次)                                   │
└─────────────────────────────────────────────────────────────┘
```

## 三、前端 ↔ 后端通信

### HTTP API

**文件**: `vuehr/src/utils/api.js`

```javascript
// 请求方法封装
export const postRequest = (url, params) => {
    return axios({method: 'post', url: `${base}${url}`, data: params})
}
export const getRequest = (url, params) => {
    return axios({method: 'get', url: `${base}${url}`, params: params})
}

// 响应拦截器
axios.interceptors.response.use(success => {
    if (success.data.msg) {
        Message.success({message: success.data.msg})
    }
    return success.data;
}, error => {
    if (error.response.status == 401) {
        router.replace('/');  // 未登录跳转
    }
})
```

### WebSocket 实时聊天

**后端配置**: `vhr-web/.../config/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/ep")
                .setAllowedOrigins("http://localhost:8080")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
    }
}
```

**后端消息处理**: `vhr-web/.../controller/WsController.java`

```java
@MessageMapping("/ws/chat")
public void handleMsg(Authentication auth, ChatMsg chatMsg) {
    Hr hr = (Hr) auth.getPrincipal();
    chatMsg.setFrom(hr.getUsername());
    chatMsg.setDate(new Date());

    // 发送给指定用户
    simpMessagingTemplate.convertAndSendToUser(
        chatMsg.getTo(),
        "/queue/chat",
        chatMsg
    );
}
```

**前端连接**: `vuehr/src/store/index.js`

```javascript
actions: {
    connect(context) {
        context.state.stomp = Stomp.over(new SockJS('/ws/ep'));
        context.state.stomp.connect({}, success => {
            // 订阅个人消息队列
            context.state.stomp.subscribe('/user/queue/chat', msg => {
                let receiveMsg = JSON.parse(msg.body);
                // 显示通知、更新会话...
                context.commit('addMessage', receiveMsg);
            })
        })
    }
}
```

## 四、架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                              前端                                    │
│                         vuehr (8080)                                │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐            │
│   │  Vue Router │    │    Vuex     │    │   Axios     │            │
│   │  动态菜单    │    │  状态管理   │    │  HTTP请求   │            │
│   └─────────────┘    └─────────────┘    └─────────────┘            │
│          │                  │                  │                    │
│          └──────────────────┼──────────────────┘                    │
│                             │                                       │
└─────────────────────────────┼───────────────────────────────────────┘
                              │ HTTP / WebSocket
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           vhrserver (8081)                          │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                        vhr-web                                │  │
│  │  Controllers / WebSocket / Security Config                    │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│                             │ @Autowired                            │
│  ┌──────────────────────────┴───────────────────────────────────┐  │
│  │                       vhr-service                             │  │
│  │  业务逻辑 / RabbitMQ生产者 / 定时任务                         │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│                             │ @Autowired                            │
│  ┌──────────────────────────┴───────────────────────────────────┐  │
│  │                       vhr-mapper                              │  │
│  │  MyBatis Mapper / Flyway                                      │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│                             │                                       │
│  ┌──────────────────────────┴───────────────────────────────────┐  │
│  │                       vhr-model                               │  │
│  │  实体类 / 常量                                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
    ┌──────────┐       ┌──────────┐       ┌──────────┐
    │  MySQL   │       │  Redis   │       │ RabbitMQ │
    │  数据库   │       │  缓存    │       │  消息队列 │
    └──────────┘       └──────────┘       └────┬─────┘
                                               │
                                               ▼
                              ┌─────────────────────────────┐
                              │     mailserver (8082)       │
                              │  消费消息 → 发送邮件         │
                              └─────────────────────────────┘
```

## 五、端口与协议汇总

| 服务 | 端口 | 协议 | 说明 |
|-----|------|------|-----|
| vuehr | 8080 | HTTP | 前端开发服务器 |
| vhrserver | 8081 | HTTP/WS | 后端主服务 |
| mailserver | 8082 | - | 邮件微服务 |
| MySQL | 3306 | TCP | 数据库 |
| Redis | 6379 | TCP | 缓存 |
| RabbitMQ | 5672 | AMQP | 消息队列 |
