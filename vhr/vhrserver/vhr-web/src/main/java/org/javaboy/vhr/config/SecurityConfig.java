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
 * Spring Security 核心配置类
 * 功能：配置认证规则、授权规则、自定义登录/登出逻辑、会话管理、权限决策等
 * 基于 WebSecurityConfigurerAdapter 实现自定义安全配置（适用于Spring Security 5.x 低版本）
 */
@Configuration // 标记为配置类，交由Spring容器管理
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    // 注入自定义的用户详情服务（用于从数据库加载用户信息）
    @Autowired
    HrService hrService;

    // 注入自定义的权限元数据数据源（用于获取访问URL所需的权限）
    @Autowired
    CustomFilterInvocationSecurityMetadataSource customFilterInvocationSecurityMetadataSource;

    // 注入自定义的权限决策管理器（用于判断用户是否有权限访问URL）
    @Autowired
    CustomUrlDecisionManager customUrlDecisionManager;

    /**
     * 配置密码加密器
     * BCryptPasswordEncoder：Spring Security推荐的密码加密算法，不可逆且自带盐值
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置认证管理器
     * 指定用户详情服务（HrService）和密码加密器（默认使用上面配置的BCryptPasswordEncoder）
     * 作用：Spring Security 会通过 HrService.loadUserByUsername() 加载用户信息进行认证
     * @param auth 认证管理器构建器
     * @throws Exception 配置异常
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(hrService);
    }

    /**
     * 配置Web层面的安全忽略规则
     * 对静态资源和验证码接口放行，不经过Spring Security过滤器链
     * @param web Web安全构建器
     * @throws Exception 配置异常
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring() // 忽略以下路径的安全校验
                .antMatchers(
                        "/css/**",    // 样式文件
                        "/js/**",     // 脚本文件
                        "/index.html",// 首页
                        "/img/**",    // 图片资源
                        "/fonts/**",  // 字体文件
                        "/favicon.ico",// 网站图标
                        "/verifyCode" // 验证码接口
                );
    }

    /**
     * 配置自定义登录过滤器
     * 替代默认的 UsernamePasswordAuthenticationFilter，实现JSON格式的登录响应
     * @return 自定义的 LoginFilter 实例
     * @throws Exception 配置异常
     */
    @Bean
    LoginFilter loginFilter() throws Exception {
        LoginFilter loginFilter = new LoginFilter();

        // 配置登录成功处理器：返回JSON格式的成功响应，而非默认的页面跳转
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            // 设置响应格式为JSON，编码UTF-8
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            // 获取认证成功的用户信息（Hr是自定义的用户实体类）
            Hr hr = (Hr) authentication.getPrincipal();
            // 清空密码，避免敏感信息返回前端
            hr.setPassword(null);
            // 构建成功响应对象
            RespBean ok = RespBean.ok("登录成功!", hr);
            // 将响应对象转为JSON字符串并输出
            String s = new ObjectMapper().writeValueAsString(ok);
            out.write(s);
            out.flush();
            out.close();
        });

        // 配置登录失败处理器：根据不同异常类型返回对应的JSON错误提示
        loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            // 初始化错误响应对象
            RespBean respBean = RespBean.error(exception.getMessage());

            // 根据异常类型细化错误提示
            if (exception instanceof LockedException) {
                respBean.setMsg("账户被锁定，请联系管理员!"); // 账户锁定
            } else if (exception instanceof CredentialsExpiredException) {
                respBean.setMsg("密码过期，请联系管理员!"); // 密码过期
            } else if (exception instanceof AccountExpiredException) {
                respBean.setMsg("账户过期，请联系管理员!"); // 账户过期
            } else if (exception instanceof DisabledException) {
                respBean.setMsg("账户被禁用，请联系管理员!"); // 账户禁用
            } else if (exception instanceof BadCredentialsException) {
                respBean.setMsg("用户名或者密码输入错误，请重新输入!"); // 用户名/密码错误
            }

            // 输出错误响应JSON
            out.write(new ObjectMapper().writeValueAsString(respBean));
            out.flush();
            out.close();
        });

        // 设置认证管理器（用于处理登录请求的认证逻辑）
        loginFilter.setAuthenticationManager(authenticationManagerBean());
        // 设置登录接口地址（替代默认的 /login）
        loginFilter.setFilterProcessesUrl("/doLogin");

        // 配置并发会话控制策略：限制同一用户只能同时登录1个会话
        ConcurrentSessionControlAuthenticationStrategy sessionStrategy = new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry());
        sessionStrategy.setMaximumSessions(1); // 最大并发会话数为1
        loginFilter.setSessionAuthenticationStrategy(sessionStrategy);

        return loginFilter;
    }

    /**
     * 配置会话注册表
     * 用于跟踪用户的会话信息，支持并发会话控制（如同一账号多端登录限制）
     * @return SessionRegistryImpl 实例
     */
    @Bean
    SessionRegistryImpl sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 配置HTTP层面的安全规则
     * 包括：授权规则、自定义权限处理器、登出逻辑、CSRF关闭、异常处理、会话过滤器等
     * @param http HTTP安全构建器
     * @throws Exception 配置异常
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                // 配置授权请求规则
                .authorizeRequests()
                // 自定义 FilterSecurityInterceptor（权限拦截器）的属性
                .withObjectPostProcessor(new ObjectPostProcessor<FilterSecurityInterceptor>() {
                    @Override
                    public <O extends FilterSecurityInterceptor> O postProcess(O object) {
                        // 设置自定义的权限决策管理器（判断用户是否有权限访问）
                        object.setAccessDecisionManager(customUrlDecisionManager);
                        // 设置自定义的权限元数据数据源（获取URL所需权限）
                        object.setSecurityMetadataSource(customFilterInvocationSecurityMetadataSource);
                        return object;
                    }
                })
                .and()
                // 配置登出逻辑
                .logout()
                // 自定义登出成功处理器：返回JSON格式的登出成功响应
                .logoutSuccessHandler((req, resp, authentication) -> {
                    resp.setContentType("application/json;charset=utf-8");
                    PrintWriter out = resp.getWriter();
                    out.write(new ObjectMapper().writeValueAsString(RespBean.ok("注销成功!")));
                    out.flush();
                    out.close();
                })
                .permitAll() // 允许所有用户访问登出接口
                .and()
                // 关闭CSRF防护（适用于前后端分离项目，前端不使用Cookie存储会话）
                .csrf().disable()
                // 配置异常处理
                .exceptionHandling()
                // 配置未认证/权限不足时的处理器（返回JSON，而非默认的重定向到登录页）
                .authenticationEntryPoint((req, resp, authException) -> {
                    resp.setContentType("application/json;charset=utf-8");
                    resp.setStatus(401); // 设置响应状态码为401（未授权）
                    PrintWriter out = resp.getWriter();
                    RespBean respBean = RespBean.error("访问失败!");
                    // 细化权限不足的错误提示
                    if (authException instanceof InsufficientAuthenticationException) {
                        respBean.setMsg("请求失败，请联系管理员!");
                    }
                    out.write(new ObjectMapper().writeValueAsString(respBean));
                    out.flush();
                    out.close();
                });

        // 添加并发会话过滤器：处理同一账号多端登录的情况
        http.addFilterAt(new ConcurrentSessionFilter(sessionRegistry(), event -> {
            HttpServletResponse resp = event.getResponse();
            resp.setContentType("application/json;charset=utf-8");
            resp.setStatus(401); // 401状态码表示未授权
            PrintWriter out = resp.getWriter();
            // 提示用户已在其他设备登录
            out.write(new ObjectMapper().writeValueAsString(RespBean.error("您已在另一台设备登录，本次登录已下线!")));
            out.flush();
            out.close();
        }), ConcurrentSessionFilter.class);

        // 将自定义的登录过滤器替换默认的 UsernamePasswordAuthenticationFilter
        http.addFilterAt(loginFilter(), UsernamePasswordAuthenticationFilter.class);
    }
}