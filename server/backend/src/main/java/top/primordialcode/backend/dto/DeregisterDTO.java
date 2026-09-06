package top.primordialcode.backend.dto;

import lombok.Data;

/**
 * 注销账号请求参数
 * 需要用户再次输入登录密码进行二次确认
 */
@Data
public class DeregisterDTO {
    /**
     * 当前账号的登录密码，用于二次确认
     */
    private String user_password;
}
