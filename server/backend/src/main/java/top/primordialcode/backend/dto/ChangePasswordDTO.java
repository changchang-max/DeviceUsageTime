package top.primordialcode.backend.dto;

import lombok.Data;

/**
 * 修改密码请求参数
 * 已登录状态(Token有效)时以Token解析出的身份为准，user_email可省略；
 * 未登录时需要通过user_email指定要修改密码的账号。
 */
@Data
public class ChangePasswordDTO {
    /**
     * 用户邮箱，未登录时必填；已登录时即使传入也会被忽略(以Token解析出的身份为准)
     */
    private String user_email;

    /**
     * 当前密码(原密码)，必填
     */
    private String old_password;

    /**
     * 新密码，必填，至少8位且同时包含字母和数字
     */
    private String new_password;
}
