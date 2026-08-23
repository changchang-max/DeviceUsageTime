package top.primordialcode.backend.vo.user;

import lombok.Data;

/**
 * 重新生成秘钥返回VO
 */
@Data
public class RegenerateKeyVO {
    /**
     * 新的秘钥
     */
    private String secretKey;
}
