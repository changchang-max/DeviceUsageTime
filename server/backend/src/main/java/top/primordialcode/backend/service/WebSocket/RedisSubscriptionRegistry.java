package top.primordialcode.backend.service.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅注册表的 Redis Pub/Sub 实现(多实例部署)
 *
 * 工作方式：
 * 1. 本地仍保留"本实例上的订阅连接"表(推送必须发给真实的 TCP 连接)；
 * 2. 需要广播给某用户时，publish 到频道 {@code realtime:{email}}；
 * 3. 每个实例都监听了 {@code realtime:*}，收到消息后只查本地订阅表，
 *    推给自己持有的连接。
 *
 * 这样容器扩到 N 个实例时，上传落在哪个实例上都能推给全部查看者。
 * 通过 {@code realtime.broker=redis} 开关启用，默认内存实现。
 */
@Slf4j
@Component
public class RedisSubscriptionRegistry implements SubscriptionRegistry, MessageListener {

    /** 频道前缀(发布与订阅共用) */
    public static final String CHANNEL_PREFIX = "realtime:";

    /** 本地订阅表：被订阅用户 → 本实例上的连接 */
    private final ConcurrentHashMap<String, Set<SubscriptionSession>> localSubscribers = new ConcurrentHashMap<>();

    /** 本地连接表：连接ID → 订阅会话 */
    private final ConcurrentHashMap<String, SubscriptionSession> localSessions = new ConcurrentHashMap<>();

    /** 连接ID → 该连接订阅的用户集合 */
    private final ConcurrentHashMap<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /** 是否启用多实例广播 */
    private final boolean enabled;

    public RedisSubscriptionRegistry(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${realtime.broker:memory}") String broker) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = "redis".equalsIgnoreCase(broker);
    }

    /**
     * 是否启用 Redis 广播
     *
     * @return true：多实例模式
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void subscribe(String userId, SubscriptionSession session) {
        localSessions.put(session.getId(), session);
        localSubscribers.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        sessionSubscriptions.computeIfAbsent(session.getId(), key -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    @Override
    public void unsubscribe(String userId, SubscriptionSession session) {
        Set<String> users = sessionSubscriptions.get(session.getId());
        if (users != null) {
            users.remove(userId);
        }
        Set<SubscriptionSession> sessions = localSubscribers.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                localSubscribers.remove(userId, sessions);
            }
        }
        if (users == null || users.isEmpty()) {
            sessionSubscriptions.remove(session.getId());
            localSessions.remove(session.getId());
        }
    }

    @Override
    public void removeSession(SubscriptionSession session) {
        localSessions.remove(session.getId());
        Set<String> users = sessionSubscriptions.remove(session.getId());
        if (users == null) {
            return;
        }
        for (String userId : users) {
            Set<SubscriptionSession> sessions = localSubscribers.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    localSubscribers.remove(userId, sessions);
                }
            }
        }
    }

    @Override
    public Set<SubscriptionSession> subscribersOf(String userId) {
        Set<SubscriptionSession> sessions = localSubscribers.get(userId);
        return sessions == null || sessions.isEmpty() ? Set.of() : Set.copyOf(sessions);
    }

    @Override
    public int sessionCount() {
        return localSessions.size();
    }

    @Override
    public int subscriberCountOf(String userId) {
        Set<SubscriptionSession> sessions = localSubscribers.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    /**
     * 把一次实时更新发布到 Redis 频道(跨实例广播)
     *
     * @param userId  数据所属用户
     * @param payload 已序列化的 JSON
     */
    public void publish(String userId, String payload) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + userId, payload);
        } catch (Exception e) {
            log.warn("实时数据发布到 Redis 失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 收到其它实例发布的实时更新，只推给本实例上该用户的订阅者
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            if (!channel.startsWith(CHANNEL_PREFIX)) {
                return;
            }
            String userId = channel.substring(CHANNEL_PREFIX.length());
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            for (SubscriptionSession session : subscribersOf(userId)) {
                try {
                    session.send(payload);
                } catch (Exception e) {
                    log.debug("转发实时数据失败: sessionId={}, error={}", session.getId(), e.getMessage());
                    removeSession(session);
                }
            }
        } catch (Exception e) {
            log.warn("处理 Redis 实时数据消息失败: {}", e.getMessage());
        }
    }

    /**
     * 供监控使用的 JSON 序列化器(与 DeviceWebSocketHandler 共用同一 ObjectMapper)
     *
     * @return ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}