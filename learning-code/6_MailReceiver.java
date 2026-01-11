package org.javaboy.mailserver.receiver;

import com.rabbitmq.client.Channel;
import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.MailConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Date;

/**
 * =====================================================
 * 邮件消息消费者 - 监听RabbitMQ队列并发送邮件
 * =====================================================
 *
 * 【类的作用】
 * 这是一个独立的邮件微服务，负责：
 * 1. 监听RabbitMQ队列中的消息
 * 2. 接收员工入职信息
 * 3. 使用模板引擎渲染邮件内容
 * 4. 发送入职欢迎邮件
 *
 * 【消息消费流程】
 *
 *  Queue ──监听──> MailReceiver.handler()
 *                       │
 *                       ↓ 检查Redis防重复
 *                       │
 *                       ↓ 渲染邮件模板
 *                       │
 *                       ↓ 发送邮件
 *                       │
 *                       ↓ 记录到Redis
 *                       │
 *                       ↓ ACK确认消息
 *
 * 【幂等性保证】
 * 使用Redis记录已处理的消息ID，防止同一消息被重复处理
 * 这在消息重试场景下非常重要
 */
@Component
public class MailReceiver {

    // 日志记录器
    public static final Logger logger = LoggerFactory.getLogger(MailReceiver.class);

    @Autowired
    private JavaMailSender javaMailSender;  // Spring Boot邮件发送组件

    @Autowired
    private MailProperties mailProperties;  // 邮件配置属性（发件人地址等）

    @Autowired
    private TemplateEngine templateEngine;  // Thymeleaf模板引擎

    @Autowired
    private StringRedisTemplate redisTemplate;  // Redis操作模板

    /**
     * 【消息监听方法】
     *
     * @RabbitListener 注解表示这个方法监听指定的队列
     * 当队列中有消息时，Spring会自动调用这个方法
     *
     * @param message RabbitMQ消息对象，包含消息体和消息头
     * @param channel RabbitMQ通道，用于手动确认消息
     * @throws IOException 可能的IO异常
     */
    @RabbitListener(queues = MailConstants.MAIL_QUEUE_NAME)  // 监听 "javaboy.mail.queue" 队列
    public void handler(Message message, Channel channel) throws IOException {

        // ========== 第一步：解析消息 ==========
        // 获取消息体（员工对象）
        Employee employee = (Employee) message.getPayload();

        // 获取消息头信息
        MessageHeaders headers = message.getHeaders();

        // 获取消息的Delivery Tag（投递标签）
        // 这是RabbitMQ用于标识消息的唯一标识，用于ACK确认
        Long tag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);

        // 获取消息的唯一ID（由生产者设置的CorrelationData）
        String msgId = (String) headers.get("spring_returned_message_correlation");

        // ========== 第二步：幂等性检查（防止重复消费）==========
        // 检查Redis中是否已经处理过这条消息
        // 使用Hash结构：key="mail_log", field=msgId, value=任意值
        if (redisTemplate.opsForHash().entries("mail_log").containsKey(msgId)) {
            // 消息已经被处理过，直接ACK确认，不再重复发送
            logger.info(msgId + ":消息已经被消费");
            channel.basicAck(tag, false);  // 确认消息
            return;
        }

