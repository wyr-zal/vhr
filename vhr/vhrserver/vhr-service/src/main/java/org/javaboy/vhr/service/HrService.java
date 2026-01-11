package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.HrMapper;
import org.javaboy.vhr.mapper.HrRoleMapper;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.utils.HrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 人力资源（Hr）核心业务服务类
 * 核心功能：
 * 1. 实现Spring Security的UserDetailsService接口，提供用户认证所需的用户信息（核心）
 * 2. 处理Hr的增删改查、角色分配、密码修改、头像更新等业务逻辑
 * 3. 基于当前登录用户的权限，实现数据隔离（如查询时排除自身）
 * 技术要点：
 * - 集成Spring Security完成用户认证
 * - 使用BCryptPasswordEncoder进行密码加密/验证
 * - 事务注解保证批量操作的数据一致性
 * - MyBatis通用方法实现数据库操作
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-09-20 8:21
 */
@Service // 标记为Spring服务层组件，交由IOC容器管理，支持依赖注入和事务管理
public class HrService implements UserDetailsService {

    // 注入Hr数据访问层（Mapper），封装Hr表的CRUD操作
    @Autowired
    HrMapper hrMapper;
    // 注入Hr与角色关联的映射器，处理hr_role中间表（多对多关系）的操作
    @Autowired
    HrRoleMapper hrRoleMapper;

    /**
     * Spring Security 核心认证方法：根据用户名加载用户详情
     * 触发时机：用户登录时，Security框架自动调用此方法获取用户信息进行认证
     * 核心逻辑：查询用户基础信息 + 关联角色信息，为认证和授权提供数据
     * @param username 前端传入的登录用户名（账号）
     * @return UserDetails 接口实现类（Hr类已实现该接口），包含用户名、加密密码、角色列表等核心信息
     * @throws UsernameNotFoundException 用户名不存在时抛出该异常，由Security框架捕获并返回登录失败提示
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 从数据库查询用户基础信息（账号、加密密码、用户状态等）
        Hr hr = hrMapper.loadUserByUsername(username);

        // 2. 用户名不存在校验：抛出Security规范的异常，统一由框架处理
        if (hr == null) {
            throw new UsernameNotFoundException("用户名不存在!");
        }

        // 3. 加载该用户关联的角色列表（如ROLE_ADMIN、ROLE_USER），设置到Hr对象中
        //    角色信息用于后续的权限控制（如URL级别的权限校验）
        hr.setRoles(hrMapper.getHrRolesById(hr.getId()));

        // 4. 返回包含角色信息的Hr对象，Security会自动校验密码和状态
        return hr;
    }

    /**
     * 获取所有Hr列表（排除当前登录用户），支持关键词模糊查询
     * 适用场景：管理员后台查询/管理用户列表，避免查询到自己
     * @param keywords 搜索关键词（支持模糊匹配用户名、姓名、手机号等字段）
     * @return 符合条件的Hr列表（已排除当前登录用户）
     */
    public List<Hr> getAllHrs(String keywords) {
        // HrUtils.getCurrentHr()：自定义工具类，从Security上下文获取当前登录的Hr对象
        // 传入当前用户ID，让Mapper层SQL排除该用户；keywords为模糊查询参数
        return hrMapper.getAllHrs(HrUtils.getCurrentHr().getId(), keywords);
    }

    /**
     * 更新Hr基础信息（选择性更新，仅更新非null字段）
     * 优势：避免更新时覆盖未传递的字段（如仅修改姓名时，不改动密码、手机号等）
     * @param hr 待更新的Hr对象（必须包含id字段，其他字段为需要更新的内容）
     * @return 数据库受影响的行数：1=更新成功，0=无数据变更/更新失败（如id不存在）
     */
    public Integer updateHr(Hr hr) {
        // updateByPrimaryKeySelective：MyBatis通用Mapper方法
        // 原理：根据主键（id）更新，仅更新实体中非null的字段
        return hrMapper.updateByPrimaryKeySelective(hr);
    }

