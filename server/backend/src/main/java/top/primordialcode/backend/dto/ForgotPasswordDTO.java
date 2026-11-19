package top.primordialcode.backend.dto;

import lombok.Data;

/**
 * 忘记密码(凭验证码重置密码)请求参数
 */
@Data
public class ForgotPasswordDTO {
    /**
     * 用户邮箱，必填(与数据库实体 user_email 字段保持一致)
     */
    private String user_email;

    /**
     * 邮箱验证码，必填，3分钟内有效且仅可使用一次
     */
    private String code;

    /**
     * 新密码，必填，至少8位且同时包含字母和数字
     */
    private String new_password;
}