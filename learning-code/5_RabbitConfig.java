package org.javaboy.vhr.config;

import org.javaboy.vhr.model.MailConstants;
import org.javaboy.vhr.service.MailSendLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * =====================================================
 * RabbitMQ 消息队列配置类
 * =====================================================
 *
 * 【RabbitMQ 核心概念】
 *
 * 1. Producer（生产者）：发送消息的程序
 * 2. Consumer（消费者）：接收消息的程序
 * 3. Exchange（交换机）：接收生产者发送的消息，根据路由规则分发到队列
 * 4. Queue（队列）：存储消息的缓冲区
 * 5. Binding（绑定）：交换机和队列之间的关联关系
 * 6. Routing Key（路由键）：用于交换机决定如何路由消息
 *
 * 【消息流转过程】
 *
 *  Producer → Exchange → Queue → Consumer
 *              ↑
 *          Routing Key
 *
 * 【本项目的应用场景】
 * 员工入职 → 发送消息到MQ → 邮件服务消费消息 → 发送入职欢迎邮件
 *
 * 【为什么用消息队列？】
 * 1. 异步处理：添加员工操作立即返回，邮件在后台发送
 * 2. 解耦：员工服务不依赖邮件服务
 * 3. 削峰：批量导入员工时，邮件发送不会压垮系统
 * 4. 可靠性：消息持久化，即使服务重启也不会丢失
 */
@Configuration
public class RabbitConfig {

    // 日志记录器
    public final static Logger logger = LoggerFactory.getLogger(RabbitConfig.class);

    @Autowired
    CachingConnectionFactory cachingConnectionFactory;  // RabbitMQ连接工厂

    @Autowired
    MailSendLogService mailSendLogService;  // 邮件发送日志服务

    /**
     * 【配置RabbitTemplate】
     *
     * RabbitTemplate是发送消息的核心组件
     * 这里配置了两个重要的回调函数，用于确保消息可靠性
     */
    @Bean
    RabbitTemplate rabbitTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(cachingConnectionFactory);

        // ========== 消息确认回调（ConfirmCallback）==========
        // 当消息发送到Exchange后，RabbitMQ会回调此方法
        // 告诉我们消息是否成功到达Exchange
        rabbitTemplate.setConfirmCallback((data, ack, cause) -> {
            // data: CorrelationData，包含消息的唯一标识
            // ack: true表示消息成功到达Exchange，false表示失败
            // cause: 失败原因

            String msgId = data.getId();  // 获取消息ID

            if (ack) {
                // ========== 消息发送成功 ==========
                logger.info(msgId + ":消息发送成功");
                // 更新数据库中的消息状态为"发送成功"
                // status=1 表示发送成功
                mailSendLogService.updateMailSendLogStatus(msgId, 1);
            } else {
                // ========== 消息发送失败 ==========
                logger.info(msgId + ":消息发送失败");
                // 消息发送失败，不更新状态
                // 后续会有定时任务重试发送
            }
        });

        // ========== 消息返回回调（ReturnCallback）==========
        // 当消息到达Exchange但无法路由到Queue时，会回调此方法
        // 常见原因：路由键不匹配、队列不存在等
        rabbitTemplate.setReturnCallback((msg, repCode, repText, exchange, routingkey) -> {
            // msg: 返回的消息
            // repCode: 回复码
            // repText: 回复文本
            // exchange: 交换机名称
            // routingkey: 路由键

            logger.info("消息发送失败");
            // 这里可以做进一步处理，如记录日志、告警等
        });

