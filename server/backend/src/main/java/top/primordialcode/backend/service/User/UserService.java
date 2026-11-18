package top.primordialcode.backend.service.User;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.UploadCredential.UploadCredentialServer;
import top.primordialcode.backend.utils.GenKeyUtil;
import top.primordialcode.backend.utils.MaskUtil;
import top.primordialcode.backend.utils.UserLookupCache;

/**
 * 用户档案与秘钥管理服务
 *
 * 秘钥变更后必须清缓存(否则旧秘钥在缓存有效期内仍可通过鉴权)，
 * 这里统一通过 {@link UserLookupCache#evictKey} 处理。
 * 日志中的邮箱与秘钥一律脱敏，避免隐私落盘。
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private UploadCredentialServer uploadCredentialServer;

    @Autowired
    private UserLookupCache userLookupCache;

    /**
     * 根据用户邮箱获取用户档案信息
     *
     * @param email 用户邮箱
     * @return 用户实体类，不包含密码
     */
    public UserAuthEntity getUserProfile(String email) {
        UserAuthEntity user = userAuthMapper.selectProfileByEmail(email);
        if (user == null) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    /**
     * 重新生成用户秘钥
     *
     * @param email 用户邮箱
     * @return 新生成的秘钥
     */
    public String regenerateSecretKey(String email) {
        UserAuthEntity profile = userAuthMapper.selectProfileByEmail(email);
        if (profile == null) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("用户不存在");
        }
        // 旧秘钥需要立刻失效：先 evict 缓存，再更新数据库
        String oldKey = profile.getUser_key();
        if (oldKey != null && !oldKey.isBlank()) {
            userLookupCache.evictKey(oldKey);
        }

        String newKey = GenKeyUtil.generateKey(16);
        int affectedRows = userAuthMapper.updateKey(email, newKey);
        if (affectedRows == 0) {
            log.error("更新秘钥失败，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("更新秘钥失败");
        }

        log.info("秘钥已重新生成，邮箱：{}，新秘钥：{}", MaskUtil.maskEmail(email), MaskUtil.maskSecret(newKey));
        return newKey;
    }

    /**
     * 作废用户秘钥
     *
     * @param email 用户邮箱
     */
    public void revokeSecretKey(String email) {
        UserAuthEntity profile = userAuthMapper.selectProfileByEmail(email);
        if (profile == null) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("用户不存在");
        }

        String oldKey = profile.getUser_key();
        int affectedRows = userAuthMapper.revokeKey(email);
        if (affectedRows == 0) {
            log.error("作废秘钥失败，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("作废秘钥失败");
        }

        // 作废后清缓存，避免旧秘钥在缓存 TTL 内继续可用
        if (oldKey != null && !oldKey.isBlank()) {
            userLookupCache.evictKey(oldKey);
        }
        log.info("秘钥已作废，邮箱：{}", MaskUtil.maskEmail(email));
    }

    /**
     * 重新生成上传凭证
     *
     * @param email 用户邮箱
     * @return 新生成的上传凭证
     */
    public String regenerateUploadCredential(String email) {
        UserAuthEntity profile = userAuthMapper.selectProfileByEmail(email);
        if (profile == null) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("用户不存在");
        }

        // 旧凭证的解析缓存要先失效
        String oldCredential = profile.getUpload_credential();
        String newCredential = GenKeyUtil.generateKey(16);

        int affectedRows = userAuthMapper.updateUploadCredential(email, newCredential);
        if (affectedRows == 0) {
            log.error("更新上传凭证失败，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("更新上传凭证失败");
        }

        if (oldCredential != null && !oldCredential.isBlank()) {
            uploadCredentialServer.invalidateCache(oldCredential);
        }
        log.info("上传凭证已更新，邮箱：{}", MaskUtil.maskEmail(email));
        return newCredential;
    }

    /**
     * 作废上传凭证
     *
     * @param email 用户邮箱
     */
    public void revokeUploadCredential(String email) {
        UserAuthEntity profile = userAuthMapper.selectProfileByEmail(email);
        if (profile == null) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("用户不存在");
        }

        String oldCredential = profile.getUpload_credential();
        int affectedRows = userAuthMapper.revokeUploadCredential(email);
        if (affectedRows == 0) {
            log.error("作废上传凭证失败，邮箱：{}", MaskUtil.maskEmail(email));
            throw new RuntimeException("作废上传凭证失败");
        }

        if (oldCredential != null && !oldCredential.isBlank()) {
            uploadCredentialServer.invalidateCache(oldCredential);
        }
        log.info("上传凭证已作废，邮箱：{}", MaskUtil.maskEmail(email));
    }
}