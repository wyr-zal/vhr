# 邮件接收器（MailReceiver）

## 1️⃣ 组件定位

- 模块位置：`mailserver / receiver`
- 类名：`MailReceiver`
- 技术点：
  - RabbitMQ 消息监听（`@RabbitListener`）
  - 手动消息确认（ACK/NACK）
  - Thymeleaf 模板引擎
  - JavaMailSender 邮件发送
  - Redis 消息幂等性

> 这是 **邮件服务的消息消费者**，监听 RabbitMQ 队列并发送入职欢迎邮件。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- MQ 中的消息无人消费
- 邮件发送逻辑耦合在主服务中
- 邮件发送失败无法重试
- 可能出现重复发送

### 有了它之后
- 独立的邮件微服务，解耦主业务
- 监听队列自动消费消息
- 手动 ACK 保证消息不丢失
- Redis 记录防止重复发送

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- mailserver 独立微服务（端口 8082）
- 监听 `javaboy.mail.queue` 队列

### 执行时机
```text
vhr-service 发送消息到 MQ
            ↓
消息进入 javaboy.mail.queue 队列
            ↓
MailReceiver.handler() 自动触发
            ↓
处理消息并发送邮件
            ↓
ACK 确认 / NACK 拒绝
```

---

## 4️⃣ 核心代码解析

```java
@Component
public class MailReceiver {

    @Autowired
    private JavaMailSender javaMailSender;   // 邮件发送器
    @Autowired
    private MailProperties mailProperties;   // 邮件配置
    @Autowired
    private TemplateEngine templateEngine;   // Thymeleaf模板引擎
    @Autowired
    private StringRedisTemplate redisTemplate; // Redis（幂等性）

    /**
     * 监听邮件队列，处理入职邮件发送
     */
    @RabbitListener(queues = MailConstants.MAIL_QUEUE_NAME)
    public void handler(Message message, Channel channel) throws IOException {
        // 1. 解析消息内容
        Employee employee = (Employee) message.getPayload();
        MessageHeaders headers = message.getHeaders();
        Long tag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
        String msgId = (String) headers.get("spring_returned_message_correlation");

        // 2. 幂等性检查：Redis 中是否已存在该消息ID
        if (redisTemplate.opsForHash().entries("mail_log").containsKey(msgId)) {
            logger.info(msgId + ":消息已经被消费");
            channel.basicAck(tag, false);  // 直接确认，跳过处理
            return;
        }

        // 3. 构建邮件
        MimeMessage msg = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg);
        try {
            // 设置邮件基本信息
            helper.setTo(employee.getEmail());           // 收件人
            helper.setFrom(mailProperties.getUsername()); // 发件人
            helper.setSubject("入职欢迎");                // 主题
            helper.setSentDate(new Date());              // 发送时间

            // 4. 使用 Thymeleaf 渲染邮件内容
            Context context = new Context();
            context.setVariable("name", employee.getName());
            context.setVariable("posName", employee.getPosition().getName());
            context.setVariable("joblevelName", employee.getJobLevel().getName());
            context.setVariable("departmentName", employee.getDepartment().getName());
            String mail = templateEngine.process("mail", context);

            helper.setText(mail, true);  // HTML格式

            // 5. 发送邮件
            javaMailSender.send(msg);

            // 6. 记录到 Redis（防止重复发送）
            redisTemplate.opsForHash().put("mail_log", msgId, "javaboy");

            // 7. 确认消息已消费
            channel.basicAck(tag, false);
            logger.info(msgId + ":邮件发送成功");

        } catch (MessagingException e) {
            // 发送失败：拒绝消息并重新入队
            channel.basicNack(tag, false, true);
            logger.error("邮件发送失败：" + e.getMessage());
        }
    }
}
```

---

## 5️⃣ 消息处理流程图

