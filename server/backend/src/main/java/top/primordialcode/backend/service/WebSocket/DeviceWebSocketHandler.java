package top.primordialcode.backend.service.WebSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.utils.MaskUtil;
import top.primordialcode.backend.utils.UserLookupCache;
import top.primordialcode.backend.vo.websocket.WsConnectedMessage;
import top.primordialcode.backend.vo.websocket.WsErrorMessage;
import top.primordialcode.backend.vo.websocket.WsPongMessage;
import top.primordialcode.backend.vo.websocket.WsRealtimeUpdateMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设备数据WebSocket处理器
 *
 * 实现 docs/前后端API文档.md 第7章 WebSocket通信协议：
 * - 7.1 连接认证(握手拦截器完成): ?token= 或 ?key=
 * - 7.2 订阅用户数据: subscribe
 * - 7.3 取消订阅: unsubscribe
 * - 7.4 实时数据推送: realtime_update(数据上传后推送给所有订阅者)
 * - 7.5 心跳机制: ping → pong
 * - 7.6 错误消息: error(401/403/404等)
 * - 7.7 连接状态消息: connected
 *
 * 本系统中以「用户邮箱」作为全局唯一用户标识，即协议中的userId。
 *
 * 权限模型：
 * - 使用Token连接 → 被授权查看Token所属用户(自己)的数据
 * - 使用秘钥连接 → 被授权查看秘钥持有者的数据
 *
 * 推送实现要点：
 * 1. 订阅关系交给 {@link SubscriptionRegistry}，单机/多实例可切换；
 * 2. 同一份快照只序列化一次，再分发给 N 个订阅者(不再逐连接序列化)；
 * 3. 每个连接用 {@link SubscriptionSession} 包装，发送串行化 + 超时保护，
 *    慢消费者会被断开而不是拖住推送线程。
 */
