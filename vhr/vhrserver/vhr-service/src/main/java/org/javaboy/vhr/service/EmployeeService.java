package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.MailConstants;
import org.javaboy.vhr.model.MailSendLog;
import org.javaboy.vhr.model.RespPageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 员工业务层服务类
 * 处理员工信息的增删改查、薪资关联、分页查询等核心业务逻辑
 * 同时集成RabbitMQ消息发送，用于新增员工时触发邮件通知
 *
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-10-29 7:44
 */
@Service // 标记为Spring服务层组件，交由Spring容器管理
public class EmployeeService {

    // ====================== 依赖注入 ======================
    /**
     * 员工数据访问层Mapper，用于数据库交互
     */
    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * RabbitMQ消息模板，用于发送消息到MQ队列
     */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 邮件发送日志服务，用于记录消息发送状态
     */
    @Autowired
    private MailSendLogService mailSendLogService;

    // ====================== 日志与格式化工具 ======================
    /**
     * 日志记录器，用于打印业务日志（SLF4J + Logback）
     */
    public final static Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    /**
     * 日期格式化工具：仅格式化年份（yyyy），用于计算合同年限
     */
    private final SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");

    /**
     * 日期格式化工具：仅格式化月份（MM），用于计算合同年限
     */
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MM");

    /**
     * 数字格式化工具：保留两位小数，用于合同年限的格式化（如1.50年）
     */
    private final DecimalFormat decimalFormat = new DecimalFormat("##.00");

    // ====================== 核心业务方法 ======================

    /**
     * 分页条件查询员工信息
     * 支持多条件筛选（姓名、部门、职级等）+ 入职日期范围 + 分页
     *
     * @param page           页码（前端传入，从1开始）
     * @param size           每页条数
     * @param employee       筛选条件对象（包含name、politicId、nationId等）
     * @param beginDateScope 入职日期范围数组 [开始日期, 结束日期]
     * @return RespPageBean 分页结果对象（包含数据列表和总条数）
     */
    public RespPageBean getEmployeeByPage(Integer page, Integer size, Employee employee, Date[] beginDateScope) {
        // 分页参数转换：前端页码从1开始，MyBatis的limit需要从0开始的偏移量
        if (page != null && size != null) {
            page = (page - 1) * size;
        }
        // 1. 查询符合条件的员工列表（分页）
        List<Employee> data = employeeMapper.getEmployeeByPage(page, size, employee, beginDateScope);
        // 2. 查询符合条件的员工总数（用于计算总页数）
        Long total = employeeMapper.getTotal(employee, beginDateScope);
        // 3. 封装分页结果对象
        RespPageBean bean = new RespPageBean();
        bean.setData(data); // 设置分页数据列表
        bean.setTotal(total); // 设置总记录数
        return bean;
    }

    /**
     * 新增单个员工
     * 核心逻辑：
     * 1. 计算合同年限（精确到月，转换为年）
     * 2. 插入员工数据到数据库
     * 3. 若插入成功，发送MQ消息触发邮件通知，并记录消息发送日志
     *
     * @param employee 待新增的员工对象（包含合同开始/结束日期等关键信息）
     * @return Integer 数据库受影响行数（1=成功，0=失败）
     */
    public Integer addEmp(Employee employee) {
        // 1. 获取合同开始和结束日期，计算合同年限
        Date beginContract = employee.getBeginContract(); // 合同开始日期
        Date endContract = employee.getEndContract();     // 合同结束日期

        // 计算总月份数：(结束年-开始年)*12 + (结束月-开始月)
        double month = (Double.parseDouble(yearFormat.format(endContract)) - Double.parseDouble(yearFormat.format(beginContract))) * 12
                + (Double.parseDouble(monthFormat.format(endContract)) - Double.parseDouble(monthFormat.format(beginContract)));
        // 转换为年（保留两位小数），设置到员工对象中
        employee.setContractTerm(Double.parseDouble(decimalFormat.format(month / 12)));

        // 2. 插入员工数据（选择性插入，仅插入非null字段）
        int result = employeeMapper.insertSelective(employee);

        // 3. 插入成功则发送MQ消息，触发邮件通知
        if (result == 1) {
            // 3.1 根据新增员工的ID查询完整员工信息（确保获取到所有字段）
            Employee emp = employeeMapper.getEmployeeById(employee.getId());

            // 3.2 生成消息唯一标识（用于消息幂等性和重试）
            String msgId = UUID.randomUUID().toString();

            // 3.3 构建邮件发送日志对象，记录消息发送状态
            MailSendLog mailSendLog = new MailSendLog();
            mailSendLog.setMsgId(msgId);                // 消息唯一ID
            mailSendLog.setCreateTime(new Date());      // 日志创建时间
            mailSendLog.setExchange(MailConstants.MAIL_EXCHANGE_NAME); // MQ交换机名称"javaboy. mail. exchange"
            mailSendLog.setRouteKey(MailConstants.MAIL_ROUTING_KEY_NAME); // 路由键"javaboy. mail. routing. key"
            mailSendLog.setEmpId(emp.getId());          // 关联的员工ID
            // 设置首次重试时间（当前时间 + 消息超时时间，默认1分钟）
            mailSendLog.setTryTime(new Date(System.currentTimeMillis() + 1000 * 60 * MailConstants.MSG_TIMEOUT));

            // 3.4 插入邮件发送日志到数据库
            mailSendLogService.insert(mailSendLog);

            // 3.5 发送MQ消息到邮件队列，携带员工信息和消息ID（用于消息确认）
            rabbitTemplate.convertAndSend(
                    MailConstants.MAIL_EXCHANGE_NAME,  // 交换机名称
                    MailConstants.MAIL_ROUTING_KEY_NAME, // 路由键
                    emp,                                 // 消息体（员工对象）
                    new CorrelationData(msgId)           // 消息关联数据（唯一ID）
            );
        }

        // 返回数据库受影响行数
        return result;
    }

