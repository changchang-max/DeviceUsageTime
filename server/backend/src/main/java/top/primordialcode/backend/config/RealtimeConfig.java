package top.primordialcode.backend.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.service.WebSocket.RedisSubscriptionRegistry;

/**
 * 实时推送相关配置
 *
 * 1. 多实例模式(realtime.broker=redis)时开启 Redis 频道监听，
 *    让任意实例收到的上传都能广播到全部查看者；
 * 2. 注册 WebSocket 在线连接数指标，便于观察容量。
 */
@Slf4j
@Configuration
public class RealtimeConfig {

    /**
     * Redis 频道监听容器
     *
     * 只在启用多实例广播时创建，单机部署不会白白占用一条订阅连接。
     *
     * @param factory        Redis 连接工厂
     * @param registry       跨实例注册表
     * @param broker         广播模式开关
     * @return 监听容器；单机模式返回 null(不注册为 Bean)
     */
    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(
            RedisConnectionFactory factory,
            RedisSubscriptionRegistry registry,
            @Value("${realtime.broker:memory}") String broker) {

        if (!"redis".equalsIgnoreCase(broker)) {
            return null;
        }

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(registry, new ChannelTopic(RedisSubscriptionRegistry.CHANNEL_PREFIX + "*"));
        log.info("实时数据广播已启用 Redis Pub/Sub 模式");
        return container;
    }

    /**
     * WebSocket 在线连接数指标：websocket.sessions.online
     *
     * 在容器初始化完成后注册，避免直接以 Gauge 作为 Bean 类型造成的歧义。
     *
     * @param registry 指标注册表
     * @param handler  WebSocket 处理器
     * @return 初始化完成回调
     */
    @Bean
    public SmartInitializingSingleton webSocketSessionGauge(
            MeterRegistry registry, DeviceWebSocketHandler handler) {
        return () -> Gauge.builder("websocket.sessions.online", handler, DeviceWebSocketHandler::onlineSessionCount)
                .description("当前在线 WebSocket 连接数")
                .register(registry);
    }
}