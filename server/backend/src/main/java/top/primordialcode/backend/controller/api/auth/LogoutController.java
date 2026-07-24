package top.primordialcode.backend.controller.api.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.service.Login.LoginServer;

@RestController
@RequestMapping("/auth")
public class LogoutController {
    private final LoginServer loginServer;

    public LogoutController(LoginServer loginServer) {
        this.loginServer = loginServer;
    }

    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization") String token){
        return loginServer.logout(token);
    }
}
