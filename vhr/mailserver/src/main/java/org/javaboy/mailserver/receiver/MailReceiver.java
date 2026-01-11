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
 * 邮件接收器，负责从RabbitMQ队列中接收消息并发送邮件。
 */
@Component
public class MailReceiver {

    // 日志记录器
    public static final Logger logger = LoggerFactory.getLogger(MailReceiver.class);

    @Autowired
    private JavaMailSender javaMailSender;  // 用于发送邮件
    @Autowired
    private MailProperties mailProperties;  // 邮件属性配置
    @Autowired
    private TemplateEngine templateEngine;  // Thymeleaf模板引擎，用于渲染邮件内容
    @Autowired
    private StringRedisTemplate redisTemplate;  // Redis模板，用于检查是否已发送邮件

    /**
     * 监听RabbitMQ队列中的邮件消息，并处理邮件发送
     * @param message 消息内容
     * @param channel RabbitMQ的channel，用于消息确认
     * @throws IOException 可能抛出的IOException异常
     */
    @RabbitListener(queues = MailConstants.MAIL_QUEUE_NAME)
    public void handler(Message message, Channel channel) throws IOException {
        // 获取员工信息对象
        Employee employee = (Employee) message.getPayload();
        // 获取消息头部中的信息
        MessageHeaders headers = message.getHeaders();
        Long tag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);  // 消息的delivery tag，用于确认消息
        String msgId = (String) headers.get("spring_returned_message_correlation");  // 消息的唯一标识

        // 检查消息ID是否已在Redis中，若已存在，则说明消息已被处理
        if (redisTemplate.opsForHash().entries("mail_log").containsKey(msgId)) {
            logger.info(msgId + ":消息已经被消费");
            channel.basicAck(tag, false);  // 确认消息已消费
            return;
        }

        // 创建并准备发送邮件
        MimeMessage msg = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg);
        try {
            // 设置邮件的收件人、发件人、主题和发送时间
            helper.setTo(employee.getEmail());
            helper.setFrom(mailProperties.getUsername());
            helper.setSubject("入职欢迎");
            helper.setSentDate(new Date());

            // 使用Thymeleaf模板引擎渲染邮件内容
            Context context = new Context();
            context.setVariable("name", employee.getName());
            context.setVariable("posName", employee.getPosition().getName());
            context.setVariable("joblevelName", employee.getJobLevel().getName());
            context.setVariable("departmentName", employee.getDepartment().getName());
            String mail = templateEngine.process("mail", context);  // 渲染邮件内容模板

            helper.setText(mail, true);  // 设置邮件的正文内容，并启用HTML格式

            // 发送邮件
            javaMailSender.send(msg);

            // 将邮件消息ID保存到Redis，防止重复发送
            redisTemplate.opsForHash().put("mail_log", msgId, "javaboy");

            // 确认消息已消费
            channel.basicAck(tag, false);
            logger.info(msgId + ":邮件发送成功");
        } catch (MessagingException e) {
            // 如果邮件发送失败，拒绝消息并重新入队
            channel.basicNack(tag, false, true);
            e.printStackTrace();
            logger.error("邮件发送失败：" + e.getMessage());
        }
    }
}
