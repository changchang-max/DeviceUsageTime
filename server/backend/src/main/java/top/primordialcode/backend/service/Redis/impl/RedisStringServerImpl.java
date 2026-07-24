package top.primordialcode.backend.service.Redis.impl;

import java.time.Duration;

public interface RedisStringServerImpl {
    void set(String key, String value, Duration ttl);
    String get(String key);
    void del(String key);
    boolean existKey(String key);
}
