package top.primordialcode.backend.service.Redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.service.Redis.impl.RedisVerifyCodeServerImpl;

import java.time.Duration;
import java.util.List;

/**
 * 验证码 Redis 服务
 * 核心在于 verifyAndDelete：比对与删除在 Redis 内一次原子完成，
 * 避免"校验通过后进程崩溃导致验证码仍可复用"的窗口。
 */
@Slf4j
@Service
public class RedisVerifyCodeServer implements RedisVerifyCodeServerImpl {

    /** 比对一致则删除，返回1；键不存在或值不一致返回0 */
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 保存验证码并设置TTL
     * @param key 验证码键
     * @param code 验证码原文
     * @param ttl 存活时长
     */
    @Override
    public void set(String key, String code, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, code, ttl);
    }

    /**
     * 原子核验并消费验证码
     * @param key 验证码键
     * @param expected 用户提交的验证码
     * @return true：校验通过且该验证码已被删除
     */
    @Override
    public boolean verifyAndDelete(String key, String expected) {
        if (expected == null) {
            return false;
        }
        Long result = stringRedisTemplate.execute(
                VERIFY_AND_DELETE_SCRIPT,
                List.of(key),
                expected);
        return result != null && result > 0;
    }

    /**
     * 删除验证码
     * @param key 验证码键
     */
    @Override
    public void del(String key) {
        stringRedisTemplate.delete(key);
    }
}