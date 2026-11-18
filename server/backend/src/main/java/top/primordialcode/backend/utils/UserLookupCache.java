package top.primordialcode.backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import top.primordialcode.backend.config.CacheConfig.CacheNames;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;

/**
 * 用户身份查询缓存工具
 *
 * 把「秘钥 → 邮箱」「邮箱是否注册」这两条最高频的 MySQL 查询收口到一处，
 * 避免各 Service 各自查库、各自忘记清缓存。
 *
 * 注意：@Cacheable 依赖 Spring AOP，同类内部自调用不会命中缓存，
 * 因此需要一个独立的 Bean，其它 Service 通过它查询。
 */
@Slf4j
@Component
public class UserLookupCache {

    @Autowired
    private UserAuthMapper userAuthMapper;

    /**
     * 根据秘钥解析用户邮箱(带缓存)
     *
     * @param userKey 用户秘钥
     * @return 用户邮箱，秘钥无效时返回 null
     */
    @Cacheable(cacheNames = CacheNames.SECRET_KEY_TO_EMAIL, key = "#userKey", unless = "#result == null")
    public String resolveEmailByKey(String userKey) {
        UserAuthEntity user = userAuthMapper.selectByKey(userKey);
        return user != null ? user.getUser_email() : null;
    }

    /**
     * 根据秘钥解析完整用户实体(不缓存，仅用于需要 profile 的场景)
     *
     * @param userKey 用户秘钥
     * @return 用户实体(仅含 user_email/user_name 等)，无效时返回 null
     */
    public UserAuthEntity selectByKey(String userKey) {
        return userAuthMapper.selectByKey(userKey);
    }

    /**
     * 邮箱是否已注册(带缓存)
     *
     * @param userEmail 用户邮箱
     * @return true：已注册
     */
    @Cacheable(cacheNames = CacheNames.USER_EXISTS, key = "#userEmail")
    public boolean existsByEmail(String userEmail) {
        return userAuthMapper.existsByEmail(userEmail);
    }

    /**
     * 清除某个秘钥的缓存(重新生成/作废秘钥后必须调用)
     *
     * @param userKey 已失效的旧秘钥
     */
    @CacheEvict(cacheNames = CacheNames.SECRET_KEY_TO_EMAIL, key = "#userKey")
    public void evictKey(String userKey) {
        log.debug("秘钥缓存已清除: {}", MaskUtil.fingerprint(userKey));
    }

    /**
     * 清除某个邮箱的存在性缓存与日期缓存(注销账号后调用)
     *
     * @param userEmail 用户邮箱
     */
    @CacheEvict(cacheNames = {CacheNames.USER_EXISTS, CacheNames.DATA_DATES}, allEntries = true)
    public void evictUser(String userEmail) {
        log.debug("用户缓存已清除: {}", MaskUtil.maskEmail(userEmail));
    }
}