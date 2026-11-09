package top.primordialcode.backend.service.User;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.UploadCredential.UploadCredentialServer;
import top.primordialcode.backend.utils.GenKeyUtil;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private UploadCredentialServer uploadCredentialServer;

    /**
     * 根据用户邮箱获取用户档案信息
     * @param email 用户邮箱
     * @return 用户实体类，不包含密码
     */
    public UserAuthEntity getUserProfile(String email) {
        log.info("获取用户档案，邮箱：{}", email);
        
        UserAuthEntity user = userAuthMapper.selectProfileByEmail(email);
        
        if (user == null) {
            log.warn("用户不存在，邮箱：{}", email);
            throw new RuntimeException("用户不存在");
        }
        
        return user;
    }

    /**
     * 重新生成用户秘钥
     * @param email 用户邮箱
     * @return 新生成的秘钥
     */
    public String regenerateSecretKey(String email) {
        log.info("重新生成秘钥，邮箱：{}", email);
        
        // 检查用户是否存在
        boolean exists = userAuthMapper.existsByEmail(email);
        if (!exists) {
            log.warn("用户不存在，邮箱：{}", email);
            throw new RuntimeException("用户不存在");
        }
        
        // 生成新的秘钥
        String newKey = GenKeyUtil.generateKey(16);
        log.info("生成新秘钥，邮箱：{}，新秘钥：{}", email, newKey);
        
        // 更新数据库中的秘钥
        int affectedRows = userAuthMapper.updateKey(email, newKey);
        
        if (affectedRows == 0) {
            log.error("更新秘钥失败，邮箱：{}", email);
            throw new RuntimeException("更新秘钥失败");
        }
        
        log.info("秘钥更新成功，邮箱：{}", email);
        return newKey;
    }

    /**
     * 作废用户秘钥
     * @param email 用户邮箱
     */
    public void revokeSecretKey(String email) {
        log.info("作废秘钥，邮箱：{}", email);
        
        // 检查用户是否存在
        boolean exists = userAuthMapper.existsByEmail(email);
        if (!exists) {
            log.warn("用户不存在，邮箱：{}", email);
            throw new RuntimeException("用户不存在");
        }
        
        // 将秘钥设置为null
        int affectedRows = userAuthMapper.revokeKey(email);
        
        if (affectedRows == 0) {
            log.error("作废秘钥失败，邮箱：{}", email);
            throw new RuntimeException("作废秘钥失败");
        }
        
        log.info("秘钥作废成功，邮箱：{}", email);
    }

    /**
     * 重新生成上传凭证
     * @param email 用户邮箱
     * @return 新生成的上传凭证
     */
    public String regenerateUploadCredential(String email) {
        log.info("重新生成上传凭证，邮箱：{}", email);
        
        // 检查用户是否存在
        boolean exists = userAuthMapper.existsByEmail(email);
        if (!exists) {
            log.warn("用户不存在，邮箱：{}", email);
            throw new RuntimeException("用户不存在");
        }
        
        // 生成新的上传凭证（使用秘钥相同的生成算法，但长度略短以区分用途）
        String newCredential = GenKeyUtil.generateKey(16);
        log.info("生成新上传凭证，邮箱：{}", email);
        
        // 更新数据库中的上传凭证
        int affectedRows = userAuthMapper.updateUploadCredential(email, newCredential);
        
        if (affectedRows == 0) {
            log.error("更新上传凭证失败，邮箱：{}", email);
            throw new RuntimeException("更新上传凭证失败");
        }
        
        log.info("上传凭证更新成功，邮箱：{}", email);
        return newCredential;
    }

    /**
     * 作废上传凭证
     * @param email 用户邮箱
     */
    public void revokeUploadCredential(String email) {
        log.info("作废上传凭证，邮箱：{}", email);
        
        // 检查用户是否存在
        boolean exists = userAuthMapper.existsByEmail(email);
        if (!exists) {
            log.warn("用户不存在，邮箱：{}", email);
            throw new RuntimeException("用户不存在");
        }
        
        // 先查询当前凭证，用于清除Redis缓存
        UserAuthEntity user = userAuthMapper.selectProfileByEmail(email);
        String oldCredential = user != null ? user.getUpload_credential() : null;
        
        // 将上传凭证设置为null
        int affectedRows = userAuthMapper.revokeUploadCredential(email);
        
        if (affectedRows == 0) {
            log.error("作废上传凭证失败，邮箱：{}", email);
            throw new RuntimeException("作废上传凭证失败");
        }
        
        // 清除Redis缓存
        if (oldCredential != null) {
            uploadCredentialServer.invalidateCache(oldCredential);
        }
        
        log.info("上传凭证作废成功，邮箱：{}", email);
    }
}
