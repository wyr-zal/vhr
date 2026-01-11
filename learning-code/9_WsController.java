package org.javaboy.vhr.controller;

import org.javaboy.vhr.model.ChatMsg;
import org.javaboy.vhr.model.Hr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Date;

/**
 * =====================================================
 * WebSocket 消息控制器 - 处理聊天消息
 * =====================================================
 *
 * 【类的作用】
 * 这是处理WebSocket消息的控制器
 * 负责接收客户端发送的聊天消息，并转发给目标用户
 *
 * 【与REST Controller的区别】
 *
 * REST Controller：
 * - 使用 @RestController 注解
 * - 使用 @RequestMapping 处理HTTP请求
 * - 请求-响应模式
 *
 * WebSocket Controller：
 * - 使用 @Controller 注解
 * - 使用 @MessageMapping 处理WebSocket消息
 * - 消息推送模式
 *
 * 【消息处理流程】
 *
 *  用户A发送消息
 *       ↓
 *  客户端 stompClient.send('/ws/chat', msg)
 *       ↓
 *  服务器 @MessageMapping("/ws/chat")
 *       ↓
 *  处理消息，设置发送者信息
 *       ↓
 *  simpMessagingTemplate.convertAndSendToUser(目标用户, 目的地, 消息)
 *       ↓
 *  用户B收到消息
 */
@Controller  // 注意：这里用@Controller而不是@RestController
public class WsController {

    /**
     * 【SimpMessagingTemplate - 消息发送模板】
     *
     * 这是Spring WebSocket提供的消息发送工具
     * 主要方法：
     * - convertAndSend(destination, payload) - 发送到指定目的地（广播）
     * - convertAndSendToUser(user, destination, payload) - 发送给指定用户（点对点）
     */
    @Autowired
    SimpMessagingTemplate simpMessagingTemplate;

    /**
     * 【处理聊天消息】
     *
     * @MessageMapping 类似于 @RequestMapping，但用于处理WebSocket消息
     * 当客户端发送消息到 /ws/chat 时，这个方法会被调用
     *
     * @param authentication Spring Security的认证对象，包含当前登录用户信息
     *                      WebSocket会自动继承HTTP会话的认证信息
     * @param chatMsg       聊天消息对象，由客户端发送的JSON自动转换
     */
    @MessageMapping("/ws/chat")
    public void handleMsg(Authentication authentication, ChatMsg chatMsg) {

        // ========== 第一步：获取当前登录用户（消息发送者）==========
        // 从认证对象中获取用户主体
        // 这里的Hr对象是在用户登录时设置的
        Hr hr = (Hr) authentication.getPrincipal();

        // ========== 第二步：设置消息的发送者信息 ==========
        chatMsg.setFrom(hr.getUsername());     // 发送者用户名（用于标识）
        chatMsg.setFromNickname(hr.getName()); // 发送者昵称（用于显示）
        chatMsg.setDate(new Date());           // 消息发送时间

        /*
         * chatMsg对象结构示例：
         * {
         *   "from": "admin",           // 发送者用户名
         *   "fromNickname": "管理员",   // 发送者昵称
         *   "to": "zhangsan",          // 接收者用户名
         *   "content": "你好",          // 消息内容
         *   "date": "2024-01-01 12:00:00"  // 发送时间
         * }
         */

        // ========== 第三步：发送消息给目标用户 ==========
        /*
         * convertAndSendToUser() 方法说明：
         *
         * 参数1：chatMsg.getTo() - 目标用户的用户名
         *        Spring会自动将其转换为用户特定的目的地
         *
         * 参数2："/queue/chat" - 目的地
         *        实际发送的目的地是：/user/{username}/queue/chat
         *
         * 参数3：chatMsg - 消息内容
         *        会被自动序列化为JSON
         *
         * 【重要】Spring如何知道用户的WebSocket连接？
         * 用户在建立WebSocket连接时，会自动注册到用户会话中
         * Spring通过用户名（Principal.getName()）来定位用户的连接
         */
        simpMessagingTemplate.convertAndSendToUser(
                chatMsg.getTo(),      // 目标用户
                "/queue/chat",        // 目的地
                chatMsg               // 消息内容
        );
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【@MessageMapping vs @RequestMapping】
 *
 *    @RequestMapping("/api/hello")    处理HTTP请求  GET /api/hello
 *    @MessageMapping("/ws/chat")      处理WebSocket消息  发送到 /ws/chat
 *
 * 2. 【点对点消息 vs 广播消息】
 *
 *    点对点（一对一）：
 *    simpMessagingTemplate.convertAndSendToUser("zhangsan", "/queue/chat", msg);
 *    → 只有zhangsan能收到
 *
 *    广播（一对多）：
 *    simpMessagingTemplate.convertAndSend("/topic/notice", msg);
 *    → 所有订阅了/topic/notice的用户都能收到
 *
 * 3. 【用户目的地的转换规则】
 *
 *    convertAndSendToUser("zhangsan", "/queue/chat", msg)
 *
 *    实际发送到：/user/zhangsan/queue/chat
 *
 *    客户端订阅时：stompClient.subscribe('/user/queue/chat', ...)
 *    Spring会自动将其转换为：/user/{当前用户名}/queue/chat
 *
 * 4. 【Authentication参数的来源】
 *
 *    WebSocket连接建立时，会继承HTTP会话的认证信息
 *    在@MessageMapping方法中，可以直接注入以下类型的参数：
 *    - Authentication - 认证对象
 *    - Principal - 主体对象
 *    - @Header - 消息头
 *    - @Payload - 消息体
 *
 * 5. 【ChatMsg消息对象结构】
 *
 *    public class ChatMsg {
 *        private String from;         // 发送者用户名
 *        private String fromNickname; // 发送者昵称
 *        private String to;           // 接收者用户名
 *        private String content;      // 消息内容
 *        private Date date;           // 发送时间
 *    }
 *
 * 6. 【前端代码示例】
 *
 *    // 建立连接
 *    let socket = new SockJS('http://localhost:8081/ws/ep');
 *    let stompClient = Stomp.over(socket);
 *
 *    stompClient.connect({}, function(frame) {
 *        // 订阅自己的消息队列
 *        stompClient.subscribe('/user/queue/chat', function(message) {
 *            let chatMsg = JSON.parse(message.body);
 *            console.log('收到来自', chatMsg.fromNickname, '的消息:', chatMsg.content);
 *        });
 *    });
 *
 *    // 发送消息
 *    function sendMsg(to, content) {
 *        stompClient.send('/ws/chat', {},
 *            JSON.stringify({
 *                to: to,
 *                content: content
 *            })
 *        );
 *    }
 *
 * 7. 【扩展功能建议】
 *
 *    ① 消息持久化
 *       - 将聊天记录保存到数据库
 *       - 用户上线时加载历史消息
 *
 *    ② 在线状态
 *       - 监听用户连接/断开事件
 *       - 维护在线用户列表
 *
 *    ③ 消息已读状态
 *       - 记录消息是否已读
 *       - 发送已读回执
 *
 *    ④ 群聊功能
 *       - 使用/topic目的地广播消息
 *       - 维护群组成员关系
 *
 * 8. 【监听连接事件】
 *
 *    @Component
 *    public class WebSocketEventListener {
 *
 *        @EventListener
 *        public void handleConnect(SessionConnectedEvent event) {
 *            // 用户连接时触发
 *        }
 *
 *        @EventListener
 *        public void handleDisconnect(SessionDisconnectEvent event) {
 *            // 用户断开时触发
 *        }
 *    }
 */
