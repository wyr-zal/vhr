# 员工服务类（EmployeeService）

## 1️⃣ 组件定位

- 模块位置：`vhr-service / service`
- 类名：`EmployeeService`
- 技术点：
  - RabbitMQ 消息发送
  - 分页查询
  - 合同年限计算
  - 消息可靠性投递
  - 批量操作

> 这是 **员工管理的核心服务类**，处理员工增删改查，并集成 RabbitMQ 实现新员工入职邮件通知。

---

## 2️⃣ 解决了什么问题？

### 如果没有它
- 员工数据的 CRUD 操作分散
- 新增员工后无法自动发送邮件
- 分页查询逻辑无法复用
- 合同年限需要手动计算

### 有了它之后
- 统一管理员工业务逻辑
- 新增员工自动发送 MQ 消息触发邮件
- 封装分页查询，支持多条件筛选
- 自动计算合同年限

---

## 3️⃣ 生效范围 & 执行时机

### 生效范围
- 员工管理模块的所有业务
- 薪资管理模块的员工查询

### MQ 消息发送时机
```text
EmpBasicController.addEmp()
        ↓
EmployeeService.addEmp()
        ↓
数据库插入成功
        ↓
发送消息到 RabbitMQ
        ↓
mailserver 消费消息并发送邮件
```

---

## 4️⃣ 核心方法解析

### 4.1 addEmp - 新增员工（含MQ消息）

```java
public Integer addEmp(Employee employee) {
    // 1. 计算合同年限
    Date beginContract = employee.getBeginContract();
    Date endContract = employee.getEndContract();

    // 计算总月份：(结束年-开始年)*12 + (结束月-开始月)
    double month = (Double.parseDouble(yearFormat.format(endContract))
                  - Double.parseDouble(yearFormat.format(beginContract))) * 12
                 + (Double.parseDouble(monthFormat.format(endContract))
                  - Double.parseDouble(monthFormat.format(beginContract)));

    // 转换为年（保留两位小数）
    employee.setContractTerm(Double.parseDouble(decimalFormat.format(month / 12)));

    // 2. 插入数据库
    int result = employeeMapper.insertSelective(employee);

    // 3. 插入成功则发送MQ消息
    if (result == 1) {
        // 查询完整员工信息（含关联数据）
        Employee emp = employeeMapper.getEmployeeById(employee.getId());

        // 生成消息唯一ID
        String msgId = UUID.randomUUID().toString();

        // 记录邮件发送日志
        MailSendLog mailSendLog = new MailSendLog();
        mailSendLog.setMsgId(msgId);
        mailSendLog.setCreateTime(new Date());
        mailSendLog.setExchange(MailConstants.MAIL_EXCHANGE_NAME);
        mailSendLog.setRouteKey(MailConstants.MAIL_ROUTING_KEY_NAME);
        mailSendLog.setEmpId(emp.getId());
        mailSendLog.setTryTime(new Date(System.currentTimeMillis()
                              + 1000 * 60 * MailConstants.MSG_TIMEOUT));
        mailSendLogService.insert(mailSendLog);

        // 发送MQ消息
        rabbitTemplate.convertAndSend(
            MailConstants.MAIL_EXCHANGE_NAME,    // 交换机
            MailConstants.MAIL_ROUTING_KEY_NAME, // 路由键
            emp,                                  // 消息体
            new CorrelationData(msgId)            // 关联数据（用于确认回调）
        );
    }

    return result;
}
```

### 4.2 getEmployeeByPage - 分页条件查询

```java
public RespPageBean getEmployeeByPage(Integer page, Integer size,
                                       Employee employee, Date[] beginDateScope) {
    // 分页参数转换：页码 → 偏移量
    if (page != null && size != null) {
        page = (page - 1) * size;  // 第1页 → offset=0
    }

    // 查询数据列表
    List<Employee> data = employeeMapper.getEmployeeByPage(
        page, size, employee, beginDateScope);

    // 查询总数
    Long total = employeeMapper.getTotal(employee, beginDateScope);

    // 封装分页结果
    RespPageBean bean = new RespPageBean();
    bean.setData(data);
    bean.setTotal(total);
    return bean;
}
```

**支持的筛选条件**：
- 姓名（模糊）
- 政治面貌
- 民族
- 职位
- 职级
- 部门
- 入职日期范围

---

## 5️⃣ MQ 消息发送流程

