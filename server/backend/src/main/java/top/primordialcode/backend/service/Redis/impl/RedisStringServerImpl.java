package top.primordialcode.backend.service.Redis.impl;

import java.time.Duration;

public interface RedisStringServerImpl {
    void set(String key, String value, Duration ttl);
    String get(String key);
    void del(String key);
    boolean existKey(String key);
    /**
     * 按通配符模式批量删除键
     * @param pattern 键的匹配模式(如 uploadDataDevice:*:user@example.com)
     */
    void deleteKeysByPattern(String pattern);
}
