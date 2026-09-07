package top.primordialcode.backend.controller.api.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.ChangePasswordDTO;
import top.primordialcode.backend.service.ChangePassword.ChangePasswordServer;

@RestController
@RequestMapping("/auth")
public class ChangePasswordController {

    private final ChangePasswordServer changePasswordServer;

    public ChangePasswordController(ChangePasswordServer changePasswordServer) {
        this.changePasswordServer = changePasswordServer;
    }

    /**
     * 修改账号密码
     * 已登录时从JWT Token中自动解析用户身份(请求体中的user_email被忽略)；
     * 未登录时需在请求体中传入user_email。
     * 密码修改成功后，当前Token立即失效，用户需重新登录。
     *
     * @param token Authorization请求头(Bearer xxx)，未登录时可不传
     * @param changePasswordDTO 请求体(user_email未登录时必填；old_password/new_password必填)
     * @return 修改结果
     */
    @PostMapping("/change-password")
    public Result changePassword(@RequestHeader(value = "Authorization", required = false) String token,
                                 @RequestBody(required = false) ChangePasswordDTO changePasswordDTO) {
        return changePasswordServer.changePassword(token, changePasswordDTO);
    }
}
