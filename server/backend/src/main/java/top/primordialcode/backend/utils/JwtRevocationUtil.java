package top.primordialcode.backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.primordialcode.backend.service.Redis.RedisStringServer;

import java.time.Duration;

/**
 * JWT 凭证吊销工具
 *
 * 项目中的 Token 是无状态 JWT，除了"把某一枚Token加入黑名单"之外，
 * 还需要支持"某账号在某一时刻之前的全部Token一次性失效"(重置密码、强制下线)。
 *
 * 实现：
 * - epoch:jwt:blacklist:[user_email] → 全局失效时间点(毫秒)，
 *   鉴权时只要 Token 的签发时间不晚于该时间点即拒绝；
 * - epoch:jwt:blacklist:[签发时间毫秒]:[user_email] → 历史签发时间戳黑名单，
 *   TTL = 签发时间 + Token有效期 - 当前时间，过期即自然清理，
 *   用于兼容只查询单 Token 黑名单的旧版本实例与 WebSocket 握手校验。
 */
@Slf4j
@Component
public class JwtRevocationUtil {

    /** 全局失效时间点键前缀 */
    public static final String EPOCH_BLACKLIST_PREFIX = "epoch:jwt:blacklist:";

    @Autowired
    private RedisStringServer redisStringServer;

    /**
     * 吊销某账号的全部凭证
     *
     * @param userEmail   目标用户邮箱
     * @param reason      吊销原因(写入Redis值，便于排查)
     * @param jwtExpirationMillis Token有效期(毫秒)
     */
    public void revokeAll(String userEmail, String reason, long jwtExpirationMillis) {
        // 时间戳取秒级(与 JWT iat 精度一致)，确保"同一秒签发"的Token也不会漏网
        long revokeAtMillis = (System.currentTimeMillis() / 1000L) * 1000L;
        long now = System.currentTimeMillis();

        // 1. 全局失效时间点：所有签发时间 <= revokeAtMillis 的Token立即失效
        redisStringServer.set(
                EPOCH_BLACKLIST_PREFIX + userEmail,
                String.valueOf(revokeAtMillis),
                Duration.ofMillis(jwtExpirationMillis));

        // 2. 历史签发时间戳黑名单：逐秒补齐 [now - 有效期, revokeAtMillis] 区间
        for (long issuedAt = revokeAtMillis; issuedAt > now - jwtExpirationMillis; issuedAt -= 1000L) {
            long ttl = issuedAt + jwtExpirationMillis - now;
            if (ttl <= 0) {
                break;
            }
            redisStringServer.set(
                    EPOCH_BLACKLIST_PREFIX + issuedAt + ":" + userEmail,
                    reason,
                    Duration.ofMillis(ttl));
        }

        log.info("账号全部凭证已吊销: user_email={}, reason={}, revoke_at={}",
                MaskUtil.maskEmail(userEmail), reason, revokeAtMillis);
    }

    /**
     * 某账号的全局失效时间点
     *
     * @param userEmail 用户邮箱
     * @return 失效时间戳(毫秒)，未吊销过返回 null
     */
    public Long getRevokeEpoch(String userEmail) {
        String value = redisStringServer.get(EPOCH_BLACKLIST_PREFIX + userEmail);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("账号全局失效时间点格式异常: user_email={}", MaskUtil.maskEmail(userEmail));
            return null;
        }
    }
}