package top.primordialcode.backend.controller.api.auth;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.LoginDTO;
import top.primordialcode.backend.service.Login.LoginServer;
import top.primordialcode.backend.service.Register.MailServer;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class LoginPageController {
    @Autowired
    private MailServer mailServer;
    @Autowired
    private LoginServer loginServer;

    // 生成并发送验证码
    @GetMapping("/sendCode")
    public Result sendVerifiCode(String to) throws MessagingException{
        return mailServer.send_email(to);
    }

    // 检验验证码
    @PostMapping("/register")
    public Result register(@RequestBody Map<String,String> map){
        String user_email = map.get("user_email");
        String user_password = map.get("user_password");
        String code = map.get("code");

        return mailServer.register(user_email,user_password,code);
    }

    // 登录
    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO){
        String token = null;
        try {
            token = loginServer.login(loginDTO);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if ("用户不存在".equals(msg)) {
                log.warn("登录失败，用户不存在: {}", loginDTO.getUser_email());
                return Result.error(400, "用户名或密码错误", null);
            } else if ("密码错误".equals(msg)) {
                log.warn("登录失败，密码错误: {}", loginDTO.getUser_email());
                return Result.error(400, "用户名或密码错误", null);
            }
            log.error("登录异常", e);
            return Result.error(500, "服务器内部错误", null);
        }
        return Result.success("登录成功",token);
    }
}
