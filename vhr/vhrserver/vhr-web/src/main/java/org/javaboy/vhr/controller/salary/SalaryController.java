package org.javaboy.vhr.controller.salary;

import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.Salary;
import org.javaboy.vhr.service.SalaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 薪资标准管理控制器
 * 处理薪资标准（Salary Of Bill）相关的HTTP请求
 * 提供薪资标准的查询、新增、删除、修改等接口
 * @author javaboy
 * @date 2026/01/08
 */
@RestController
@RequestMapping("/salary/sob")
public class SalaryController {

    /**
     * 注入薪资业务层服务对象
     * 用于调用薪资相关的业务逻辑方法
     */
    @Autowired
    SalaryService salaryService;

    /**
     * 获取所有薪资标准列表
     * @return 薪资标准列表（List<Salary>）
     */
    @GetMapping("/")
    public List<Salary> getAllSalaries() {
        return salaryService.getAllSalaries();
    }

    /**
     * 新增薪资标准
     * @param salary 薪资标准对象（通过RequestBody接收JSON参数）
     * @return RespBean 统一响应结果（包含操作状态和提示信息）
     */
    @PostMapping("/")
    public RespBean addSalary(@RequestBody Salary salary) {
        // 调用服务层新增方法，返回1表示新增成功
        if (salaryService.addSalary(salary) == 1) {
            return RespBean.ok("添加成功!");
        }
        // 新增失败返回错误提示
        return RespBean.error("添加失败!");
    }

    /**
     * 根据ID删除薪资标准
     * @param id 薪资标准ID（通过PathVariable从URL路径中获取）
     * @return RespBean 统一响应结果
     */
    @DeleteMapping("/{id}")
    public RespBean deleteSalaryById(@PathVariable Integer id) {
        // 调用服务层删除方法，返回1表示删除成功
        if (salaryService.deleteSalaryById(id) == 1) {
            return RespBean.ok("删除成功！");
        }
        // 删除失败返回错误提示
        return RespBean.error("删除失败！");
    }

    /**
     * 修改薪资标准
     * @param salary 薪资标准对象（包含修改后的信息和主键ID）
     * @return RespBean 统一响应结果
     */
    @PutMapping("/")
    public RespBean updateSalaryById(@RequestBody Salary salary) {
        // 调用服务层更新方法，返回1表示更新成功
        if (salaryService.updateSalaryById(salary) == 1) {
            return RespBean.ok("更新成功!");
        }
        // 更新失败返回错误提示
        return RespBean.error("更新失败!");
    }
}