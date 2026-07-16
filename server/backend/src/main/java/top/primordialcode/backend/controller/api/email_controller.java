package top.primordialcode.backend.controller.api;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.service.Register.MailServer;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class email_controller {
    @Autowired
    private MailServer mailServer;

    @GetMapping("/test")
    public Result test(String to) throws MessagingException {

        mailServer.send_email(to);
        return Result.success();
    }

    // 生成并发送验证码
    @GetMapping("/sendcode")
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
}
