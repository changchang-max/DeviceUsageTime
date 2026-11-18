package top.primordialcode.backend.service.WebSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅注册表的内存实现(单实例部署)
 *
 * 数据结构与旧实现一致，只把 value 从原始 WebSocketSession 换成
 * {@link SubscriptionSession}(带发送串行化与超时保护)：
 * - userSubscribers:    被订阅用户 → 订阅连接集合
 * - sessionSubscriptions: 连接ID   → 该连接订阅的用户集合(用于连接关闭时清理)
 */
@Slf4j
@Component
public class InMemorySubscriptionRegistry implements SubscriptionRegistry {

    /** 被订阅用户 → 订阅连接集合 */
    private final Map<String, Set<SubscriptionSession>> userSubscribers = new ConcurrentHashMap<>();

    /** 连接ID → 该连接订阅的用户集合 */
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    /** 连接ID → 订阅会话(供清理任务遍历，避免反查嵌套集合) */
    private final Map<String, SubscriptionSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String userId, SubscriptionSession session) {
        sessions.put(session.getId(), session);

        userSubscribers
                .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                .add(session);

        sessionSubscriptions
                .computeIfAbsent(session.getId(), key -> ConcurrentHashMap.newKeySet())
                .add(userId);
    }

    @Override
    public void unsubscribe(String userId, SubscriptionSession session) {
        Set<String> subscribedUsers = sessionSubscriptions.get(session.getId());
        if (subscribedUsers != null) {
            subscribedUsers.remove(userId);
        }

        Set<SubscriptionSession> registered = userSubscribers.get(userId);
        if (registered != null) {
            registered.remove(session);
            if (registered.isEmpty()) {
                userSubscribers.remove(userId, registered);
            }
        }

        // 该连接已不订阅任何用户时，一并回收会话引用
        if (subscribedUsers == null || subscribedUsers.isEmpty()) {
            sessionSubscriptions.remove(session.getId());
            sessions.remove(session.getId());
        }
    }

    @Override
    public void removeSession(SubscriptionSession session) {
        sessions.remove(session.getId());

        Set<String> subscribedUsers = sessionSubscriptions.remove(session.getId());
        if (subscribedUsers == null) {
            return;
        }
        for (String userId : subscribedUsers) {
            Set<SubscriptionSession> registered = userSubscribers.get(userId);
            if (registered != null) {
                registered.remove(session);
                if (registered.isEmpty()) {
                    userSubscribers.remove(userId, registered);
                }
            }
        }
    }

    @Override
    public Set<SubscriptionSession> subscribersOf(String userId) {
        Set<SubscriptionSession> registered = userSubscribers.get(userId);
        if (registered == null || registered.isEmpty()) {
            return Collections.emptySet();
        }
        // 返回快照，避免推送过程中集合被并发修改
        return Set.copyOf(registered);
    }

    @Override
    public int sessionCount() {
        return sessions.size();
    }

    @Override
    public int subscriberCountOf(String userId) {
        Set<SubscriptionSession> registered = userSubscribers.get(userId);
        return registered == null ? 0 : registered.size();
    }

    /**
     * 当前全部在线连接(供空闲/失效连接清理任务使用)
     *
     * @return 连接集合快照
     */
    public Collection<SubscriptionSession> allSessions() {
        return Set.copyOf(sessions.values());
    }
}