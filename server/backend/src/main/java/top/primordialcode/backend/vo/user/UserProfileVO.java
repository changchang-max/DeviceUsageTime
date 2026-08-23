package top.primordialcode.backend.vo.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * 用户档案信息返回VO
 */
@Data
public class UserProfileVO {
    /**
     * 用户邮箱
     */
    private String email;
    
    /**
     * 用户秘钥
     */
    private String secretKey;
    
    /**
     * 账户创建时间
     */
    private Instant createdAt;
    
    /**
     * 最后登录时间
     */
    private Instant lastLoginAt;
}
