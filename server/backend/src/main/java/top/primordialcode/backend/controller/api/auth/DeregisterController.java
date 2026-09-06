package top.primordialcode.backend.controller.api.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.DeregisterDTO;
import top.primordialcode.backend.service.Deregister.DeregisterServer;

@RestController
@RequestMapping("/auth")
public class DeregisterController {

    private final DeregisterServer deregisterServer;

    public DeregisterController(DeregisterServer deregisterServer) {
        this.deregisterServer = deregisterServer;
    }

    /**
     * 注销账号：永久删除当前账号及其所有数据，操作不可逆。
     * 需要用户再次输入登录密码进行二次确认。
     * @param token Authorization请求头(Bearer xxx)
     * @param deregisterDTO 包含登录密码的请求体
     * @return 注销结果
     */
    @PostMapping("/deregister")
    public Result deregister(@RequestHeader("Authorization") String token,
                             @RequestBody DeregisterDTO deregisterDTO) {
        return deregisterServer.deregister(token, deregisterDTO.getUser_password());
    }
}
