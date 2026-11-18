package top.primordialcode.backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的固定窗口计数器限流器
 *
 * 用途：秘钥校验、登录、发送验证码等"可被暴力枚举"的入口，
 * 单个 IP 或单个秘钥指纹在窗口期内超过阈值即拒绝。
 *
 * 实现：INCR + 首次 PEXPIRE，单键单命令，无需 Lua 也具备原子性
 * (INCR 本身原子；PEXPIRE 只在计数为 1 时设置，重复设置会造成窗口漂移，
 *  因此用 Lua 把两步合成一次调用)。
 */
@Slf4j
@Component
public class RateLimiterUtil {

    /** 原子执行 INCR + EXPIRE(仅首次) */
    private static final DefaultRedisScript<Long> INCR_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 尝试消费一次配额
     *
     * @param scene  场景名(用于区分不同限流维度，如 "key-verify")
     * @param subject 限流主体(IP、秘钥指纹、邮箱等)
     * @param limit  窗口期内允许的最大次数
     * @param window 窗口长度
     * @return true：允许本次调用；false：已超限
     */
    public boolean tryAcquire(String scene, String subject, int limit, Duration window) {
        if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return true;
        }
        String redisKey = "rateLimit:" + scene + ":" + subject;
        try {
            Long count = redisTemplate.execute(
                    INCR_WITH_TTL_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(window.toMillis()));
            return count == null || count <= limit;
        } catch (Exception e) {
            // Redis 不可用时放行，避免限流组件故障导致全站不可用(降级策略)
            log.warn("限流计数失败，本次放行: scene={}, error={}", scene, e.getMessage());
            return true;
        }
    }

    /**
     * 清空某个主体的计数(鉴权成功后调用，避免正常用户被历史失败次数拖累)
     *
     * @param scene   场景名
     * @param subject 限流主体
     */
    public void reset(String scene, String subject) {
        try {
            redisTemplate.delete("rateLimit:" + scene + ":" + subject);
        } catch (Exception e) {
            log.warn("限流计数清零失败: scene={}, error={}", scene, e.getMessage());
        }
    }
}