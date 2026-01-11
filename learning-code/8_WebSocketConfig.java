package org.javaboy.vhr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * =====================================================
 * WebSocket 配置类 - 实现在线聊天功能
 * =====================================================
 *
 * 【WebSocket 简介】
 * WebSocket是一种在单个TCP连接上进行全双工通信的协议
 * 与HTTP不同，WebSocket允许服务器主动向客户端推送数据
 *
 * 【HTTP vs WebSocket】
 *
 *  HTTP（请求-响应模式）：
 *  客户端 ──请求──> 服务器
 *  客户端 <──响应── 服务器
 *  （每次通信都需要客户端发起）
 *
 *  WebSocket（全双工模式）：
 *  客户端 <───────> 服务器
 *  （建立连接后，双方可以随时发送消息）
 *
 * 【STOMP协议】
 * STOMP（Simple Text Oriented Messaging Protocol）是一个简单的文本消息协议
 * 它在WebSocket之上提供了更高层次的抽象，类似于HTTP在TCP之上的关系
 *
 * STOMP的优势：
 * 1. 定义了消息的格式（命令、头、体）
 * 2. 支持订阅/发布模式
 * 3. 与Spring集成良好
 *
 * 【本项目的应用场景】
 * 实现HR系统的在线聊天功能，用户可以实时收发消息
 */
@Configuration
@EnableWebSocketMessageBroker  // 启用WebSocket消息代理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 【注册STOMP端点】
     *
     * 客户端通过这个端点与服务器建立WebSocket连接
     *
     * @param registry 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        /*
         * addEndpoint("/ws/ep")
         * 注册一个WebSocket端点，路径为 /ws/ep
         * 客户端连接时使用：ws://localhost:8081/ws/ep
         *
         * setAllowedOrigins("http://localhost:8080")
         * 设置允许跨域访问的源
         * 前端运行在8080端口，后端运行在8081端口，属于跨域
         * 【注意】生产环境应该设置为实际的域名
         *
         * withSockJS()
         * 启用SockJS支持
         * SockJS是一个JavaScript库，在不支持WebSocket的浏览器中提供降级方案
         * 降级顺序：WebSocket → HTTP Streaming → HTTP Long Polling
         */
        registry.addEndpoint("/ws/ep")
                .setAllowedOrigins("http://localhost:8080")
                .withSockJS();
    }

    /**
     * 【配置消息代理】
     *
     * 消息代理负责将消息路由到正确的目的地
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        /*
         * enableSimpleBroker("/queue")
         * 启用一个简单的内存消息代理
         * 以 /queue 开头的目的地将由这个代理处理
         *
         * 消息流向：
         * 发送者 → 服务器 → /queue/xxx → 订阅者
         *
         * 为什么用 /queue？
         * - /queue 通常用于点对点消息（一对一聊天）
         * - /topic 通常用于广播消息（群聊、通知）
         *
         * 【扩展配置】
         * 如果需要更复杂的功能，可以使用外部消息代理：
         * registry.enableStompBrokerRelay("/queue", "/topic")
         *         .setRelayHost("localhost")
         *         .setRelayPort(61613);
         */
        registry.enableSimpleBroker("/queue");

        /*
         * 【其他可选配置】
         *
         * setApplicationDestinationPrefixes("/app")
         * 设置应用程序目的地前缀
         * 客户端发送消息到 /app/xxx 时，会路由到 @MessageMapping("/xxx") 方法
         *
         * setUserDestinationPrefix("/user")
         * 设置用户目的地前缀（默认就是/user）
         * 用于点对点消息，如 /user/queue/chat
         */
    }
}

/*
 * =====================================================
 * 【学习要点总结】
 * =====================================================
 *
 * 1. 【WebSocket连接流程】
 *
 *    ┌─────────────────────────────────────────────────────┐
 *    │  前端代码示例（使用SockJS和STOMP）                    │
 *    │                                                     │
 *    │  // 1. 建立连接                                     │
 *    │  let socket = new SockJS('http://localhost:8081/ws/ep');│
 *    │  let stompClient = Stomp.over(socket);              │
 *    │                                                     │
 *    │  // 2. 连接成功后订阅消息                            │
 *    │  stompClient.connect({}, function(frame) {          │
 *    │      // 订阅自己的消息队列                           │
 *    │      stompClient.subscribe('/user/queue/chat', function(msg) {│
 *    │          console.log('收到消息:', msg.body);         │
 *    │      });                                            │
 *    │  });                                                │
 *    │                                                     │
 *    │  // 3. 发送消息                                     │
 *    │  stompClient.send('/ws/chat', {}, JSON.stringify({  │
 *    │      to: '张三',                                    │
 *    │      content: '你好'                                │
 *    │  }));                                               │
 *    └─────────────────────────────────────────────────────┘
 *
 * 2. 【消息路由规则】
 *
 *    客户端发送消息到：/ws/chat
 *                        ↓
 *    服务器 @MessageMapping("/ws/chat") 方法处理
 *                        ↓
 *    使用 SimpMessagingTemplate.convertAndSendToUser()
 *                        ↓
 *    消息发送到：/user/{username}/queue/chat
 *                        ↓
 *    目标用户的客户端收到消息
 *
 * 3. 【SimpleBroker vs StompBrokerRelay】
 *
 *    SimpleBroker（简单代理）：
 *    - 内存中的消息代理
 *    - 适合单机部署
 *    - 不支持集群
 *    - 服务重启消息丢失
 *
 *    StompBrokerRelay（外部代理）：
 *    - 连接外部消息代理（如RabbitMQ、ActiveMQ）
 *    - 支持集群部署
 *    - 消息持久化
 *    - 适合生产环境
 *
 * 4. 【SockJS降级策略】
 *
 *    优先级从高到低：
 *    ① WebSocket - 原生WebSocket连接
 *    ② XHR Streaming - 使用XMLHttpRequest的流式传输
 *    ③ XHR Polling - 轮询方式
 *    ④ iframe方案 - 兼容老旧浏览器
 *
 * 5. 【跨域配置说明】
 *
 *    setAllowedOrigins() - 设置允许的源
 *    - 开发环境：setAllowedOrigins("http://localhost:8080")
 *    - 生产环境：setAllowedOrigins("https://yourdomain.com")
 *    - 允许所有：setAllowedOrigins("*")（不推荐）
 *
 * 6. 【与Spring Security集成】
 *
 *    WebSocket连接会自动继承HTTP会话的认证信息
 *    在@MessageMapping方法中可以获取当前用户：
 *
 *    @MessageMapping("/chat")
 *    public void chat(Authentication auth, ChatMsg msg) {
 *        Hr user = (Hr) auth.getPrincipal();
 *        // user就是当前登录用户
 *    }
 */
