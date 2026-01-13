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
 * 自定义登录过滤器（扩展Spring Security默认的UsernamePasswordAuthenticationFilter）
 * 核心扩展功能：
 * 1. 支持JSON格式的登录参数（适配前后端分离项目，默认仅支持表单提交）
 * 2. 增加登录验证码校验逻辑
 * 3. 注册用户会话信息到SessionRegistry，支持并发登录控制
 */
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    // 注入会话注册表，用于跟踪用户会话（支持并发登录限制、踢人等功能）
    @Autowired
    SessionRegistry sessionRegistry;

    /**
     * 核心重写方法：处理登录认证请求
     * 扩展逻辑：
     * - 校验请求方法必须为POST
     * - 兼容JSON/表单两种登录参数格式
     * - 增加验证码校验
     * - 注册用户会话信息
     * @param request 登录请求对象
     * @param response 登录响应对象
     * @return Authentication 认证结果对象（包含用户身份信息）
     * @throws AuthenticationException 认证异常（如验证码错误、用户名密码错误等）
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        // 1. 校验请求方法：仅支持POST，非POST直接抛出异常
        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException(
                    "Authentication method not supported: " + request.getMethod());
        }

        // 2. 从Session中获取生成的验证码（由验证码接口存入Session）
        String verify_code = (String) request.getSession().getAttribute("verify_code");

        // 3. 判断请求内容类型：区分JSON格式和传统表单格式
        if (request.getContentType().contains(MediaType.APPLICATION_JSON_VALUE) ||
                request.getContentType().contains(MediaType.APPLICATION_JSON_UTF8_VALUE)) {

            // ========== 处理JSON格式的登录请求 ==========
            Map<String, String> loginData = new HashMap<>();
            try {
                // 从请求体中读取JSON数据，解析为键值对（包含username/password/code）
                loginData = new ObjectMapper().readValue(request.getInputStream(), Map.class);
            } catch (IOException e) {
                // 解析JSON失败时，loginData保持空Map，后续参数校验会处理
                e.printStackTrace();
            } finally {
                // 无论JSON解析是否成功，都校验验证码（finally保证必执行）
                String code = loginData.get("code"); // 获取前端传入的验证码
                checkCode(response, code, verify_code);
            }

            // 4. 从JSON中提取用户名和密码（兼容框架默认的参数名，可通过配置修改）
            String username = loginData.get(getUsernameParameter()); // 默认参数名：username
            String password = loginData.get(getPasswordParameter()); // 默认参数名：password

            // 5. 参数空值处理：避免空指针，空值转为空字符串
            if (username == null) {
                username = "";
            }
            if (password == null) {
                password = "";
            }
            username = username.trim(); // 去除用户名前后空格

            // 6. 构建未认证的令牌对象（仅封装用户名和密码，未经过认证）
            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(
                    username, password);

            // 7. 设置请求详情（如远程IP、会话ID等），供后续认证使用
            setDetails(request, authRequest);

            // 8. 注册用户会话到SessionRegistry（用于并发登录控制）
            Hr principal = new Hr(); // 构建用户主体对象
            principal.setUsername(username); // 暂存用户名（认证成功后会替换为完整Hr对象）
            // 注册新会话：会话ID + 用户主体，框架会跟踪该用户的所有会话
            sessionRegistry.registerNewSession(request.getSession(true).getId(), principal);

            // 9. 交给认证管理器（AuthenticationManager）执行真正的认证逻辑（校验用户名密码）
            return this.getAuthenticationManager().authenticate(authRequest);
        } else {
            // ========== 处理传统表单格式的登录请求 ==========
            // 从请求参数中获取验证码，校验验证码
            checkCode(response, request.getParameter("code"), verify_code);
            // 调用父类默认的表单登录处理逻辑
            return super.attemptAuthentication(request, response);
        }
    }

    /**
     * 验证码校验工具方法
     * 核心逻辑：对比前端传入的验证码与Session中存储的验证码（忽略大小写）
     * @param resp 响应对象（暂未使用，可扩展返回自定义JSON提示）
     * @param code 前端传入的验证码（用户输入）
     * @param verify_code Session中存储的真实验证码（系统生成）
     * @throws AuthenticationServiceException 验证码错误时抛出该异常，触发登录失败
     */
    public void checkCode(HttpServletResponse resp, String code, String verify_code) {
        // TODO: 开发环境临时跳过验证码校验，生产环境请删除此行
        // 开发时输入code=dev即可绕过验证码，方便调试
        if ("dev".equalsIgnoreCase(code)) return;

        // 验证码校验逻辑：
        // 1. 前端传入的code为空 或 Session中的验证码为空
        // 2. code为空字符串 或 大小写不匹配
        if (verify_code == null || "".equals(code) ||
                !verify_code.equalsIgnoreCase(code)) {
            // 抛出认证服务异常，框架会捕获并触发登录失败处理器
            throw new AuthenticationServiceException("验证码不正确");
        }
    }
}