    /**
     * 查询最大工号
     * 用于新增员工时生成唯一的工号（避免重复）
     *
     * @return Integer 数据库中最大的工号
     */
    public Integer maxWorkID() {
        return employeeMapper.maxWorkID();
    }

    /**
     * 根据员工ID删除员工
     *
     * @param id 员工ID
     * @return Integer 数据库受影响行数（1=成功，0=失败）
     */
    public Integer deleteEmpByEid(Integer id) {
        return employeeMapper.deleteByPrimaryKey(id);
    }

    /**
     * 更新员工信息（选择性更新，仅更新非null字段）
     *
     * @param employee 待更新的员工对象（必须包含id，其他字段非null则更新）
     * @return Integer 数据库受影响行数（1=成功，0=失败）
     */
    public Integer updateEmp(Employee employee) {
        return employeeMapper.updateByPrimaryKeySelective(employee);
    }

    /**
     * 批量新增员工
     * 适用于批量导入员工数据的场景
     *
     * @param list 员工对象列表
     * @return Integer 数据库受影响行数（成功插入的记录数）
     */
    public Integer addEmps(List<Employee> list) {
        return employeeMapper.addEmps(list);
    }

    /**
     * 分页查询员工+薪资信息
     * 关联查询员工的薪资详情和部门信息
     *
     * @param page 页码（前端传入，从1开始）
     * @param size 每页条数
     * @return RespPageBean 分页结果对象（包含员工+薪资数据列表和总条数）
     */
    public RespPageBean getEmployeeByPageWithSalary(Integer page, Integer size) {
        // 分页参数转换：前端页码转MyBatis偏移量
        if (page != null && size != null) {
            page = (page - 1) * size;
        }
        // 1. 查询员工+薪资信息列表（分页）
        List<Employee> list = employeeMapper.getEmployeeByPageWithSalary(page, size);
        // 2. 封装分页结果对象
        RespPageBean respPageBean = new RespPageBean();
        respPageBean.setData(list);
        // 总条数：查询所有员工总数（无筛选条件）
        respPageBean.setTotal(employeeMapper.getTotal(null, null));
        return respPageBean;
    }

    /**
     * 更新员工的薪资关联关系
     * 本质是操作empsalary中间表，建立员工ID和薪资ID的关联
     *
     * @param eid 员工ID
     * @param sid 薪资ID
     * @return Integer 数据库受影响行数（1=成功，0=失败）
     */
    public Integer updateEmployeeSalaryById(Integer eid, Integer sid) {
        return employeeMapper.updateEmployeeSalaryById(eid, sid);
    }

    /**
     * 根据员工ID查询员工完整信息
     * 关联查询民族、政治面貌、部门、职级、职位等关联信息
     *
     * @param empId 员工ID
     * @return Employee 完整的员工对象（包含所有关联信息）
     */
    public Employee getEmployeeById(Integer empId) {
        return employeeMapper.getEmployeeById(empId);
    }
}