```text
┌─────────────────────────────────────────────────────────────┐
│                    新增员工 → 邮件通知流程                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Controller 接收请求                                      │
│     POST /emp/basic {employee...}                           │
│                       ↓                                     │
│  2. EmployeeService.addEmp()                                │
│     - 计算合同年限                                           │
│     - 插入数据库                                             │
│                       ↓                                     │
│  3. 生成消息ID，记录发送日志                                 │
│     INSERT INTO mail_send_log                               │
│                       ↓                                     │
│  4. 发送消息到 RabbitMQ                                      │
│     Exchange: javaboy.mail.exchange                         │
│     RoutingKey: javaboy.mail.routing.key                    │
│     Queue: javaboy.mail.queue                               │
│                       ↓                                     │
│  5. RabbitTemplate 确认回调                                  │
│     - 成功: 更新日志状态为已投递                             │
│     - 失败: 日志状态保持待投递（定时任务重试）               │
│                       ↓                                     │
│  6. mailserver 消费消息                                      │
│     - 发送邮件                                               │
│     - 记录 Redis 防重复                                      │
│     - ACK 确认                                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ 消息可靠性保障

### 6.1 生产端（vhr-service）

```java
// RabbitConfig.java 中的确认回调
rabbitTemplate.setConfirmCallback((data, ack, cause) -> {
    String msgId = data.getId();
    if (ack) {
        logger.info(msgId + ":消息发送成功");
        // 更新日志状态为已投递
        mailSendLogService.updateMailSendLogStatus(msgId, 1);
    } else {
        logger.info(msgId + ":消息发送失败");
        // 状态保持0，等待定时任务重试
    }
});
```

### 6.2 消费端（mailserver）

```java
// 手动ACK + Redis防重复
if (redisTemplate.opsForHash().entries("mail_log").containsKey(msgId)) {
    channel.basicAck(tag, false);  // 已处理，直接确认
    return;
}
// 发送邮件...
redisTemplate.opsForHash().put("mail_log", msgId, "javaboy");
channel.basicAck(tag, false);
```

---

## 7️⃣ 分页结果封装

```java
public class RespPageBean {
    private Long total;       // 总记录数
    private List<?> data;     // 当前页数据

    // getter/setter...
}
```

**前端分页组件使用**：
```javascript
// Element UI 分页
<el-pagination
    :total="total"
    :page-size="size"
    @current-change="handlePageChange">
</el-pagination>
```

---

## 8️⃣ 方法清单

| 方法 | 功能 | 特点 |
|------|------|------|
| `addEmp` | 新增员工 | 自动计算合同年限 + MQ消息 |
| `getEmployeeByPage` | 分页条件查询 | 支持多条件筛选 |
| `deleteEmpByEid` | 删除员工 | 物理删除 |
| `updateEmp` | 更新员工 | 选择性更新 |
| `addEmps` | 批量新增 | Excel导入使用 |
| `maxWorkID` | 查询最大工号 | 生成新工号 |
| `getEmployeeByPageWithSalary` | 分页查询（含薪资） | 薪资管理使用 |
| `updateEmployeeSalaryById` | 更新薪资关联 | 员工-薪资关系 |
| `getEmployeeById` | 根据ID查询 | 含所有关联信息 |

---

## 9️⃣ 常量定义（MailConstants）

```java
public class MailConstants {
    // 消息投递状态
    public static final Integer DELIVERING = 0;  // 投递中
    public static final Integer SUCCESS = 1;     // 投递成功
    public static final Integer FAILURE = 2;     // 投递失败

    // 消息超时时间（分钟）
    public static final Integer MSG_TIMEOUT = 1;

    // MQ相关
    public static final String MAIL_QUEUE_NAME = "javaboy.mail.queue";
    public static final String MAIL_EXCHANGE_NAME = "javaboy.mail.exchange";
    public static final String MAIL_ROUTING_KEY_NAME = "javaboy.mail.routing.key";
}
```

---

## 🔟 与其他模块的关系

```text
┌─────────────────────────────────────────────────────────────┐
│                    EmployeeService 依赖关系                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  EmployeeMapper (数据访问)                                   │
│       ↑                                                     │
│  EmployeeService                                            │
│       ↓                                                     │
│  RabbitTemplate (消息发送) → RabbitMQ → MailReceiver        │
│       ↓                                                     │
│  MailSendLogService (日志记录)                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
