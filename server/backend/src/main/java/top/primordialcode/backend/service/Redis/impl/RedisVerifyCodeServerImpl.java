package top.primordialcode.backend.service.Redis.impl;

import java.time.Duration;

/**
 * 验证码专用 Redis 操作接口
 * 与通用字符串操作分开，便于按场景设置 TTL、以及原子"取出并删除"
 */
public interface RedisVerifyCodeServerImpl {

    /**
     * 保存验证码并设置存活时长(重复写入会覆盖旧验证码与剩余TTL)
     * @param key 验证码在Redis中的键
     * @param code 验证码原文
     * @param ttl 存活时长
     */
    void set(String key, String code, Duration ttl);

    /**
     * 原子核验并消费验证码：比对一致时立即删除该键，保证"一次有效"
     * @param key 验证码在Redis中的键
     * @param expected 用户提交的验证码
     * @return true：校验通过(键已被删除)；false：验证码错误/已过期/已被使用
     */
    boolean verifyAndDelete(String key, String expected);

    /**
     * 删除验证码
     * @param key 验证码在Redis中的键
     */
    void del(String key);
}