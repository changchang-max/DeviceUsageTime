package top.primordialcode.backend.service.WebSocket;

import java.util.Set;

/**
 * WebSocket 订阅注册表抽象
 *
 * 单实例部署用内存实现({@link InMemorySubscriptionRegistry})；
 * 多实例部署换成 Redis Pub/Sub 实现后，任何实例收到上传广播，
 * 只会推给"连在本实例上"的订阅者，功能不再随扩容而失效。
 */
public interface SubscriptionRegistry {

    /**
     * 建立订阅关系(幂等)
     *
     * @param userId  被订阅的用户标识(本系统中为用户邮箱)
     * @param session 订阅连接
     */
    void subscribe(String userId, SubscriptionSession session);

    /**
     * 取消订阅关系(幂等)
     *
     * @param userId  被取消订阅的用户标识
     * @param session 订阅连接
     */
    void unsubscribe(String userId, SubscriptionSession session);

    /**
     * 移除某个连接的全部订阅关系(连接关闭/异常时调用)
     *
     * @param session 断开的连接
     */
    void removeSession(SubscriptionSession session);

    /**
     * 返回订阅了指定用户的连接快照(调用方可以安全遍历)
     *
     * @param userId 被订阅的用户标识
     * @return 订阅连接集合，无订阅时为空集合
     */
    Set<SubscriptionSession> subscribersOf(String userId);

    /**
     * 当前本实例上的订阅连接总数(用于监控指标)
     *
     * @return 连接数
     */
    int sessionCount();

    /**
     * 某个用户的订阅连接数(用于日志与指标)
     *
     * @param userId 被订阅的用户标识
     * @return 连接数
     */
    int subscriberCountOf(String userId);
}