    /**
     * 批量更新Hr的角色（先删后加策略，保证角色数据的准确性）
     * @Transactional：声明式事务注解，保证删除和新增操作的原子性
     *                （删除成功但新增失败时，事务回滚，避免角色数据不完整）
     * @param hrid Hr的主键ID（要分配角色的用户ID）
     * @param rids 角色ID数组（要分配给该用户的角色列表，如[1,2]表示分配管理员+普通用户角色）
     * @return 布尔值：true=更新成功，false=更新失败（新增行数与角色数组长度不一致）
     */
    @Transactional(rollbackFor = Exception.class) // 补充：指定所有异常都回滚（默认仅运行时异常）
    public boolean updateHrRole(Integer hrid, Integer[] rids) {
        // 第一步：删除该用户原有的所有角色关联（清空hr_role中间表中该用户的记录）
        hrRoleMapper.deleteByHrid(hrid);

        // 第二步：批量添加新的角色关联，并校验新增行数是否等于角色数组长度
        // 保证所有传入的角色都成功分配，避免部分分配的情况
        return hrRoleMapper.addRole(hrid, rids) == rids.length;
    }

    /**
     * 根据ID物理删除Hr用户
     * 注意：实际生产中建议使用逻辑删除（如is_deleted字段），避免数据丢失
     * @param id 要删除的Hr主键ID
     * @return 数据库受影响的行数：1=删除成功，0=删除失败（如id不存在）
     */
    public Integer deleteHrById(Integer id) {
        // deleteByPrimaryKey：MyBatis通用Mapper方法，根据主键物理删除记录
        return hrMapper.deleteByPrimaryKey(id);
    }

    /**
     * 获取除当前登录用户外的所有Hr列表（无关键词过滤）
     * 适用场景：如给其他用户分配任务、发送通知等，排除当前用户
     * @return 所有Hr列表（已排除当前登录用户）
     */
    public List<Hr> getAllHrsExceptCurrentHr() {
        // 传入当前登录用户ID，Mapper层SQL排除该用户
        return hrMapper.getAllHrsExceptCurrentHr(HrUtils.getCurrentHr().getId());
    }

    /**
     * 【方法名笔误修正】更新Hr信息（与updateHr逻辑完全一致，疑似开发时的笔误）
     * 注：原方法名updateHyById应为updateHrById，"Hy"是"Hr"的拼写错误
     * 建议：实际开发中应删除该重复方法，统一使用updateHr()
     * @param hr 待更新的Hr对象（必须包含id字段）
     * @return 数据库受影响的行数：1=成功，0=失败
     */
    public Integer updateHyById(Hr hr) {
        // 复用updateByPrimaryKeySelective方法，逻辑与updateHr完全相同
        return hrMapper.updateByPrimaryKeySelective(hr);
    }

    /**
     * 修改Hr密码（验证原密码 + 加密新密码，保证密码修改的安全性）
     * 核心安全设计：
     * 1. 必须验证原密码，防止未授权修改
     * 2. 新密码通过BCrypt加密后存储，不存储明文
     * @param oldpass 前端传入的原密码（明文）
     * @param pass 前端传入的新密码（明文）
     * @param hrid 要修改密码的Hr主键ID
     * @return 布尔值：true=密码修改成功，false=原密码错误/更新失败
     */
    public boolean updateHrPasswd(String oldpass, String pass, Integer hrid) {
        // 1. 根据ID查询用户当前的加密密码（从数据库获取）
        Hr hr = hrMapper.selectByPrimaryKey(hrid);

        // 2. 初始化BCrypt密码加密器（与Security配置中的加密器保持一致）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 3. 验证原密码：matches方法自动将明文oldpass与数据库中的密文对比
        if (encoder.matches(oldpass, hr.getPassword())) {
            // 4. 加密新密码：BCrypt算法自动生成随机盐值，无需手动处理
            String encodePass = encoder.encode(pass);

            // 5. 更新密码到数据库（专用的updatePasswd方法，仅更新password字段）
            Integer result = hrMapper.updatePasswd(hrid, encodePass);

            // 6. 校验更新结果：受影响行数为1表示更新成功
            if (result == 1) {
                return true;
            }
        }

        // 原密码错误 或 更新失败时返回false
        return false;
    }

    /**
     * 更新Hr的头像地址
     * 适用场景：用户上传头像后，更新数据库中的头像URL
     * @param url 新头像的存储URL（如OSS地址、本地服务器路径）
     * @param id 要更新头像的Hr主键ID
     * @return 数据库受影响的行数：1=成功，0=失败
     */
    public Integer updateUserface(String url, Integer id) {
        // 专用的updateUserface方法，仅更新userface（头像）字段
        return hrMapper.updateUserface(url, id);
    }
}