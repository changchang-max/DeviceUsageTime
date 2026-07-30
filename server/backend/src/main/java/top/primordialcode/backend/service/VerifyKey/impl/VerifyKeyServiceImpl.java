package top.primordialcode.backend.service.VerifyKey.impl;

import top.primordialcode.backend.vo.VerifyKeyVO;

/**
 * 秘钥验证服务接口
 */
public interface VerifyKeyServiceImpl {
    /**
     * 验证秘钥并返回VO对象
     * @param secretKey 要验证的秘钥
     * @return VerifyKeyVO对象,包含用户昵称和ROLE_VISITOR角色的token
     */
    VerifyKeyVO verifyKeyAndGenerateToken(String secretKey);
}
