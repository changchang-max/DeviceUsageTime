package top.primordialcode.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动期一次性维护任务
 *
 * 1. 清理升级前遗留的"无日期分桶"旧 key。原实现把这段清理挂在每次上传的
 *    热路径上(每次 3 条 DEL)，现在改为启动时用 SCAN 扫一遍，热路径不再发多余命令。
 * 2. 定期回收空闲/失效的 WebSocket 连接(客户端被强杀时 TCP 可能长期不关)。
 */
@Slf4j
@Configuration
public class StartupMaintenanceConfig {

    private final StringRedisTemplate redisTemplate;

    private final RedisDataUploadServer redisDataUploadServer;

    private final DeviceWebSocketHandler deviceWebSocketHandler;

    public StartupMaintenanceConfig(
            StringRedisTemplate redisTemplate,
            RedisDataUploadServer redisDataUploadServer,
            DeviceWebSocketHandler deviceWebSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.redisDataUploadServer = redisDataUploadServer;
        this.deviceWebSocketHandler = deviceWebSocketHandler;
    }

    /**
     * 启动时清理遗留 key(SCAN 分批，避免 KEYS 阻塞 Redis)
     *
     * @param enabled 是否启用清理，默认 true
     * @return ApplicationRunner
     */
    @Bean
    public ApplicationRunner legacyKeyCleanupRunner(
            @Value("${redis.legacy-cleanup.enabled:true}") boolean enabled) {
        return args -> {
            if (!enabled) {
                log.info("跳过遗留 Redis key 清理(redis.legacy-cleanup.enabled=false)");
                return;
            }
            for (String pattern : redisDataUploadServer.legacyKeyPatterns()) {
                int removed = scanAndDelete(pattern);
                if (removed > 0) {
                    log.info("已清理遗留 Redis key: pattern={}, 数量={}", pattern, removed);
                }
            }
        };
    }

    /**
     * 定期回收空闲 WebSocket 连接
     *
     * @param idleTimeoutSeconds 超过该时长未活跃即断开，默认 180 秒(心跳周期 30 秒)
     * @param enabled            是否启用，默认 true
     * @return ApplicationRunner(注册一个守护线程，结束后自动退出)
     */
    @Bean
    public ApplicationRunner idleSessionEvictor(
            @Value("${realtime.idle-timeout-seconds:180}") long idleTimeoutSeconds,
            @Value("${realtime.idle-evict.enabled:true}") boolean enabled) {
        return args -> {
            if (!enabled) {
                return;
            }
            Thread evictor = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(60_000L);
                        deviceWebSocketHandler.evictIdleSessions(idleTimeoutSeconds * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.warn("WebSocket 空闲连接回收失败: {}", e.getMessage());
                    }
                }
            }, "ws-idle-evictor");
            evictor.setDaemon(true);
            evictor.start();
        };
    }

    /**
     * 用 SCAN 分批查找并删除匹配的 key
     *
     * @param pattern key 模式
     * @return 删除数量
     */
    private int scanAndDelete(String pattern) {
        List<String> batch = new ArrayList<>(256);
        int total = 0;
        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= 500) {
                        total += deleteBatch(batch);
                    }
                }
            }
            total += deleteBatch(batch);
        } catch (Exception e) {
            log.warn("扫描遗留 Redis key 失败: pattern={}, error={}", pattern, e.getMessage());
        }
        return total;
    }

    /**
     * 批量删除并清空当前批次
     *
     * @param batch key 列表(调用后清空)
     * @return 删除数量
     */
    private int deleteBatch(List<String> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        int size = batch.size();
        try {
            redisTemplate.delete(batch);
        } catch (Exception e) {
            log.warn("删除遗留 Redis key 失败: size={}, error={}", size, e.getMessage());
        }
        batch.clear();
        return size;
    }
}