package top.primordialcode.backend.service.Redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.service.Redis.impl.RedisStringServerImpl;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
public class RedisStringServer implements RedisStringServerImpl {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value,ttl);
    }

    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void del(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public boolean existKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public void deleteKeysByPattern(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        stringRedisTemplate.delete(keys);
        log.info("按模式批量删除Redis键: pattern={}, 数量={}", pattern, keys.size());
    }

}
