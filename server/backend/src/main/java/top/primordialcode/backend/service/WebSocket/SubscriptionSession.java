package top.primordialcode.backend.service.WebSocket;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * 订阅会话包装
 *
 * 直接对 Tomcat 的 WsSession 并发 sendMessage 是未定义行为，
 * 而且一个卡死的浏览器会把推送线程一起拖住。这里统一用
 * {@link ConcurrentWebSocketSessionDecorator} 串行化发送，并设置
 * "发送超时 / 缓冲区上限"，慢消费者直接被断开而不是拖垮服务。
 */
public final class SubscriptionSession {

    /** 单条消息发送超时(毫秒)：超过即认为该连接已卡死 */
    private static final int SEND_TIME_LIMIT_MS = 2000;

    /** 单连接积压缓冲上限(字节)：超过即断开(实时数据属于"可以丢，重新拉一次就好") */
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final WebSocketSession rawSession;

    private final WebSocketSession decoratedSession;

    private final long connectedAtMillis;

    /** 最近一次收到客户端消息(含 ping)的时间，供空闲回收任务使用 */
    private volatile long lastActiveMillis;

    /**
     * 构造订阅会话
     *
     * @param rawSession 原始连接(已经被认证拦截器写入授权用户)
     */
    public SubscriptionSession(WebSocketSession rawSession) {
        this.rawSession = rawSession;
        this.decoratedSession = new ConcurrentWebSocketSessionDecorator(
                rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        long now = System.currentTimeMillis();
        this.connectedAtMillis = now;
        this.lastActiveMillis = now;
    }

    /**
     * 连接 ID
     *
     * @return sessionId
     */
    public String getId() {
        return rawSession.getId();
    }

    /**
     * 连接是否仍然可用
     *
     * @return true：可发送
     */
    public boolean isOpen() {
        return decoratedSession.isOpen();
    }

    /**
     * 发送文本消息(经装饰器串行化，线程安全)
     *
     * @param payload 已序列化好的 JSON
     */
    public void send(String payload) {
        if (!isOpen()) {
            return;
        }
        try {
            decoratedSession.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            // 发送失败视为连接不可用，由调用方清理
            throw new IllegalStateException("WebSocket发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 记录一次活跃(收到客户端消息时调用)
     */
    public void touch() {
        this.lastActiveMillis = System.currentTimeMillis();
    }

    /**
     * 最近活跃时间戳
     *
     * @return epoch millis
     */
    public long getLastActiveMillis() {
        return lastActiveMillis;
    }

    /**
     * 连接建立时间戳
     *
     * @return epoch millis
     */
    public long getConnectedAtMillis() {
        return connectedAtMillis;
    }

    /**
     * 原始连接(仅用于读取认证阶段写入的 attributes)
     *
     * @return WebSocketSession
     */
    public WebSocketSession getRawSession() {
        return rawSession;
    }
}