        // ========== 第三步：创建邮件 ==========
        MimeMessage msg = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg);

        try {
            // ========== 第四步：设置邮件基本信息 ==========
            helper.setTo(employee.getEmail());           // 收件人：员工邮箱
            helper.setFrom(mailProperties.getUsername()); // 发件人：配置的邮箱
            helper.setSubject("入职欢迎");                // 邮件主题
            helper.setSentDate(new Date());              // 发送时间

            // ========== 第五步：使用Thymeleaf渲染邮件内容 ==========
            // 创建Thymeleaf上下文，设置模板变量
            Context context = new Context();
            context.setVariable("name", employee.getName());                    // 员工姓名
            context.setVariable("posName", employee.getPosition().getName());   // 职位名称
            context.setVariable("joblevelName", employee.getJobLevel().getName()); // 职级名称
            context.setVariable("departmentName", employee.getDepartment().getName()); // 部门名称

            // 使用模板引擎处理 "mail" 模板（对应 mail.html）
            // 模板路径：resources/templates/mail.html
            String mail = templateEngine.process("mail", context);

            // 设置邮件正文（第二个参数true表示HTML格式）
            helper.setText(mail, true);

            // ========== 第六步：发送邮件 ==========
            javaMailSender.send(msg);

            // ========== 第七步：记录到Redis（防止重复消费）==========
            // 将消息ID记录到Redis，下次收到相同消息时跳过
            redisTemplate.opsForHash().put("mail_log", msgId, "javaboy");

            // ========== 第八步：ACK确认消息 ==========
            // 告诉RabbitMQ这条消息已经被成功处理
            // 参数说明：
            // - tag: 消息的投递标签
            // - multiple: false表示只确认当前消息，true表示确认所有小于等于tag的消息
            channel.basicAck(tag, false);

            logger.info(msgId + ":邮件发送成功");

        } catch (MessagingException e) {
            // ========== 异常处理：邮件发送失败 ==========
            // NACK拒绝消息，让RabbitMQ重新投递
            // 参数说明：
            // - tag: 消息的投递标签
            // - multiple: false表示只拒绝当前消息
            // - requeue: true表示重新放入队列，false表示丢弃或进入死信队列
            channel.basicNack(tag, false, true);

            e.printStackTrace();
            logger.error("邮件发送失败：" + e.getMessage());
        }
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【@RabbitListener注解】
 *    - queues: 指定监听的队列名称
 *    - 可以监听多个队列：@RabbitListener(queues = {"queue1", "queue2"})
 *    - Spring会自动将消息反序列化为Java对象
 *
 * 2. 【消息确认机制（ACK）】
 *
 *    ┌─────────────────────────────────────────────────┐
 *    │  basicAck(tag, false)                          │
 *    │  └─ 确认成功处理，从队列中移除消息               │
 *    │                                                 │
 *    │  basicNack(tag, false, true)                   │
 *    │  └─ 处理失败，重新放入队列等待重试               │
 *    │                                                 │
 *    │  basicNack(tag, false, false)                  │
 *    │  └─ 处理失败，丢弃消息或进入死信队列             │
 *    └─────────────────────────────────────────────────┘
 *
 * 3. 【幂等性（Idempotency）】
 *
 *    为什么需要幂等性？
 *    - 网络抖动可能导致ACK丢失，RabbitMQ重新投递消息
 *    - 消费者处理过程中宕机，重启后消息被重新投递
 *
 *    实现方式：
 *    - 使用Redis记录已处理的消息ID
 *    - 收到消息时先检查是否已处理
 *    - 也可以使用数据库唯一索引实现
 *
 * 4. 【Thymeleaf邮件模板】
 *
 *    模板文件 mail.html 示例：
 *    <!DOCTYPE html>
 *    <html xmlns:th="http://www.thymeleaf.org">
 *    <body>
 *        <p>欢迎 <span th:text="${name}"></span> 加入公司！</p>
 *        <p>您的职位是：<span th:text="${posName}"></span></p>
 *        <p>您的职级是：<span th:text="${joblevelName}"></span></p>
 *        <p>您所在部门：<span th:text="${departmentName}"></span></p>
 *    </body>
 *    </html>
 *
 * 5. 【消息处理的最佳实践】
 *
 *    ① 先做幂等性检查
 *    ② 执行业务逻辑
 *    ③ 记录处理状态（Redis/数据库）
 *    ④ 最后ACK确认
 *
 *    这个顺序很重要！如果先ACK再处理，处理失败时消息已经丢失
 *
 * 6. 【手动ACK vs 自动ACK】
 *
 *    自动ACK：消息发送给消费者后立即确认
 *    - 优点：简单
 *    - 缺点：如果消费者处理失败，消息会丢失
 *
 *    手动ACK：消费者处理完成后显式确认
 *    - 优点：可靠，失败可以重试
 *    - 缺点：需要手动调用channel.basicAck()
 *
 *    生产环境建议使用手动ACK！
 *
 *    配置方式：
 *    spring:
 *      rabbitmq:
 *        listener:
 *          simple:
 *            acknowledge-mode: manual  # 手动确认
 */