        return rabbitTemplate;
    }

    /**
     * 【声明队列】
     *
     * 创建一个持久化的队列，用于存储邮件消息
     *
     * @return Queue对象
     *
     * 参数说明：
     * - name: 队列名称
     * - durable: true表示持久化，RabbitMQ重启后队列依然存在
     */
    @Bean
    Queue mailQueue() {
        // MailConstants.MAIL_QUEUE_NAME = "javaboy.mail.queue"
        return new Queue(MailConstants.MAIL_QUEUE_NAME, true);
    }

    /**
     * 【声明交换机】
     *
     * 创建一个Direct类型的交换机
     *
     * @return DirectExchange对象
     *
     * 【交换机类型】
     * 1. Direct：精确匹配路由键
     * 2. Fanout：广播到所有绑定的队列
     * 3. Topic：模式匹配路由键（支持通配符）
     * 4. Headers：根据消息头匹配
     *
     * 参数说明：
     * - name: 交换机名称
     * - durable: true表示持久化
     * - autoDelete: false表示不自动删除
     */
    @Bean
    DirectExchange mailExchange() {
        // MailConstants.MAIL_EXCHANGE_NAME = "javaboy.mail.exchange"
        return new DirectExchange(MailConstants.MAIL_EXCHANGE_NAME, true, false);
    }

    /**
     * 【绑定队列到交换机】
     *
     * 将队列和交换机关联起来，指定路由键
     * 当消息发送到交换机时，交换机根据路由键将消息路由到对应的队列
     *
     * @return Binding对象
     *
     * 绑定关系：
     * mailExchange --[路由键]--> mailQueue
     */
    @Bean
    Binding mailBinding() {
        // MailConstants.MAIL_ROUTING_KEY_NAME = "javaboy.mail.routing.key"
        return BindingBuilder
                .bind(mailQueue())           // 绑定队列
                .to(mailExchange())          // 到交换机
                .with(MailConstants.MAIL_ROUTING_KEY_NAME);  // 使用指定的路由键
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【消息可靠性保证机制】
 *
 *    ┌─────────────────────────────────────────────────────┐
 *    │                   消息发送流程                        │
 *    │                                                     │
 *    │  Producer ──①──> Exchange ──②──> Queue ──③──> Consumer│
 *    │                                                     │
 *    │  ① ConfirmCallback：确认消息到达Exchange            │
 *    │  ② ReturnCallback：确认消息路由到Queue              │
 *    │  ③ ACK机制：确认消费者成功处理消息                  │
 *    └─────────────────────────────────────────────────────┘
 *
 * 2. 【消息持久化】
 *    - 交换机持久化：durable=true
 *    - 队列持久化：durable=true
 *    - 消息持久化：设置deliveryMode=2
 *    三者都持久化，才能保证RabbitMQ重启后消息不丢失
 *
 * 3. 【Direct交换机的路由规则】
 *    - 精确匹配：消息的routingKey必须与绑定的routingKey完全相同
 *    - 一对多：一个交换机可以绑定多个队列（不同routingKey）
 *    - 多对一：多个绑定可以使用相同的routingKey
 *
 * 4. 【本项目的消息流转】
 *
 *    EmployeeService.addEmp()
 *           │
 *           ↓ 发送消息
 *    rabbitTemplate.convertAndSend(
 *        "javaboy.mail.exchange",      // 交换机
 *        "javaboy.mail.routing.key",   // 路由键
 *        employee,                     // 消息内容（员工对象）
 *        correlationData               // 关联数据（包含消息ID）
 *    )
 *           │
 *           ↓ Exchange根据路由键转发
 *    javaboy.mail.queue（队列）
 *           │
 *           ↓ 消费者监听
 *    MailReceiver.handler()
 *           │
 *           ↓
 *    发送邮件
 *
 * 5. 【与数据库的配合】
 *    - 发送消息前：在mail_send_log表记录一条日志，状态为"发送中"
 *    - ConfirmCallback成功：更新状态为"发送成功"
 *    - 定时任务：扫描"发送中"状态的记录，重新发送
 *
 * 6. 【需要开启的RabbitMQ配置】
 *    application.yml:
 *    spring:
 *      rabbitmq:
 *        publisher-confirms: true    # 开启确认回调
 *        publisher-returns: true     # 开启返回回调
 */