@Slf4j
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    /* ==================== Session属性Key ==================== */

    /** Session属性: 被授权查看的用户标识(userId，本系统中为用户邮箱) */
    public static final String ATTR_USER_ID = "userId";

    /** Session属性: 认证方式(取值见 AUTH_MODE_*) */
    public static final String ATTR_AUTH_MODE = "authMode";

    /** 认证方式: Token认证(用户查看自己) */
    public static final String AUTH_MODE_TOKEN = "TOKEN";

    /** 认证方式: 秘钥认证(查看者查看秘钥持有者) */
    public static final String AUTH_MODE_KEY = "KEY";

    /* ==================== 客户端消息类型 ==================== */

    private static final String MSG_SUBSCRIBE = "subscribe";
    private static final String MSG_UNSUBSCRIBE = "unsubscribe";
    private static final String MSG_PING = "ping";

    /** 连接ID → 订阅会话，便于按 sessionId 清理 */
    private final java.util.concurrent.ConcurrentHashMap<String, SubscriptionSession> activeSessions
            = new java.util.concurrent.ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final UserLookupCache userLookupCache;
    private final SubscriptionRegistry registry;

    /**
     * 构造处理器
     *
     * 内存实现与 Redis 实现同时存在时，按 realtime.broker 选择唯一个运行时注册表，
     * 避免 "expected single matching bean but found 2" 的歧义注入。
     *
     * @param objectMapper     JSON 序列化器
     * @param userLookupCache  用户缓存
     * @param inMemoryRegistry 内存订阅表(单机模式)
     * @param redisRegistry    Redis 订阅表(多实例模式)
     * @param broker           广播模式开关
     */
    @org.springframework.beans.factory.annotation.Autowired
    public DeviceWebSocketHandler(
            ObjectMapper objectMapper,
            UserLookupCache userLookupCache,
            InMemorySubscriptionRegistry inMemoryRegistry,
            RedisSubscriptionRegistry redisRegistry,
            @org.springframework.beans.factory.annotation.Value("${realtime.broker:memory}") String broker) {
        this.objectMapper = objectMapper;
        this.userLookupCache = userLookupCache;
        this.registry = "redis".equalsIgnoreCase(broker) ? redisRegistry : inMemoryRegistry;
        log.info("WebSocket 订阅注册表实现: {}", registry.getClass().getSimpleName());
    }

    /**
     * 测试与内部使用的构造器：直接指定注册表
     *
     * @param objectMapper    JSON 序列化器
     * @param userLookupCache 用户缓存
     * @param registry        订阅注册表
     */
    public DeviceWebSocketHandler(ObjectMapper objectMapper,
                                  UserLookupCache userLookupCache,
                                  SubscriptionRegistry registry) {
        this.objectMapper = objectMapper;
        this.userLookupCache = userLookupCache;
        this.registry = registry;
    }

    /* ==================== 连接建立 ==================== */

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get(ATTR_USER_ID);

        if (userId == null) {
            // 没有认证身份，拒绝连接
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        SubscriptionSession subscriptionSession = new SubscriptionSession(session);
        activeSessions.put(subscriptionSession.getId(), subscriptionSession);

        // 连接建立后自动订阅被授权查看的用户
        registry.subscribe(userId, subscriptionSession);

        // 发送连接成功消息(Token认证时返回userId，即用户查看自己)
        String authMode = (String) session.getAttributes().get(ATTR_AUTH_MODE);
        String selfUserId = AUTH_MODE_TOKEN.equals(authMode) ? userId : null;
        send(subscriptionSession, new WsConnectedMessage("WebSocket连接成功", selfUserId));

        log.info("WebSocket连接建立: userId={}, authMode={}, 本连接订阅数={}",
                MaskUtil.maskEmail(userId), authMode, registry.subscriberCountOf(userId));
    }

    /* ==================== 接收客户端消息 ==================== */

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SubscriptionSession subscriptionSession = activeSessions.get(session.getId());
        if (subscriptionSession == null) {
            // 未登记的连接(理论上不会出现)，直接忽略
            return;
        }
        subscriptionSession.touch();

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("收到无法解析的JSON消息: sessionId={}", session.getId());
            sendError(subscriptionSession, 400, "消息格式错误，无法解析为JSON");
            return;
        }

        if (root == null || !root.isObject()) {
            sendError(subscriptionSession, 400, "消息格式错误，必须是JSON对象");
            return;
        }

        String type = root.path("type").asText("").trim();

        switch (type) {
            case MSG_PING:
                // 心跳请求 → 立即响应pong
                send(subscriptionSession, new WsPongMessage(Instant.now()));
                break;
            case MSG_SUBSCRIBE:
                handleSubscribe(subscriptionSession, root);
                break;
            case MSG_UNSUBSCRIBE:
                handleUnsubscribe(subscriptionSession, root);
                break;
            default:
                log.warn("未知消息类型: {}, userId={}", type,
                        MaskUtil.maskEmail((String) session.getAttributes().get(ATTR_USER_ID)));
                sendError(subscriptionSession, 400, "未知消息类型: " + type);
                break;
        }
    }

    /**
     * 处理订阅消息(协议7.2)
     *
     * @param subscriptionSession 订阅连接
     * @param root                客户端消息
     */
    private void handleSubscribe(SubscriptionSession subscriptionSession, JsonNode root) {
        String targetUserId = root.path("userId").asText("").trim();

        if (targetUserId.isEmpty()) {
            sendError(subscriptionSession, 400, "缺少userId参数");
            return;
        }

        String authorizedUserId = (String) subscriptionSession.getRawSession()
                .getAttributes().get(ATTR_USER_ID);

        // 订阅的是被授权用户本人 → 直接允许(大小写不敏感)
        if (authorizedUserId != null && authorizedUserId.equalsIgnoreCase(targetUserId)) {
            registry.subscribe(targetUserId, subscriptionSession);
            log.info("订阅成功: sessionId={} 订阅用户={}",
                    subscriptionSession.getId(), MaskUtil.maskEmail(targetUserId));
            return;
        }

        // 订阅其他用户：先判断是否存在(404)，再判断权限(403)。走缓存，避免被刷库。
        if (!userLookupCache.existsByEmail(targetUserId)) {
            log.warn("订阅失败，用户不存在: {}", MaskUtil.maskEmail(targetUserId));
            sendError(subscriptionSession, 404, "用户不存在");
            return;
        }

        log.warn("订阅失败，无权限订阅该用户: 连接授权用户={}, 目标用户={}",
                MaskUtil.maskEmail(authorizedUserId), MaskUtil.maskEmail(targetUserId));
        sendError(subscriptionSession, 403, "无权限订阅该用户");
    }

    /**
     * 处理取消订阅消息(协议7.3)
     *
     * @param subscriptionSession 订阅连接
     * @param root                客户端消息
     */
    private void handleUnsubscribe(SubscriptionSession subscriptionSession, JsonNode root) {
        String targetUserId = root.path("userId").asText("").trim();

        if (targetUserId.isEmpty()) {
            sendError(subscriptionSession, 400, "缺少userId参数");
            return;
        }

        registry.unsubscribe(targetUserId, subscriptionSession);
        log.info("取消订阅: sessionId={} 取消订阅用户={}",
                subscriptionSession.getId(), MaskUtil.maskEmail(targetUserId));
    }

    /* ==================== 连接关闭/异常 ==================== */

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SubscriptionSession subscriptionSession = activeSessions.remove(session.getId());
        if (subscriptionSession != null) {
            registry.removeSession(subscriptionSession);
        }
        log.info("WebSocket连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket连接异常: sessionId={}, 原因={}", session.getId(), exception.getMessage());

        SubscriptionSession subscriptionSession = activeSessions.remove(session.getId());
        if (subscriptionSession != null) {
            registry.removeSession(subscriptionSession);
        }

        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception e) {
                log.warn("关闭异常连接失败: {}", e.getMessage());
            }
        }
    }

    /* ==================== 消息推送 ==================== */

    /**
     * 推送实时数据给所有订阅指定用户的查看者(协议7.4)
     *
     * 同一份 payload 只序列化一次；多实例模式下由调用方决定是否经 Redis 转发。
     *
     * @param userEmail    数据所属用户邮箱
     * @param timestamp    数据时间戳
     * @param applications 完整应用列表(可空，空时推空数组)
     * @param statistics   统计数据(可空)
     * @return 实际送达的本地连接数
     */
    public int pushRealtimeUpdate(
            String userEmail,
            Instant timestamp,
            List<ApplicationDTO> applications,
            StatisticsDTO statistics) {

        Set<SubscriptionSession> subscribers = registry.subscribersOf(userEmail);
        if (subscribers.isEmpty()) {
            return 0;
        }

        String json = serialize(timestamp, userEmail, applications, statistics);
        if (json == null) {
            return 0;
        }

        int delivered = 0;
        for (SubscriptionSession subscriptionSession : subscribers) {
            if (!subscriptionSession.isOpen()) {
                registry.removeSession(subscriptionSession);
                activeSessions.remove(subscriptionSession.getId());
                continue;
            }
            try {
                subscriptionSession.send(json);
                delivered++;
            } catch (Exception e) {
                log.warn("推送给连接失败，sessionId={}: {}", subscriptionSession.getId(), e.getMessage());
                registry.removeSession(subscriptionSession);
                activeSessions.remove(subscriptionSession.getId());
            }
        }

        log.debug("实时数据推送完成: userId={}, 订阅连接数={}, 成功={}",
                MaskUtil.maskEmail(userEmail), subscribers.size(), delivered);
        return delivered;
    }

    /**
     * 把实时更新序列化成协议 JSON(供本类推送与 Redis 跨实例广播共用)
     *
     * @param timestamp    时间戳
     * @param userEmail    用户邮箱
     * @param applications 应用列表
     * @param statistics   统计数据
     * @return JSON 字符串，序列化失败返回 null
     */
    public String serialize(Instant timestamp, String userEmail,
                            List<ApplicationDTO> applications, StatisticsDTO statistics) {
        WsRealtimeUpdateMessage payload = new WsRealtimeUpdateMessage(
                userEmail,
                timestamp,
                new WsRealtimeUpdateMessage.WsRealtimeData(
                        applications != null ? applications : new ArrayList<>(),
                        statistics));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("实时数据推送消息序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /* ==================== 工具方法 ==================== */

    /**
     * 给单个连接发送消息对象(自动序列化为JSON)
     *
     * @param subscriptionSession 目标连接
     * @param payload             消息体
     */
    private void send(SubscriptionSession subscriptionSession, Object payload) {
        if (!subscriptionSession.isOpen()) {
            return;
        }
        try {
            subscriptionSession.send(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("发送WebSocket消息失败: sessionId={}, 原因={}",
                    subscriptionSession.getId(), e.getMessage());
        }
    }

    /**
     * 给单个连接发送错误消息(协议7.6)
     *
     * @param subscriptionSession 目标连接
     * @param code                业务错误码
     * @param message             错误提示
     */
    private void sendError(SubscriptionSession subscriptionSession, int code, String message) {
        send(subscriptionSession, new WsErrorMessage(code, message));
    }

    /**
     * 当前在线连接数(供监控指标与测试使用)
     *
     * @return 连接数
     */
    public int onlineSessionCount() {
        return registry.sessionCount();
    }

    /**
     * 清理空闲/已失效连接
     *
     * 客户端被强杀时 TCP 可能长时间不关闭，这里按"最近活跃时间"回收。
     *
     * @param idleTimeoutMillis 超过该时长未活跃即断开
     * @return 本次清理的连接数
     */
    public int evictIdleSessions(long idleTimeoutMillis) {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (SubscriptionSession subscriptionSession : new ArrayList<>(activeSessions.values())) {
            boolean stale = now - subscriptionSession.getLastActiveMillis() > idleTimeoutMillis;
            if (!stale && subscriptionSession.isOpen()) {
                continue;
            }
            registry.removeSession(subscriptionSession);
            activeSessions.remove(subscriptionSession.getId());
            removed++;
            try {
                subscriptionSession.getRawSession().close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (Exception e) {
                log.debug("关闭空闲连接失败: sessionId={}, error={}",
                        subscriptionSession.getId(), e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("清理空闲/失效WebSocket连接: 数量={}", removed);
        }
        return removed;
    }
}