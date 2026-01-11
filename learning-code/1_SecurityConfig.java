package org.javaboy.vhr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.service.HrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.session.ConcurrentSessionFilter;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * =====================================================
 * Spring Security 核心安全配置类
 * =====================================================
 *
 * 【类的作用】
 * 这是整个项目安全认证的核心配置类，负责：
 * 1. 配置用户认证方式（从数据库加载用户）
 * 2. 配置登录成功/失败的处理逻辑（返回JSON而非页面跳转）
 * 3. 配置动态权限控制（URL和角色的映射关系）
 * 4. 配置会话管理（单点登录，防止同一账号多处登录）
 *
 * 【核心流程】
 * 用户请求 → LoginFilter拦截登录 → 认证成功/失败处理器 → 动态权限校验 → 访问资源
 */
@Configuration  // 标记为Spring配置类
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    HrService hrService;  // 用户服务，实现了UserDetailsService接口，用于从数据库加载用户

    @Autowired
    CustomFilterInvocationSecurityMetadataSource customFilterInvocationSecurityMetadataSource;  // 动态权限元数据源

    @Autowired
    CustomUrlDecisionManager customUrlDecisionManager;  // 权限决策管理器

    /**
     * 【密码编码器配置】
     * 使用BCrypt算法对密码进行加密
     * BCrypt特点：每次加密结果不同（加盐），安全性高
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 【认证管理器配置】
     * 配置用户认证的数据来源
     * 这里使用hrService作为UserDetailsService，从数据库加载用户信息
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(hrService);  // 告诉Spring Security去hrService中查找用户
    }

    /**
     * 【静态资源放行配置】
     * 配置哪些请求可以绑过安全认证，直接访问
     * 通常是静态资源：CSS、JS、图片、验证码等
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(
            "/css/**",       // CSS样式文件
            "/js/**",        // JavaScript文件
            "/index.html",   // 首页
            "/img/**",       // 图片资源
            "/fonts/**",     // 字体文件
            "/favicon.ico",  // 网站图标
            "/verifyCode"    // 验证码接口（需要在登录前获取）
        );
    }

    /**
     * 【自定义登录过滤器配置】
     *
     * 这是前后端分离项目的核心配置！
     * 传统的Spring Security登录成功后会重定向页面，但前后端分离项目需要返回JSON
     */
    @Bean
    LoginFilter loginFilter() throws Exception {
        LoginFilter loginFilter = new LoginFilter();

        // ========== 登录成功处理器 ==========
        // 登录成功后，返回JSON格式的用户信息给前端
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();

            // 从认证信息中获取当前登录的用户对象
            Hr hr = (Hr) authentication.getPrincipal();
            hr.setPassword(null);  // 【安全措施】不要把密码返回给前端！

            // 构建成功响应
            RespBean ok = RespBean.ok("登录成功!", hr);
            String s = new ObjectMapper().writeValueAsString(ok);
            out.write(s);
            out.flush();
            out.close();
        });

        // ========== 登录失败处理器 ==========
        // 登录失败后，返回JSON格式的错误信息，根据不同异常类型返回不同提示
        loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            RespBean respBean = RespBean.error(exception.getMessage());

            // 【重点】根据不同的异常类型，给用户友好的提示信息
            if (exception instanceof LockedException) {
                respBean.setMsg("账户被锁定，请联系管理员!");
            } else if (exception instanceof CredentialsExpiredException) {
                respBean.setMsg("密码过期，请联系管理员!");
            } else if (exception instanceof AccountExpiredException) {
                respBean.setMsg("账户过期，请联系管理员!");
            } else if (exception instanceof DisabledException) {
                respBean.setMsg("账户被禁用，请联系管理员!");
            } else if (exception instanceof BadCredentialsException) {
                respBean.setMsg("用户名或者密码输入错误，请重新输入!");
            }

            out.write(new ObjectMapper().writeValueAsString(respBean));
            out.flush();
            out.close();
        });

        // 设置认证管理器
        loginFilter.setAuthenticationManager(authenticationManagerBean());

        // 设置登录请求的URL路径
        loginFilter.setFilterProcessesUrl("/doLogin");

        // ========== 会话并发控制 ==========
        // 实现单点登录：同一账号只能在一个地方登录
        ConcurrentSessionControlAuthenticationStrategy sessionStrategy =
            new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry());
        sessionStrategy.setMaximumSessions(1);  // 最大会话数为1，即只允许一处登录
        loginFilter.setSessionAuthenticationStrategy(sessionStrategy);

        return loginFilter;
    }

    /**
     * 【会话注册表】
     * 用于管理所有用户的会话信息
     * 配合会话并发控制，实现单点登录功能
     */
    @Bean
    SessionRegistryImpl sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 【HTTP安全配置】
     * 这是整个安全配置的核心方法
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                // ========== 动态权限配置（核心！）==========
                // 使用ObjectPostProcessor将自定义的权限控制组件注入到过滤器链中
                .withObjectPostProcessor(new ObjectPostProcessor<FilterSecurityInterceptor>() {
                    @Override
                    public <O extends FilterSecurityInterceptor> O postProcess(O object) {
                        // 设置自定义的权限决策管理器
                        object.setAccessDecisionManager(customUrlDecisionManager);
                        // 设置自定义的权限元数据源（根据URL获取需要的角色）
                        object.setSecurityMetadataSource(customFilterInvocationSecurityMetadataSource);
                        return object;
                    }
                })
                .and()
                // ========== 注销配置 ==========
                .logout()
                .logoutSuccessHandler((req, resp, authentication) -> {
                    // 注销成功后返回JSON
                    resp.setContentType("application/json;charset=utf-8");
                    PrintWriter out = resp.getWriter();
                    out.write(new ObjectMapper().writeValueAsString(RespBean.ok("注销成功!")));
                    out.flush();
                    out.close();
                })
                .permitAll()
                .and()
                // ========== 异常处理配置 ==========
                .csrf().disable()  // 禁用CSRF（前后端分离项目通常禁用）
                .exceptionHandling()
                // 未认证时的处理（返回JSON而非重定向到登录页）
                .authenticationEntryPoint((req, resp, authException) -> {
                    resp.setContentType("application/json;charset=utf-8");
                    resp.setStatus(401);  // HTTP 401 未授权
                    PrintWriter out = resp.getWriter();
                    RespBean respBean = RespBean.error("访问失败!");
                    if (authException instanceof InsufficientAuthenticationException) {
                        respBean.setMsg("请求失败，请联系管理员!");
                    }
                    out.write(new ObjectMapper().writeValueAsString(respBean));
                    out.flush();
                    out.close();
                });

        // ========== 会话并发过滤器 ==========
        // 当用户在别处登录时，之前的会话会被踢下线，这里处理被踢下线的响应
        http.addFilterAt(new ConcurrentSessionFilter(sessionRegistry(), event -> {
            HttpServletResponse resp = event.getResponse();
            resp.setContentType("application/json;charset=utf-8");
            resp.setStatus(401);
            PrintWriter out = resp.getWriter();
            out.write(new ObjectMapper().writeValueAsString(
                RespBean.error("您已在另一台设备登录，本次登录已下线!")
            ));
            out.flush();
            out.close();
        }), ConcurrentSessionFilter.class);

        // ========== 替换默认的登录过滤器 ==========
        // 用自定义的LoginFilter替换默认的UsernamePasswordAuthenticationFilter
        http.addFilterAt(loginFilter(), UsernamePasswordAuthenticationFilter.class);
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 前后端分离的关键：所有响应都是JSON格式，不做页面重定向
 *
 * 2. 动态权限控制三剑客：
 *    - CustomFilterInvocationSecurityMetadataSource：根据URL获取需要的角色
 *    - CustomUrlDecisionManager：判断用户是否有权访问
 *    - SecurityConfig：将上述两个组件注入到过滤器链
 *
 * 3. 会话管理：
 *    - SessionRegistry：会话注册表，记录所有在线用户
 *    - ConcurrentSessionControlAuthenticationStrategy：控制并发会话数
 *    - ConcurrentSessionFilter：处理会话过期/被踢下线的情况
 *
 * 4. 异常处理的分类：
 *    - LockedException：账户被锁定
 *    - CredentialsExpiredException：密码过期
 *    - AccountExpiredException：账户过期
 *    - DisabledException：账户被禁用
 *    - BadCredentialsException：用户名或密码错误
 */
