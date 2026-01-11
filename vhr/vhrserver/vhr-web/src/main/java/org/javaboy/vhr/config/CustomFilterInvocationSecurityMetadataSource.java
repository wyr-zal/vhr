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
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-09-29 7:37
 *
 * 这个类的作用，主要是根据用户传来的请求地址，分析出请求需要的角色。
 * 它实现了 Spring Security 的 FilterInvocationSecurityMetadataSource 接口，
 * 用来动态根据请求 URL 获取所需的权限角色。
 */
@Component
public class CustomFilterInvocationSecurityMetadataSource implements FilterInvocationSecurityMetadataSource {

    // 注入 MenuService，用于获取所有的菜单和角色信息
    @Autowired
    MenuService menuService;

    // 创建 AntPathMatcher 用于 URL 路径匹配
    AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 根据请求 URL 获取相应的角色权限
     *
     * @param object 请求对象（FilterInvocation），包含了请求 URL 等信息
     * @return 返回需要的角色权限列表（ConfigAttribute），这些角色决定了是否能访问该 URL
     * @throws IllegalArgumentException 如果传入的 object 类型不正确，则抛出异常
     */
    @Override
    public Collection<ConfigAttribute> getAttributes(Object object) throws IllegalArgumentException {
        // 获取当前请求的 URL
        String requestUrl = ((FilterInvocation) object).getRequestUrl();

        // 获取所有菜单和它们对应的角色
        List<Menu> menus = menuService.getAllMenusWithRole();

        // 遍历菜单列表，检查请求 URL 是否与菜单的 URL 匹配
        for (Menu menu : menus) {
            // 如果当前菜单的 URL 与请求的 URL 匹配
            if (antPathMatcher.match(menu.getUrl(), requestUrl)) {
                // 获取该菜单对应的角色列表
                List<Role> roles = menu.getRoles();

                // 创建一个字符串数组来存储角色名称
                String[] str = new String[roles.size()];
                for (int i = 0; i < roles.size(); i++) {
                    str[i] = roles.get(i).getName();
                }

                // 返回需要的角色列表，这些角色决定了该 URL 的访问权限
                return SecurityConfig.createList(str);
            }
        }

        // 如果没有匹配的菜单，则返回一个默认的角色，表示需要登录才能访问
        return SecurityConfig.createList("ROLE_LOGIN");
    }

    /**
     * 获取所有配置的角色权限信息
     *
     * @return 返回所有的角色权限集合
     */
    @Override
    public Collection<ConfigAttribute> getAllConfigAttributes() {
        return null;
    }

    /**
     * 判断当前类是否支持某种类型的对象
     *
     * @param clazz 要判断的类类型
     * @return 如果支持该类类型，则返回 true，否则返回 false
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}