```text
┌─────────────────────────────────────────────────────────────┐
│                    邮件消费处理流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  收到消息 (Employee对象 + msgId)                            │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 1. Redis 幂等性检查                                  │   │
│  │    mail_log 中是否存在 msgId?                        │   │
│  │    是 → basicAck() → 结束                           │   │
│  │    否 → 继续处理                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 2. 构建邮件对象                                      │   │
│  │    - 收件人: employee.getEmail()                     │   │
│  │    - 发件人: mailProperties.getUsername()            │   │
│  │    - 主题: "入职欢迎"                                │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 3. Thymeleaf 渲染邮件模板                            │   │
│  │    模板: resources/templates/mail.html               │   │
│  │    变量: name, posName, joblevelName, departmentName │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 4. 发送邮件                                          │   │
│  │    javaMailSender.send(msg)                         │   │
│  └─────────────────────────────────────────────────────┘   │
│                       ↓                                     │
│  ┌──────────────┬──────────────────────────────────────┐   │
│  │ 成功         │ 失败                                  │   │
│  ├──────────────┼──────────────────────────────────────┤   │
│  │ Redis记录    │ basicNack(tag, false, true)          │   │
│  │ basicAck()   │ 消息重新入队，等待重试                 │   │
│  └──────────────┴──────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ ACK 机制说明

### basicAck - 确认消息

```java
channel.basicAck(tag, false);
```

| 参数 | 说明 |
|------|------|
| `tag` | 消息的唯一标识（deliveryTag） |
| `false` | 是否批量确认（false=仅当前消息） |

**效果**：消息从队列中移除

### basicNack - 拒绝消息

```java
channel.basicNack(tag, false, true);
```

| 参数 | 说明 |
|------|------|
| `tag` | 消息的唯一标识 |
| `false` | 是否批量拒绝 |
| `true` | 是否重新入队（true=重新排队，false=丢弃） |

**效果**：消息重新进入队列头部，等待再次消费

---

## 7️⃣ 幂等性保障（Redis）

### 问题场景
```text
1. 消费者收到消息，发送邮件成功
2. 执行 basicAck() 前，消费者宕机
3. MQ 未收到 ACK，消息重新投递
4. 消费者重启后再次收到同一消息
5. 导致重复发送邮件
```

### 解决方案
```java
// 消费前检查
if (redisTemplate.opsForHash().entries("mail_log").containsKey(msgId)) {
    channel.basicAck(tag, false);  // 已处理，直接确认
    return;
}

// 消费后记录
redisTemplate.opsForHash().put("mail_log", msgId, "javaboy");
channel.basicAck(tag, false);
```

### Redis 数据结构
```text
Key: mail_log
Type: Hash
Field: msgId (如 "550e8400-e29b-41d4-a716-446655440000")
Value: "javaboy" (任意标记值)
```

---

## 8️⃣ 邮件模板（Thymeleaf）

### 模板位置
`mailserver/src/main/resources/templates/mail.html`

### 模板示例
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>入职欢迎</title>
</head>
<body>
    <p>欢迎 <span th:text="${name}"></span> 加入公司！</p>
    <p>您的职位：<span th:text="${posName}"></span></p>
    <p>职级：<span th:text="${joblevelName}"></span></p>
    <p>所属部门：<span th:text="${departmentName}"></span></p>
</body>
</html>
```

### 变量说明
| 变量 | 来源 | 说明 |
|------|------|------|
| `name` | employee.getName() | 员工姓名 |
| `posName` | employee.getPosition().getName() | 职位名称 |
| `joblevelName` | employee.getJobLevel().getName() | 职级名称 |
| `departmentName` | employee.getDepartment().getName() | 部门名称 |

---

## 9️⃣ 配置依赖（application.properties）

```properties
# 服务端口
server.port=8082

# 邮件服务配置（QQ邮箱SMTP）
spring.mail.host=smtp.qq.com
spring.mail.port=587
spring.mail.username=xxx@qq.com
spring.mail.password=授权码（非QQ密码）
spring.mail.default-encoding=UTF-8
spring.mail.properties.mail.smtp.socketFactory.class=javax.net.ssl.SSLSocketFactory

# RabbitMQ
spring.rabbitmq.host=192.168.100.128
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
# 手动确认模式
spring.rabbitmq.listener.simple.acknowledge-mode=manual

# Redis
spring.redis.host=192.168.100.128
spring.redis.port=6379
spring.redis.database=1
```

---

## 🔟 与其他组件的关系

```text
┌─────────────────────────────────────────────────────────────┐
│                    邮件服务完整流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  vhr-service                                                │
│  └── EmployeeService.addEmp()                               │
│      └── rabbitTemplate.convertAndSend() ─────┐             │
│                                               ↓             │
│                                    ┌────────────────┐       │
│                                    │   RabbitMQ     │       │
│                                    │ (mail.queue)   │       │
│                                    └───────┬────────┘       │
│                                            ↓                │
│  mailserver                                                 │
│  └── MailReceiver.handler()                                 │
│      ├── Redis 幂等检查                                     │
│      ├── Thymeleaf 渲染模板                                 │
│      ├── JavaMailSender 发送邮件                            │
│      ├── Redis 记录已发送                                   │
│      └── channel.basicAck() 确认                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
