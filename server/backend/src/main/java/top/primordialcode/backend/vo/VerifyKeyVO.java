package top.primordialcode.backend.vo;

import lombok.Data;

/**
 * 秘钥验证返回数据VO
 */
@Data
public class VerifyKeyVO {
    /**
     * 用户昵称
     */
    private String userName;
    
    /**
     * 带有ROLE_VISITOR角色的JWT token
     */
    private String token;
}
