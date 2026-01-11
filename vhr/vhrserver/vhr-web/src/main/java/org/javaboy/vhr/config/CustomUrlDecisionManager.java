package org.javaboy.vhr.config;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.util.Collection;

/**
 * 自定义URL权限决策管理器
 * 核心作用：根据用户的认证信息和请求资源所需的权限，判断用户是否有权限访问该资源
 * AccessDecisionManager 是 Spring Security 中用于决策访问控制的核心接口
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-09-29 7:53
 */
@Component // 交给Spring容器管理，成为全局可用的Bean
public class CustomUrlDecisionManager implements AccessDecisionManager {

    /**
     * 核心决策方法：判断当前用户是否有权限访问目标资源
     * @param authentication  当前用户的认证信息（包含用户身份、拥有的权限列表等）
     * @param object          被访问的目标资源（通常是 FilterInvocation 对象，包含请求URL、请求方法等）
     * @param configAttributes 访问目标资源所需的权限列表（由 UrlFilterInvocationSecurityMetadataSource 提供）
     * @throws AccessDeniedException 访问被拒绝异常（权限不足/未登录）
     * @throws InsufficientAuthenticationException 认证信息不足异常
     */
    @Override
    public void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes)
            throws AccessDeniedException, InsufficientAuthenticationException {
        // 遍历访问当前资源所需的所有权限
        for (ConfigAttribute configAttribute : configAttributes) {
            // 获取当前资源所需的具体权限标识（如 ROLE_ADMIN、ROLE_LOGIN 等）
            String needRole = configAttribute.getAttribute();

            // 1. 处理"仅需登录即可访问"的场景（ROLE_LOGIN 是自定义的标识）
            if ("ROLE_LOGIN".equals(needRole)) {
                // AnonymousAuthenticationToken 表示匿名用户（未登录）
                if (authentication instanceof AnonymousAuthenticationToken) {
                    throw new AccessDeniedException("尚未登录，请登录!"); // 未登录则抛出拒绝访问异常
                } else {
                    return; // 已登录，直接允许访问，结束权限校验
                }
            }

            // 2. 处理"需要特定角色/权限"的场景
            // 获取当前用户拥有的所有权限/角色
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            // 遍历用户的权限，判断是否包含访问当前资源所需的权限
            for (GrantedAuthority authority : authorities) {
                // 如果用户拥有该权限，直接允许访问，结束权限校验
                if (authority.getAuthority().equals(needRole)) {
                    return;
                }
            }
        }
        // 遍历完所有所需权限后，用户仍无匹配权限，抛出拒绝访问异常
        throw new AccessDeniedException("权限不足，请联系管理员!");
    }

    /**
     * 表示当前决策管理器是否支持传入的 ConfigAttribute（权限配置项）
     * 返回true表示支持，Spring Security 会使用该管理器处理对应的权限校验
     * @param attribute 权限配置项
     * @return 始终返回true，表示支持所有权限配置项
     */
    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    /**
     * 表示当前决策管理器是否支持处理指定类型的资源对象
     * 返回true表示支持，
    Spring Security 会将该类型的资源交给此管理器处理
     * @param clazz 资源对象的类型（如 FilterInvocation.class）
     * @return 始终返回true，表示支持所有类型的资源对象
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}