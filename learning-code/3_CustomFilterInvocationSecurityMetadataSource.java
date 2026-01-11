package org.javaboy.vhr.config;

import org.javaboy.vhr.model.Menu;
import org.javaboy.vhr.model.Role;
import org.javaboy.vhr.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.List;

/**
 * =====================================================
 * 自定义权限元数据源 - 动态获取URL所需角色
 * =====================================================
 *
 * 【类的作用】
 * 这个类是动态权限控制的核心组件之一
 * 它的职责是：根据当前请求的URL，查询数据库，返回访问该URL所需要的角色列表
 *
 * 【为什么叫"元数据源"？】
 * 元数据(Metadata)就是"描述数据的数据"
 * 这里的元数据就是：某个URL需要哪些角色才能访问
 *
 * 【传统方式 vs 动态方式】
 *
 * 传统方式（在代码中写死）：
 * http.authorizeRequests()
 *     .antMatchers("/admin/**").hasRole("ADMIN")
 *     .antMatchers("/user/**").hasRole("USER")
 *
 * 动态方式（从数据库读取）：
 * 1. 数据库中存储 URL 和 角色 的对应关系
 * 2. 请求到来时，查询数据库获取所需角色
 * 3. 管理员可以在后台动态修改权限，无需重启应用
 *
 * 【数据库表结构示意】
 * menu表：id, name, url, ...
 * role表：id, name, ...
 * menu_role表：mid, rid  (菜单和角色的多对多关系)
 */
@Component  // 注册为Spring Bean
public class CustomFilterInvocationSecurityMetadataSource implements FilterInvocationSecurityMetadataSource {

    @Autowired
    MenuService menuService;  // 菜单服务，用于查询菜单和角色的对应关系

    /**
     * 【AntPathMatcher - URL路径匹配器】
     *
     * 支持的通配符：
     * - ?  匹配单个字符
     * - *  匹配0个或多个字符（不跨目录）
     * - ** 匹配0个或多个目录
     *
     * 示例：
     * /user/*   匹配 /user/add, /user/delete，不匹配 /user/add/1
     * /user/**  匹配 /user/add, /user/add/1, /user/a/b/c
     */
    AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 【核心方法】根据请求URL获取所需的角色权限
     *
     * @param object FilterInvocation对象，包含当前请求的信息
     * @return 访问该URL所需的角色列表
     * @throws IllegalArgumentException 参数类型错误时抛出
     *
     * 【方法调用时机】
     * 每次有请求到达时，Spring Security都会调用此方法
     * 获取当前URL需要的角色，然后传递给AccessDecisionManager进行决策
     */
    @Override
    public Collection<ConfigAttribute> getAttributes(Object object) throws IllegalArgumentException {

        // ========== 第一步：获取当前请求的URL ==========
        // FilterInvocation封装了HttpServletRequest，可以获取请求URL
        String requestUrl = ((FilterInvocation) object).getRequestUrl();
        // 例如：/system/config/menu 或 /employee/basic/?page=1

        // ========== 第二步：查询所有菜单及其对应的角色 ==========
        // 这里从数据库查询所有菜单，每个菜单包含其对应的角色列表
        // 【优化建议】可以添加缓存，避免每次请求都查数据库
        List<Menu> menus = menuService.getAllMenusWithRole();

        /*
         * 查询结果示例：
         * [
         *   { url: "/system/basic/**", roles: [ROLE_ADMIN] },
         *   { url: "/system/config/**", roles: [ROLE_ADMIN, ROLE_MANAGER] },
         *   { url: "/employee/**", roles: [ROLE_ADMIN, ROLE_EMPLOYEE] }
         * ]
         */

        // ========== 第三步：遍历菜单，找到匹配当前URL的菜单 ==========
        for (Menu menu : menus) {
            // 使用AntPathMatcher进行URL模式匹配
            // 例如：/employee/basic/ 可以匹配 /employee/**
            if (antPathMatcher.match(menu.getUrl(), requestUrl)) {

                // ========== 第四步：获取该菜单对应的角色 ==========
                List<Role> roles = menu.getRoles();

                // 将角色列表转换为字符串数组
                String[] str = new String[roles.size()];
                for (int i = 0; i < roles.size(); i++) {
                    str[i] = roles.get(i).getName();  // 角色名称，如 "ROLE_ADMIN"
                }

                // ========== 第五步：返回角色配置 ==========
                // SecurityConfig.createList() 创建ConfigAttribute列表
                // 这个列表会传递给AccessDecisionManager进行权限决策
                return SecurityConfig.createList(str);
            }
        }

        // ========== 兜底处理：没有匹配到任何菜单 ==========
        // 返回一个特殊标识 "ROLE_LOGIN"，表示只需要登录即可访问
        // 这个标识会在CustomUrlDecisionManager中特殊处理
        return SecurityConfig.createList("ROLE_LOGIN");
    }

    /**
     * 获取所有的权限配置
     *
     * 如果返回null，表示这个SecurityMetadataSource不支持这个方法
     * Spring Security启动时会调用此方法，用于校验配置是否正确
     */
    @Override
    public Collection<ConfigAttribute> getAllConfigAttributes() {
        return null;
    }

    /**
     * 判断是否支持传入的类类型
     *
     * FilterInvocation.class 是Web请求的标准封装类
     * 返回true表示支持处理Web请求
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【接口说明】FilterInvocationSecurityMetadataSource
 *    这是Spring Security提供的接口，用于获取受保护资源的安全元数据
 *    "受保护资源"在Web项目中就是URL
 *    "安全元数据"就是访问该URL需要的角色/权限
 *
 * 2. 【动态权限的优势】
 *    - 权限配置存储在数据库中，可以动态修改
 *    - 无需修改代码和重启应用
 *    - 可以通过后台管理界面配置权限
 *
 * 3. 【URL匹配规则】
 *    使用Spring的AntPathMatcher进行模式匹配
 *    数据库中存储的是URL模式（如 /employee/**）
 *    请求URL与模式匹配时，返回对应的角色
 *
 * 4. 【ROLE_LOGIN的含义】
 *    这是一个自定义的标识，表示"只需登录，无需特定角色"
 *    用于处理没有在数据库中配置的URL
 *    在CustomUrlDecisionManager中会对这个标识做特殊处理
 *
 * 5. 【性能优化建议】
 *    - 添加缓存：将菜单-角色映射关系缓存起来
 *    - 权限变更时刷新缓存
 *    - 可以使用Redis或本地缓存
 *
 * 6. 【与AccessDecisionManager的协作】
 *    本类的职责：告诉系统"访问这个URL需要什么角色"
 *    AccessDecisionManager的职责：判断"当前用户是否拥有这些角色"
 */
