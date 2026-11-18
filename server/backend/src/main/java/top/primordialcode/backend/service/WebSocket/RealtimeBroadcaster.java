package top.primordialcode.backend.service.WebSocket;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 实时推送分发器
 *
 * 职责：把"某用户的数据变化"变成"所有订阅者的推送"。
 * - 载荷只序列化一次(由 {@link DeviceWebSocketHandler#serialize} 负责)；
 * - 经 {@link WsBroadcastBatcher} 按时间窗合帧，避免 1 秒 1 次的抖动；
 * - 多实例模式下经 Redis Pub/Sub 转发，本实例也通过监听收到消息后推送，
 *   因此不存在"重复推送"：单机分支直接推本地，多实例分支走频道。
 */
@Slf4j
@Component
public class RealtimeBroadcaster implements WsBroadcastBatcher.LocalSender {

    private final DeviceWebSocketHandler handler;

    private final SubscriptionRegistry registry;

    private final WsBroadcastBatcher batcher;

    private final RedisSubscriptionRegistry redisRegistry;

    public RealtimeBroadcaster(DeviceWebSocketHandler handler,
                               InMemorySubscriptionRegistry inMemoryRegistry,
                               WsBroadcastBatcher batcher,
                               RedisSubscriptionRegistry redisRegistry) {
        this.handler = handler;
        this.registry = redisRegistry.isEnabled() ? redisRegistry : inMemoryRegistry;
        this.batcher = batcher;
        this.redisRegistry = redisRegistry;
    }

    /**
     * 绑定本地发送器，避免 Handler ↔ Broadcaster 的构造循环
     */
    @PostConstruct
    public void init() {
        batcher.setLocalSender(this);
    }

    /**
     * 提交一次实时更新(合帧后推送)
     *
     * @param userId        用户邮箱
     * @param serializedJson 已序列化的 realtime_update 消息
     */
    public void submit(String userId, String serializedJson) {
        if (serializedJson == null || serializedJson.isEmpty()) {
            return;
        }
        // 没有任何订阅者时直接跳过，省掉一次序列化/排队
        if (registry.subscriberCountOf(userId) == 0 && !redisRegistry.isEnabled()) {
            return;
        }
        batcher.submit(userId, serializedJson);
    }

    /**
     * 合帧窗口结束后真正发送
     *
     * @param userId  用户邮箱
     * @param payload 已序列化 JSON
     */
    @Override
    public void sendToLocalSubscribers(String userId, String payload) {
        if (redisRegistry.isEnabled()) {
            // 多实例：发布到频道，由各实例(含本实例)的监听器推给本地连接
            redisRegistry.publish(userId, payload);
            return;
        }

        Set<SubscriptionSession> subscribers = registry.subscribersOf(userId);
        if (subscribers.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (SubscriptionSession session : subscribers) {
            if (!session.isOpen()) {
                registry.removeSession(session);
                continue;
            }
            try {
                session.send(payload);
                delivered++;
            } catch (Exception e) {
                log.warn("实时数据推送失败，移除连接: sessionId={}, error={}",
                        session.getId(), e.getMessage());
                registry.removeSession(session);
            }
        }
        log.debug("实时数据推送完成: userId={}, 订阅数={}, 成功={}", userId, subscribers.size(), delivered);
    }

    /**
     * 序列化辅助(供上传链路直接使用)
     *
     * @param userId       用户邮箱
     * @param timestamp    时间戳
     * @param applications 应用列表
     * @param statistics   统计数据
     * @return JSON 字符串
     */
    public String serialize(String userId,
                            java.time.Instant timestamp,
                            java.util.List<top.primordialcode.backend.dto.DataUpload.ApplicationDTO> applications,
                            top.primordialcode.backend.dto.DataUpload.StatisticsDTO statistics) {
        return handler.serialize(timestamp, userId, applications, statistics);
    }
}