package top.primordialcode.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置(Caffeine)
 *
 * 缓存的对象都是"读多写少、短时不一致可接受"的数据：
 * - secretKeyToEmail: 秘钥 → 用户邮箱，实时页每秒轮询都会命中；
 * - userExists:       邮箱是否已注册，上传/查询鉴权路径都会用到；
 * - dataDates:        某用户某月的"有数据日期"列表，日历用，一天最多变几次。
 *
 * 失效策略：写路径(重新生成/作废秘钥、注销账号)显式 evict，
 * 同时用 TTL 兜底，避免多实例部署时其他实例缓存不失效导致长期脏读。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 单个缓存最多保留的条目数，防止被大量随机秘钥打爆内存 */
    @Value("${cache.max-size:10000}")
    private long maxSize;

    /**
     * 缓存管理器：按缓存名使用不同的过期时间
     *
     * @return Caffeine CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CacheNames.SECRET_KEY_TO_EMAIL,
                CacheNames.USER_EXISTS,
                CacheNames.DATA_DATES);

        // 默认过期时间(秘钥→邮箱)：写路径已显式 evict，TTL 只需兜底
        // recordStats() 让 Micrometer 能输出命中率(否则启动时会告警"未记录统计")
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());

        // 每个缓存单独配置
        manager.registerCustomCache(CacheNames.USER_EXISTS,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .recordStats()
                        .build());

        manager.registerCustomCache(CacheNames.DATA_DATES,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .recordStats()
                        .build());

        return manager;
    }

    /**
     * 缓存名称常量，避免各处手写字符串拼错
     */
    public static final class CacheNames {

        /** 秘钥 → 用户邮箱 */
        public static final String SECRET_KEY_TO_EMAIL = "secretKeyToEmail";

        /** 用户邮箱 → 是否已注册 */
        public static final String USER_EXISTS = "userExists";

        /** 用户邮箱 + 年月 → 有数据的日期列表 */
        public static final String DATA_DATES = "dataDates";

        private CacheNames() {
        }
    }
}