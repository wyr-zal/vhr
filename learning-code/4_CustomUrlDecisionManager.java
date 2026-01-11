package org.javaboy.vhr.config;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * =====================================================
 * 自定义URL权限决策管理器 - 判断用户是否有权访问
 * =====================================================
 *
 * 【类的作用】
 * AccessDecisionManager是Spring Security权限控制的核心组件
 * 它的职责是：判断当前用户是否有权限访问当前请求的资源
 *
 * 【工作原理】
 * 1. CustomFilterInvocationSecurityMetadataSource 告诉系统：这个URL需要哪些角色
 * 2. CustomUrlDecisionManager 判断：当前用户是否拥有这些角色
 *
 * 【三个输入参数】
 * - authentication：当前用户的认证信息（包含用户拥有的角色）
 * - object：被访问的资源（URL）
 * - configAttributes：访问该资源需要的角色（由SecurityMetadataSource提供）
 *
 * 【决策结果】
 * - 如果有权访问：方法正常返回，请求继续
 * - 如果无权访问：抛出AccessDeniedException异常，请求被拒绝
 */
@Component
public class CustomUrlDecisionManager implements AccessDecisionManager {

    /**
     * 【核心决策方法】判断当前用户是否有权限访问目标资源
     *
     * @param authentication   当前用户的认证信息
     *                        - 已登录用户：包含用户名、角色列表等信息
     *                        - 未登录用户：AnonymousAuthenticationToken（匿名令牌）
     *
     * @param object          被访问的目标资源
     *                        - 在Web项目中通常是FilterInvocation
     *                        - 包含请求URL、HTTP方法等信息
     *
     * @param configAttributes 访问该资源需要的角色列表
     *                        - 由CustomFilterInvocationSecurityMetadataSource提供
     *                        - 例如：[ROLE_ADMIN, ROLE_MANAGER]
     *
     * @throws AccessDeniedException 权限不足时抛出
     * @throws InsufficientAuthenticationException 认证信息不足时抛出
     */
    @Override
    public void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes)
            throws AccessDeniedException, InsufficientAuthenticationException {

        // ========== 遍历访问当前资源所需的所有角色 ==========
        // configAttributes 可能包含多个角色，只要用户拥有其中一个就允许访问
        for (ConfigAttribute configAttribute : configAttributes) {

            // 获取当前需要的角色名称
            String needRole = configAttribute.getAttribute();

            // ========== 情况1：处理 ROLE_LOGIN 标识 ==========
            // ROLE_LOGIN 是一个特殊标识，表示"只需登录即可访问，无需特定角色"
            // 这个标识由SecurityMetadataSource在没有匹配到菜单时返回
            if ("ROLE_LOGIN".equals(needRole)) {

                // 判断用户是否已登录
                // AnonymousAuthenticationToken 表示匿名用户（未登录）
                if (authentication instanceof AnonymousAuthenticationToken) {
                    // 未登录，抛出访问被拒绝异常
                    throw new AccessDeniedException("尚未登录，请登录!");
                } else {
                    // 已登录，直接放行
                    return;
                }
            }

            // ========== 情况2：处理需要特定角色的情况 ==========
            // 获取当前用户拥有的所有角色
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

            /*
             * authorities 示例：
             * [
             *   GrantedAuthority { authority: "ROLE_ADMIN" },
             *   GrantedAuthority { authority: "ROLE_EMPLOYEE" }
             * ]
             */

            // 遍历用户的角色，检查是否包含所需角色
            for (GrantedAuthority authority : authorities) {
                // 如果用户拥有该角色，允许访问
                if (authority.getAuthority().equals(needRole)) {
                    return;  // 直接返回，表示有权访问
                }
            }
        }

        // ========== 所有角色都不匹配，拒绝访问 ==========
        // 走到这里说明：用户没有访问该URL所需的任何一个角色
        throw new AccessDeniedException("权限不足，请联系管理员!");
    }

    /**
     * 是否支持传入的ConfigAttribute
     *
     * @param attribute 权限配置项
     * @return 返回true表示支持
     */
    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    /**
     * 是否支持传入的资源类型
     *
     * @param clazz 资源类型
     * @return 返回true表示支持
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
 * 1. 【AccessDecisionManager的职责】
 *    接收三个输入：用户认证信息、请求资源、所需角色
 *    输出决策结果：允许访问（正常返回）或 拒绝访问（抛异常）
 *
 * 2. 【决策逻辑】
 *
 *    if (需要的角色是ROLE_LOGIN) {
 *        if (用户未登录) {
 *            拒绝访问
 *        } else {
 *            允许访问
 *        }
 *    } else {
 *        遍历用户角色 {
 *            if (用户拥有所需角色) {
 *                允许访问
 *            }
 *        }
 *        拒绝访问（用户没有任何所需角色）
 *    }
 *
 * 3. 【关键类说明】
 *
 *    Authentication - 认证信息接口
 *    ├── getPrincipal() - 获取用户主体（通常是UserDetails对象）
 *    ├── getCredentials() - 获取凭证（密码）
 *    └── getAuthorities() - 获取用户拥有的权限/角色列表
 *
 *    GrantedAuthority - 已授予的权限/角色
 *    └── getAuthority() - 获取权限/角色名称，如 "ROLE_ADMIN"
 *
 *    AnonymousAuthenticationToken - 匿名认证令牌
 *    └── 表示未登录的用户
 *
 * 4. 【与SecurityMetadataSource的协作】
 *
 *    请求到达 → SecurityMetadataSource.getAttributes()
 *            返回：["ROLE_ADMIN", "ROLE_MANAGER"]
 *            ↓
 *            AccessDecisionManager.decide()
 *            输入：用户认证信息 + ["ROLE_ADMIN", "ROLE_MANAGER"]
 *            ↓
 *            遍历比对，决定是否放行
 *
 * 5. 【为什么只要匹配一个角色就放行？】
 *    这是"或"的关系：用户拥有 ROLE_ADMIN 或 ROLE_MANAGER 都可以访问
 *    这是最常见的权限控制策略
 *    如果需要"且"的关系，需要修改决策逻辑
 *
 * 6. 【扩展思考】
 *    Spring Security提供了三种投票器（Voter）策略：
 *    - AffirmativeBased：有一个投赞成票就通过（默认）
 *    - ConsensusBased：赞成票多于反对票就通过
 *    - UnanimousBased：全部投赞成票才通过
 *
 *    本项目的实现类似于 AffirmativeBased 策略
 */
