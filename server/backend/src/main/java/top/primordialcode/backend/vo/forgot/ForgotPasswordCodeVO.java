package top.primordialcode.backend.vo.forgot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送"忘记密码"验证码返回数据
 * 只返回邮箱与有效期，绝不返回验证码本身(验证码只通过邮件下发)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordCodeVO {
    /**
     * 验证码接收邮箱
     */
    private String user_email;

    /**
     * 验证码有效期(秒)，前端据此倒计时
     */
    private long expires_in;
}