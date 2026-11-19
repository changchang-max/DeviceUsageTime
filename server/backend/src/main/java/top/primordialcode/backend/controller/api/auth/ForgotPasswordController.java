package top.primordialcode.backend.controller.api.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.ForgotPasswordDTO;
import top.primordialcode.backend.dto.SendResetCodeDTO;
import top.primordialcode.backend.service.ForgotPassword.ForgotPasswordServer;

/**
 * 忘记密码控制器(无需登录，SecurityConfig 中已放行)
 * 完整路径：
 * - GET  /api/auth/forgot-password/code      获取重置密码验证码(3分钟有效)
 * - POST /api/auth/forgot-password/reset     凭验证码重置密码
 */
@RestController
@RequestMapping("/auth/forgot-password")
public class ForgotPasswordController {

    private final ForgotPasswordServer forgotPasswordServer;

    public ForgotPasswordController(ForgotPasswordServer forgotPasswordServer) {
        this.forgotPasswordServer = forgotPasswordServer;
    }

    /**
     * 获取重置密码验证码(发送到注册邮箱，3分钟内有效)
     *
     * @param to 收件邮箱(与数据库 user_email 一致)
     * @return 操作结果，成功时 data 含邮箱与有效期秒数
     */
    @GetMapping("/code")
    public Result sendResetCode(String to) {
        SendResetCodeDTO sendResetCodeDTO = new SendResetCodeDTO();
        sendResetCodeDTO.setUser_email(to);
        return forgotPasswordServer.sendResetCode(sendResetCodeDTO);
    }

    /**
     * 凭验证码重置密码
     * 重置成功后该账号所有已签发Token立即失效，所有已登录设备需要重新登录。
     *
     * @param forgotPasswordDTO 请求体(user_email/code/new_password必填)
     * @return 操作结果
     */
    @PostMapping("/reset")
    public Result resetPassword(@RequestBody(required = false) ForgotPasswordDTO forgotPasswordDTO) {
        return forgotPasswordServer.resetPassword(forgotPasswordDTO);
    }
}