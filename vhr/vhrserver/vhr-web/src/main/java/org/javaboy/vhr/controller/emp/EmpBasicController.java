package org.javaboy.vhr.controller.emp;

import org.javaboy.vhr.model.*;
import org.javaboy.vhr.service.*;
import org.javaboy.vhr.utils.POIUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-10-29 7:41
 */
@RestController
@RequestMapping("/employee/basic")  // 基础员工管理模块的请求映射路径
public class EmpBasicController {

    @Autowired
    EmployeeService employeeService;  // 注入员工服务层
    @Autowired
    NationService nationService;  // 注入民族服务层
    @Autowired
    PoliticsstatusService politicsstatusService;  // 注入政治面貌服务层
    @Autowired
    JobLevelService jobLevelService;  // 注入职级服务层
    @Autowired
    PositionService positionService;  // 注入职位服务层
    @Autowired
    DepartmentService departmentService;  // 注入部门服务层

    /**
     * 分页获取员工列表
     * @param page 当前页数，默认为1
     * @param size 每页显示条数，默认为10
     * @param employee 员工查询条件，可以为空
     * @param beginDateScope 入职日期范围，可以为空
     * @return 返回分页的员工信息
     */
    @GetMapping("/")
    public RespPageBean getEmployeeByPage(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          Employee employee,
                                          Date[] beginDateScope) {
        return employeeService.getEmployeeByPage(page, size, employee, beginDateScope);
    }

    /**
     * 添加一个员工
     * @param employee 员工对象
     * @return 返回添加结果
     */
    @PostMapping("/")
    public RespBean addEmp(@RequestBody Employee employee) {
        if (employeeService.addEmp(employee) == 1) {
            return RespBean.ok("添加成功!");  // 添加成功返回
        }
        return RespBean.error("添加失败!");  // 添加失败返回
    }

    /**
     * 根据员工ID删除员工
     * @param id 员工ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public RespBean deleteEmpByEid(@PathVariable Integer id) {
        if (employeeService.deleteEmpByEid(id) == 1) {
            return RespBean.ok("删除成功!");  // 删除成功返回
        }
        return RespBean.error("删除失败!");  // 删除失败返回
    }

    /**
     * 更新员工信息
     * @param employee 员工对象
     * @return 更新结果
     */
    @PutMapping("/")
    public RespBean updateEmp(@RequestBody Employee employee) {
        if (employeeService.updateEmp(employee) == 1) {
            return RespBean.ok("更新成功!");  // 更新成功返回
        }
        return RespBean.error("更新失败!");  // 更新失败返回
    }

    /**
     * 获取所有民族
     * @return 返回所有民族信息
     */
    @GetMapping("/nations")
    public List<Nation> getAllNations() {
        return nationService.getAllNations();
    }

    /**
     * 获取所有政治面貌
     * @return 返回所有政治面貌信息
     */
    @GetMapping("/politicsstatus")
    public List<Politicsstatus> getAllPoliticsstatus() {
        return politicsstatusService.getAllPoliticsstatus();
    }

    /**
     * 获取所有职级
     * @return 返回所有职级信息
     */
    @GetMapping("/joblevels")
    public List<JobLevel> getAllJobLevels() {
        return jobLevelService.getAllJobLevels();
    }

    /**
     * 获取所有职位
     * @return 返回所有职位信息
     */
    @GetMapping("/positions")
    public List<Position> getAllPositions() {
        return positionService.getAllPositions();
    }

    /**
     * 获取最大工作ID（以当前最大工作ID为基础+1）
     * @return 返回下一个可用的工作ID
     */
    @GetMapping("/maxWorkID")
    public RespBean maxWorkID() {
        RespBean respBean = RespBean.build().setStatus(200)
                .setObj(String.format("%08d", employeeService.maxWorkID() + 1));  // 格式化为8位数
        return respBean;
    }

    /**
     * 获取所有部门信息
     * @return 返回所有部门信息
     */
    @GetMapping("/deps")
    public List<Department> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    /**
     * 导出所有员工数据为Excel文件
     * @return 导出的Excel文件内容
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportData() {
        // 获取所有员工数据
        List<Employee> list = (List<Employee>) employeeService.getEmployeeByPage(null, null, new Employee(), null).getData();
        return POIUtils.employee2Excel(list);  // 使用POI工具类将员工数据导出为Excel文件
    }

    /**
     * 导入员工数据（通过Excel文件）
     * @param file 上传的Excel文件
     * @return 导入结果
     * @throws IOException 文件处理异常
     */
    @PostMapping("/import")
    public RespBean importData(MultipartFile file) throws IOException {
        // 使用POI工具类将Excel文件转换为员工对象列表
        List<Employee> list = POIUtils.excel2Employee(file,
                nationService.getAllNations(),
                politicsstatusService.getAllPoliticsstatus(),
                departmentService.getAllDepartmentsWithOutChildren(),
                positionService.getAllPositions(),
                jobLevelService.getAllJobLevels());

        // 批量插入员工数据
        if (employeeService.addEmps(list) == list.size()) {
            return RespBean.ok("上传成功");  // 上传成功返回
        }
        return RespBean.error("上传失败");  // 上传失败返回
    }
}
