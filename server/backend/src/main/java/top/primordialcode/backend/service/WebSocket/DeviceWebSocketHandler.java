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
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.vo.websocket.WsConnectedMessage;
import top.primordialcode.backend.vo.websocket.WsErrorMessage;
import top.primordialcode.backend.vo.websocket.WsPongMessage;
import top.primordialcode.backend.vo.websocket.WsRealtimeUpdateMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备数据WebSocket处理器
 *
 * 实现 docs/前后端API文档.md 第7章 WebSocket通信协议:
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
 * 权限模型:
 * - 使用Token连接 → 被授权查看Token所属用户(自己)的数据
 * - 使用秘钥连接 → 被授权查看秘钥持有者的数据
 * - 连接建立后服务端自动订阅该被授权用户，客户端也可通过subscribe消息订阅；
 *   订阅其他用户会被拒绝(403)，用户不存在返回404。
 */
@Slf4j
@Component
public class DeviceWebSocketHandler
        extends TextWebSocketHandler {

    /* ==================== Session属性Key ==================== */

    /**
     * Session属性: 被授权查看的用户标识(userId，本系统中为用户邮箱)
     */
    public static final String ATTR_USER_ID = "userId";

    /**
     * Session属性: 认证方式(取值见 AUTH_MODE_*)
     */
    public static final String ATTR_AUTH_MODE = "authMode";

    /**
     * 认证方式: Token认证(用户查看自己)
     */
    public static final String AUTH_MODE_TOKEN = "TOKEN";

    /**
     * 认证方式: 秘钥认证(查看者查看秘钥持有者)
     */
    public static final String AUTH_MODE_KEY = "KEY";

    /* ==================== 客户端消息类型 ==================== */

    private static final String MSG_SUBSCRIBE = "subscribe";
    private static final String MSG_UNSUBSCRIBE = "unsubscribe";
    private static final String MSG_PING = "ping";

    /**
     * 订阅关系: 被订阅用户(userId/邮箱) → 订阅该用户的所有连接Session
     */
    private final Map<String, Set<WebSocketSession>> userSubscribers
            = new ConcurrentHashMap<>();

    /**
     * 连接关系: sessionId → 该连接当前订阅的用户集合(关闭连接时用于清理订阅关系)
     */
    private final Map<String, Set<String>> sessionSubscriptions
            = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final UserAuthMapper userAuthMapper;

    public DeviceWebSocketHandler(
            ObjectMapper objectMapper,
            UserAuthMapper userAuthMapper) {
        this.objectMapper = objectMapper;
        this.userAuthMapper = userAuthMapper;
    }

    /* ==================== 连接建立 ==================== */

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {

        String userId = (String) session.getAttributes().get(ATTR_USER_ID);

        if (userId == null) {
            // 没有认证身份，拒绝连接
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 连接建立后自动订阅被授权查看的用户
        addSubscription(session, userId);

        // 发送连接成功消息(Token认证时返回userId，即用户查看自己)
        String authMode = (String) session.getAttributes().get(ATTR_AUTH_MODE);
        String selfUserId = AUTH_MODE_TOKEN.equals(authMode) ? userId : null;
        send(session, new WsConnectedMessage("WebSocket连接成功", selfUserId));

        log.info("WebSocket连接建立: userId={}, authMode={}, 当前订阅连接数={}",
                userId, authMode, userSubscribers.getOrDefault(userId, Collections.emptySet()).size());
    }

    /* ==================== 接收客户端消息 ==================== */

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("收到无法解析的JSON消息: {}", message.getPayload());
            sendError(session, 400, "消息格式错误，无法解析为JSON");
            return;
        }

        if (root == null || !root.isObject()) {
            sendError(session, 400, "消息格式错误，必须是JSON对象");
            return;
        }

        String type = root.path("type").asText("").trim();

        switch (type) {
            case MSG_PING:
                // 心跳请求 → 立即响应pong
                send(session, new WsPongMessage(Instant.now()));
                break;
            // 连接建立
            case MSG_SUBSCRIBE:
                handleSubscribe(session, root);
                break;
            // 关闭连接请求
            case MSG_UNSUBSCRIBE:
                handleUnsubscribe(session, root);
                break;

            default:
                log.warn("未知消息类型: {}, userId={}",
                        type, session.getAttributes().get(ATTR_USER_ID));
                sendError(session, 400, "未知消息类型: " + type);
                break;
        }
    }

    /**
     * 处理订阅消息(协议7.2)
     * <pre>
     * {
     *   "type": "subscribe",
     *   "userId": "xxx"
     * }
     * </pre>
     */
    private void handleSubscribe(WebSocketSession session, JsonNode root) {
        String targetUserId = root.path("userId").asText("").trim();

        if (targetUserId.isEmpty()) {
            sendError(session, 400, "缺少userId参数");
            return;
        }

        // 该连接被授权查看的用户(自己/秘钥持有者)
        String authorizedUserId = (String) session.getAttributes().get(ATTR_USER_ID);

        // 订阅的是被授权用户本人 → 直接允许(大小写不敏感)
        if (authorizedUserId != null
                && authorizedUserId.equalsIgnoreCase(targetUserId)) {
            addSubscription(session, targetUserId);
            log.info("订阅成功: sessionId={} 订阅用户={}",
                    session.getId(), targetUserId);
            return;
        }

        // 订阅其他用户: 先判断用户是否存在(404)，再判断是否有权限(403)
        if (!userAuthMapper.existsByEmail(targetUserId)) {
            log.warn("订阅失败，用户不存在: {}", targetUserId);
            sendError(session, 404, "用户不存在");
            return;
        }

        log.warn("订阅失败，无权限订阅该用户: 连接授权用户={}, 目标用户={}",
                authorizedUserId, targetUserId);
        sendError(session, 403, "无权限订阅该用户");
    }

    /**
     * 处理取消订阅消息(协议7.3)
     * <pre>
     * {
     *   "type": "unsubscribe",
     *   "userId": "xxx"
     * }
     * </pre>
     */
    private void handleUnsubscribe(WebSocketSession session, JsonNode root) {
        String targetUserId = root.path("userId").asText("").trim();

        if (targetUserId.isEmpty()) {
            sendError(session, 400, "缺少userId参数");
            return;
        }

        removeSubscription(session, targetUserId);
        log.info("取消订阅: sessionId={} 取消订阅用户={}",
                session.getId(), targetUserId);
    }

    /* ==================== 连接关闭/异常 ==================== */

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 清理该连接的所有订阅关系
        removeSession(session);
        log.info("WebSocket连接关闭: sessionId={}, status={}",
                session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket连接异常: sessionId={}, 原因: {}",
                session.getId(), exception.getMessage());

        // 清理订阅关系
        removeSession(session);

        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception e) {
                log.warn("关闭异常连接失败: {}", e.getMessage());
            }
        }
    }

    /* ==================== 订阅关系维护 ==================== */

    /**
     * 添加订阅关系(幂等)
     *
     * @param session 订阅连接
     * @param userId  被订阅的用户标识
     */
    private void addSubscription(WebSocketSession session, String userId) {
        userSubscribers
                .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                .add(session);

        sessionSubscriptions
                .computeIfAbsent(session.getId(), key -> ConcurrentHashMap.newKeySet())
                .add(userId);
    }

    /**
     * 移除单个订阅关系(幂等)
     *
     * @param session 订阅连接
     * @param userId  被取消订阅的用户标识
     */
    private void removeSubscription(WebSocketSession session, String userId) {
        Set<String> subscribedUsers = sessionSubscriptions.get(session.getId());
        if (subscribedUsers != null) {
            subscribedUsers.remove(userId);
        }

        Set<WebSocketSession> sessions = userSubscribers.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSubscribers.remove(userId, sessions);
            }
        }
    }

    /**
     * 连接断开/异常时清理该连接的全部订阅关系(幂等)
     *
     * @param session 断开的连接
     */
    private void removeSession(WebSocketSession session) {
        Set<String> subscribedUsers =
                sessionSubscriptions.remove(session.getId());

        if (subscribedUsers == null) {
            return;
        }

        for (String userId : subscribedUsers) {
            Set<WebSocketSession> sessions = userSubscribers.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSubscribers.remove(userId, sessions);
                }
            }
        }
    }

    /* ==================== 消息推送 ==================== */

    /**
     * 推送实时数据给所有订阅指定用户的查看者(协议7.4)
     *
     * 推送的是Redis中的完整实时快照(所有应用的累计数据 + 统计数据)，
     * 由数据上传服务在客户端每次上传后调用。
     *
     * @param userEmail    数据所属用户邮箱
     * @param timestamp    数据时间戳
     * @param applications 完整应用列表(可空，空时推空数组)
     * @param statistics   统计数据(可空)
     */
    public void pushRealtimeUpdate(
            String userEmail,
            Instant timestamp,
            List<ApplicationDTO> applications,
            StatisticsDTO statistics) {

        Set<WebSocketSession> subscribers = userSubscribers.get(userEmail);

        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        WsRealtimeUpdateMessage payload = new WsRealtimeUpdateMessage(
                userEmail,
                timestamp,
                new WsRealtimeUpdateMessage.WsRealtimeData(
                        applications != null ? applications : new ArrayList<>(),
                        statistics
                )
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("实时数据推送消息序列化失败: {}", e.getMessage(), e);
            return;
        }

        for (WebSocketSession session : subscribers) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.warn("推送给连接失败，sessionId={}: {}",
                        session.getId(), e.getMessage());
            }
        }

        log.debug("实时数据推送完成: userId={}, 订阅连接数={}",
                userEmail, subscribers.size());
    }

    /* ==================== 工具方法 ==================== */

    /**
     * 给单个连接发送消息对象(自动序列化为JSON)
     */
    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.warn("发送WebSocket消息失败: sessionId={}, 原因={}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * 给单个连接发送错误消息(协议7.6)
     */
    private void sendError(WebSocketSession session, int code, String message) {
        send(session, new WsErrorMessage(code, message));
    }
}
