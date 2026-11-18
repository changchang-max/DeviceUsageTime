package top.primordialcode.backend.service.WebSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时推送合帧器
 *
 * 客户端约 1 秒上传一次，每次上传都会触发一次广播。若逐个立即推送：
 * - 查看者端图表每秒收到一次全量快照，动画被打断、看不出增长；
 * - 多应用场景下带宽翻倍。
 *
 * 这里按"用户 + 时间窗(默认 500ms)"合并：窗口内多次提交只推最后一次的快照，
 * 既保证最终一致，又把推送频率稳定在 ~2 次/秒以内。
 *
 * 实现为"延迟刷新队列"：提交时若该用户没有待处理任务则安排一次 flush，
 * flush 时取出最新载荷并推给订阅者。
 */
@Slf4j
@Component
public class WsBroadcastBatcher {

    /** 合帧时间窗(毫秒) */
    private static final long WINDOW_MILLIS = 500L;

    /** 用户 → 待推送的最新载荷 */
    private final Map<String, PendingPayload> pending = new ConcurrentHashMap<>();

    /** 用户 → 已安排的刷新任务(避免重复安排) */
    private final Map<String, Boolean> scheduled = new ConcurrentHashMap<>();

    private final SubscriptionRegistry registry;

    /** 由 DeviceWebSocketHandler / RealtimeBroadcaster 注入，用于把 payload 推给本地连接 */
    private volatile LocalSender localSender;

    public WsBroadcastBatcher(InMemorySubscriptionRegistry inMemoryRegistry,
                              RedisSubscriptionRegistry redisRegistry) {
        // 两种实现同时存在，按 realtime.broker 选择运行时使用的那一个
        this.registry = redisRegistry.isEnabled() ? redisRegistry : inMemoryRegistry;
    }

    /**
     * 设置本地发送器(避免 Handler 与本类循环依赖)
     *
     * @param localSender 发送器实现
     */
    public void setLocalSender(LocalSender localSender) {
        this.localSender = localSender;
    }

    /**
     * 提交一次待推送载荷(同用户同窗口内只保留最后一份)
     *
     * @param userId  用户邮箱
     * @param payload 已序列化的 JSON
     */
    public void submit(String userId, String payload) {
        pending.put(userId, new PendingPayload(payload, System.currentTimeMillis()));

        if (scheduled.putIfAbsent(userId, Boolean.TRUE) != null) {
            // 已有刷新任务在排队，本次只更新载荷
            return;
        }

        Thread flushThread = new Thread(() -> flushAfterWindow(userId), "ws-batch-flush");
        flushThread.setDaemon(true);
        flushThread.start();
    }

    /**
     * 等待窗口结束后推送最新载荷
     *
     * @param userId 用户邮箱
     */
    private void flushAfterWindow(String userId) {
        try {
            Thread.sleep(WINDOW_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            scheduled.remove(userId);
        }

        PendingPayload latest = pending.remove(userId);
        if (latest == null) {
            return;
        }

        LocalSender sender = localSender;
        if (sender == null) {
            log.warn("推送合帧器未绑定本地发送器，丢弃本次推送: userId={}", userId);
            return;
        }
        sender.sendToLocalSubscribers(userId, latest.payload());
    }

    /**
     * 立即清空某个用户的待推送载荷(连接全部断开时可调用)
     *
     * @param userId 用户邮箱
     */
    public void clear(String userId) {
        pending.remove(userId);
    }

    /**
     * 当前待推送用户数(供监控)
     *
     * @return 待推送数量
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * 待推送载荷
     *
     * @param payload   已序列化 JSON
     * @param timestamp 提交时间
     */
    private record PendingPayload(String payload, long timestamp) {
    }

    /**
     * 本地发送器：把 payload 推给本实例上的订阅者
     */
    public interface LocalSender {

        /**
         * 推送给本实例上订阅了该用户的连接
         *
         * @param userId  用户邮箱
         * @param payload 已序列化 JSON
         */
        void sendToLocalSubscribers(String userId, String payload);
    }
}