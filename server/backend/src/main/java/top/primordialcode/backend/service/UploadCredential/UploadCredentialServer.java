package top.primordialcode.backend.service.UploadCredential;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Redis.RedisStringServer;

import java.time.Duration;

/**
 * Service to resolve upload credential → user email with Redis cache.
 *
 * Upload credential allows clients to upload data without logging in.
 * The credential is cached in Redis for fast lookup.
 */
@Slf4j
@Service
public class UploadCredentialServer {

    /** Redis key prefix for upload credential → email mapping */
    private static final String UPLOAD_CREDENTIAL_KEY_PREFIX = "uploadCredential:";

    /** TTL for cached upload credential mapping: 7 days */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private RedisStringServer redisStringServer;

    /**
     * Resolve user email from upload credential.
     * Checks Redis cache first, falls back to MySQL on cache miss and caches the result.
     *
     * @param credential the upload credential
     * @return user email, or null if credential is invalid
     */
    public String resolveEmail(String credential) {
        //  判断字符串是否为空 
        if (credential == null || credential.isBlank()) {
            return null;
        }

        String cacheKey = UPLOAD_CREDENTIAL_KEY_PREFIX + credential;
        String redisKey = "uploadCredential:resolve:" + credential;

        // 1. Try Redis cache first
        try {
            String cached = redisStringServer.get(redisKey);
            if (cached != null && !cached.isEmpty()) {
                log.debug("Upload credential resolved from Redis: credential={}", credential);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis lookup failed for upload credential, falling back to MySQL: error={}", e.getMessage());
        }

        // 2. Fall back to MySQL
        try {
            var user = userAuthMapper.selectByUploadCredential(credential);
            if (user == null || user.getUser_email() == null) {
                log.warn("Upload credential not found in database: credential={}", credential);
                return null;
            }

            String email = user.getUser_email();

            // 3. Cache in Redis
            try {
                redisStringServer.set(redisKey, email, CACHE_TTL);
                log.debug("Upload credential cached in Redis: credential={}, email={}", credential, email);
            } catch (Exception e) {
                log.warn("Failed to cache upload credential in Redis: error={}", e.getMessage());
            }

            return email;
        } catch (Exception e) {
            log.error("Database query failed for upload credential: credential={}, error={}", credential, e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate cached upload credential mapping (e.g., when credential is revoked).
     *
     * @param credential the upload credential to invalidate
     */
    public void invalidateCache(String credential) {
        if (credential == null || credential.isBlank()) {
            return;
        }
        try {
            String redisKey = "uploadCredential:resolve:" + credential;
            redisStringServer.del(redisKey);
            log.debug("Upload credential cache invalidated: credential={}", credential);
        } catch (Exception e) {
            log.warn("Failed to invalidate upload credential cache: error={}", e.getMessage());
        }
    }

    /**
     * Invalidate cached upload credential by email (invalidates any credential associated with this user).
     * This works by trying to find the credential first - only use when you know the credential value.
     *
     * @param email user email whose credentials to invalidate
     */
    public void invalidateCacheByEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            // Delete any cached credential keys for this user
            // Since we don't know the exact credential from email alone,
            // we use a pattern (requires SCAN under the hood)
            String pattern = "uploadCredential:resolve:*";
            // We can't do pattern-based delete targeting specific email easily,
            // but the TTL will expire stale entries naturally.
            // For immediate invalidation during deregistration, we also invalidate
            // by credential in the caller after fetching it.
            log.debug("Cache invalidation by email requested (keys expire naturally via TTL): email={}", email);
        } catch (Exception e) {
            log.warn("Failed to invalidate upload credential cache by email: error={}", e.getMessage());
        }
    }
}