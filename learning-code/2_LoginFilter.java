package org.javaboy.vhr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaboy.vhr.model.Hr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * =====================================================
 * 自定义登录过滤器 - 支持JSON格式登录
 * =====================================================
 *
 * 【为什么需要自定义？】
 * Spring Security默认的UsernamePasswordAuthenticationFilter只支持表单登录
 * (Content-Type: application/x-www-form-urlencoded)
 *
 * 但前后端分离项目中，前端通常发送JSON格式的数据
 * (Content-Type: application/json)
 *
 * 所以需要自定义过滤器来支持JSON格式的登录请求
 *
 * 【处理流程】
 * 1. 拦截登录请求 (/doLogin)
 * 2. 判断请求类型（JSON还是表单）
 * 3. 解析用户名、密码、验证码
 * 4. 校验验证码
 * 5. 执行认证
 */
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    @Autowired
    SessionRegistry sessionRegistry;  // 会话注册表，用于管理用户会话

    /**
     * 【核心方法】尝试认证
     *
     * 这个方法会在用户提交登录请求时被调用
     * 负责从请求中提取用户名、密码，并进行认证
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        // ========== 第一步：验证请求方法 ==========
        // 登录只接受POST请求
        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException(
                    "Authentication method not supported: " + request.getMethod());
        }

        // ========== 第二步：获取Session中的验证码 ==========
        // 验证码在用户请求验证码图片时就已经存入Session
        String verify_code = (String) request.getSession().getAttribute("verify_code");

        // ========== 第三步：判断请求类型并处理 ==========
        // 检查Content-Type是否为JSON格式
        if (request.getContentType().contains(MediaType.APPLICATION_JSON_VALUE)
            || request.getContentType().contains(MediaType.APPLICATION_JSON_UTF8_VALUE)) {

            // ========== JSON格式请求处理 ==========
            Map<String, String> loginData = new HashMap<>();
            try {
                // 使用Jackson的ObjectMapper从请求体中读取JSON数据
                // 前端发送的格式：{"username": "admin", "password": "123", "code": "abcd"}
                loginData = new ObjectMapper().readValue(request.getInputStream(), Map.class);
            } catch (IOException e) {
                // 解析失败时忽略，后续会处理空值情况
            } finally {
                // 获取用户输入的验证码
                String code = loginData.get("code");
                // 校验验证码
                checkCode(response, code, verify_code);
            }

            // 从JSON数据中获取用户名和密码
            // getUsernameParameter() 默认返回 "username"
            // getPasswordParameter() 默认返回 "password"
            String username = loginData.get(getUsernameParameter());
            String password = loginData.get(getPasswordParameter());

            // 处理空值情况
            if (username == null) {
                username = "";
            }
            if (password == null) {
                password = "";
            }
            username = username.trim();  // 去除用户名前后空格

            // ========== 创建认证令牌 ==========
            // UsernamePasswordAuthenticationToken是Spring Security用于存储用户名密码的标准类
            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(
                    username, password);

            // 设置认证详情（包含IP地址、Session ID等信息）
            setDetails(request, authRequest);

            // ========== 注册会话 ==========
            // 将用户会话注册到SessionRegistry中
            // 这是实现单点登录的关键步骤
            Hr principal = new Hr();
            principal.setUsername(username);
            sessionRegistry.registerNewSession(request.getSession(true).getId(), principal);

            // ========== 执行认证 ==========
            // 调用AuthenticationManager进行认证
            // 认证过程：AuthenticationManager → AuthenticationProvider → UserDetailsService
            return this.getAuthenticationManager().authenticate(authRequest);

        } else {
            // ========== 表单格式请求处理 ==========
            // 如果不是JSON格式，则按传统表单方式处理
            checkCode(response, request.getParameter("code"), verify_code);
            // 调用父类方法处理表单登录
            return super.attemptAuthentication(request, response);
        }
    }

    /**
     * 【验证码校验方法】
     *
     * @param resp        响应对象
     * @param code        用户输入的验证码
     * @param verify_code Session中存储的正确验证码
     */
    public void checkCode(HttpServletResponse resp, String code, String verify_code) {
        // ========== 开发环境跳过验证码 ==========
        // 【注意】生产环境必须删除这行！
        // 输入 "dev" 作为验证码可以跳过校验，方便开发调试
        if (code != null && "dev".equalsIgnoreCase(code)) return;

        // ========== 验证码校验逻辑 ==========
        // 校验条件：
        // 1. 用户输入的验证码不能为空
        // 2. Session中的验证码不能为空（可能过期或未获取）
        // 3. 两者必须相等（忽略大小写）
        if (code == null || verify_code == null || "".equals(code)
            || !verify_code.toLowerCase().equals(code.toLowerCase())) {
            // 验证码不正确，抛出认证异常
            // 这个异常会被SecurityConfig中配置的失败处理器捕获
            throw new AuthenticationServiceException("验证码不正确");
        }
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【继承关系】
 *    LoginFilter
 *      ↓ extends
 *    UsernamePasswordAuthenticationFilter
 *      ↓ extends
 *    AbstractAuthenticationProcessingFilter
 *
 * 2. 【JSON登录的关键】
 *    - 判断Content-Type是否为application/json
 *    - 使用ObjectMapper从InputStream读取JSON数据
 *    - 手动解析username、password字段
 *
 * 3. 【认证流程】
 *    用户请求 → LoginFilter.attemptAuthentication()
 *            → AuthenticationManager.authenticate()
 *            → AuthenticationProvider.authenticate()
 *            → UserDetailsService.loadUserByUsername()
 *            → 返回UserDetails对象
 *            → 密码比对
 *            → 认证成功/失败
 *
 * 4. 【验证码校验时机】
 *    在解析完JSON后、执行认证前进行验证码校验
 *    这样可以避免无效的数据库查询
 *
 * 5. 【会话注册】
 *    sessionRegistry.registerNewSession() 的作用：
 *    - 记录用户的会话信息
 *    - 配合ConcurrentSessionFilter实现单点登录
 *    - 当同一用户再次登录时，可以踢掉之前的会话
 */
