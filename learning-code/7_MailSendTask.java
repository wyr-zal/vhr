package org.javaboy.vhr.task;

import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.MailConstants;
import org.javaboy.vhr.model.MailSendLog;
import org.javaboy.vhr.service.EmployeeService;
import org.javaboy.vhr.service.MailSendLogService;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * =====================================================
 * 邮件发送重试定时任务
 * =====================================================
 *
 * 【类的作用】
 * 这是一个定时任务类，负责处理发送失败的邮件消息
 * 定期扫描数据库中"发送中"状态的消息，进行重试
 *
 * 【为什么需要重试机制？】
 * 消息发送可能因各种原因失败：
 * 1. 网络抖动导致消息未到达RabbitMQ
 * 2. RabbitMQ服务暂时不可用
 * 3. 消息发送后，应用在收到确认前宕机
 *
 * 【重试策略】
 * - 最多重试3次
 * - 每10秒扫描一次
 * - 超过3次标记为发送失败
 *
 * 【消息状态流转】
 *
 *  发送消息 → status=0(发送中)
 *              ↓
 *       ConfirmCallback成功？
 *          ↓是              ↓否
 *    status=1(成功)     定时任务重试
 *                           ↓
 *                    重试次数 >= 3？
 *                       ↓是      ↓否
 *                status=2(失败)  重新发送
 */
@Component
public class MailSendTask {

    @Autowired
    MailSendLogService mailSendLogService;  // 邮件发送日志服务

    @Autowired
    RabbitTemplate rabbitTemplate;  // RabbitMQ模板

    @Autowired
    EmployeeService employeeService;  // 员工服务

    /**
     * 【邮件重发定时任务】
     *
     * @Scheduled 注解表示这是一个定时任务
     * cron表达式 "0/10 * * * * ?" 表示每10秒执行一次
     *
     * cron表达式格式：秒 分 时 日 月 周
     * 0/10 表示从0秒开始，每10秒执行一次
     */
    @Scheduled(cron = "0/10 * * * * ?")
    public void mailResendTask() {

        // ========== 第一步：查询需要重试的消息 ==========
        // 获取所有状态为"发送中"(status=0)且已超过预期确认时间的消息
        List<MailSendLog> logs = mailSendLogService.getMailSendLogsByStatus();

        // 如果没有需要重试的消息，直接返回
        if (logs == null || logs.size() == 0) {
            return;
        }

        // ========== 第二步：遍历处理每条消息 ==========
        logs.forEach(mailSendLog -> {

            // ========== 判断重试次数 ==========
            if (mailSendLog.getCount() >= 3) {
                // ========== 超过最大重试次数，标记为发送失败 ==========
                // status=2 表示发送失败
                mailSendLogService.updateMailSendLogStatus(mailSendLog.getMsgId(), 2);

                // 【扩展】这里可以：
                // 1. 发送告警通知运维人员
                // 2. 记录到失败日志表
                // 3. 触发人工处理流程

            } else {
                // ========== 未超过最大重试次数，重新发送 ==========

                // 更新重试次数和重试时间
                mailSendLogService.updateCount(mailSendLog.getMsgId(), new Date());

                // 根据员工ID查询完整的员工信息
                Employee emp = employeeService.getEmployeeById(mailSendLog.getEmpId());

                // 重新发送消息到RabbitMQ
                // 参数说明：
                // - exchange: 交换机名称
                // - routingKey: 路由键
                // - message: 消息内容（员工对象）
                // - correlationData: 关联数据，包含消息ID，用于追踪
                rabbitTemplate.convertAndSend(
                        MailConstants.MAIL_EXCHANGE_NAME,     // 交换机
                        MailConstants.MAIL_ROUTING_KEY_NAME,  // 路由键
                        emp,                                   // 消息内容
                        new CorrelationData(mailSendLog.getMsgId())  // 消息ID
                );
            }
        });
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【Spring定时任务】
 *
 *    启用定时任务需要在启动类添加 @EnableScheduling 注解
 *
 *    @SpringBootApplication
 *    @EnableScheduling  // 启用定时任务
 *    public class Application { ... }
 *
 * 2. 【Cron表达式详解】
 *
 *    格式：秒 分 时 日 月 周 [年]
 *
 *    常用表达式：
 *    "0/10 * * * * ?"    每10秒执行一次
 *    "0 0/5 * * * ?"     每5分钟执行一次
 *    "0 0 * * * ?"       每小时执行一次
 *    "0 0 0 * * ?"       每天凌晨执行一次
 *    "0 0 0 * * 1"       每周一凌晨执行一次
 *
 *    特殊字符：
 *    * 表示任意值
 *    ? 表示不关心（只用于日和周）
 *    / 表示增量（0/10表示从0开始每10个单位）
 *
 * 3. 【消息可靠性保证的完整方案】
 *
 *    ┌─────────────────────────────────────────────────────┐
 *    │                   发送端                             │
 *    │  ① 发送消息前，先在数据库记录日志（status=0）        │
 *    │  ② 发送消息到RabbitMQ                               │
 *    │  ③ ConfirmCallback确认后，更新状态（status=1）       │
 *    │  ④ 定时任务扫描未确认的消息，重新发送                 │
 *    └─────────────────────────────────────────────────────┘
 *
 *    ┌─────────────────────────────────────────────────────┐
 *    │                   消费端                             │
 *    │  ① 接收消息                                         │
 *    │  ② Redis幂等性检查                                  │
 *    │  ③ 处理业务逻辑                                     │
 *    │  ④ 记录到Redis                                      │
 *    │  ⑤ 手动ACK确认                                      │
 *    └─────────────────────────────────────────────────────┘
 *
 * 4. 【mail_send_log表结构】
 *
 *    CREATE TABLE mail_send_log (
 *        msgId VARCHAR(255) PRIMARY KEY,  -- 消息ID
 *        empId INT,                        -- 员工ID
 *        status INT DEFAULT 0,             -- 状态：0发送中，1成功，2失败
 *        routeKey VARCHAR(255),            -- 路由键
 *        exchange VARCHAR(255),            -- 交换机
 *        count INT DEFAULT 0,              -- 重试次数
 *        tryTime DATETIME,                 -- 下次重试时间
 *        createTime DATETIME,              -- 创建时间
 *        updateTime DATETIME               -- 更新时间
 *    );
 *
 * 5. 【查询待重试消息的SQL】
 *
 *    SELECT * FROM mail_send_log
 *    WHERE status = 0           -- 发送中
 *    AND tryTime < NOW()        -- 已超过预期确认时间
 *
 * 6. 【重试间隔的优化建议】
 *
 *    当前实现：固定10秒重试一次
 *
 *    更好的实现：指数退避（Exponential Backoff）
 *    - 第1次重试：1分钟后
 *    - 第2次重试：5分钟后
 *    - 第3次重试：30分钟后
 *
 *    这样可以避免频繁重试对系统造成压力
 *
 * 7. 【与RabbitConfig的配合】
 *
 *    RabbitConfig 配置了 ConfirmCallback：
 *    - 消息到达Exchange成功 → 更新status=1
 *    - 消息到达Exchange失败 → 保持status=0
 *
 *    MailSendTask 处理status=0的记录：
 *    - 重试次数<3 → 重新发送
 *    - 重试次数>=3 → 更新status=2
 */
