package top.primordialcode.backend.dto;

import lombok.Data;

/**
 * 发送"忘记密码"验证码请求参数
 */
@Data
public class SendResetCodeDTO {
    /**
     * 用户邮箱，必填(与数据库实体 user_email 字段保持一致)
     */
    private String user_email;
}