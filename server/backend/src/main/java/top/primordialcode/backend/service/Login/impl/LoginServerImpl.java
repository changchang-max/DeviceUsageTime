package top.primordialcode.backend.service.Login.impl;

import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.LoginDTO;

public interface LoginServerImpl {
    //处理登录请求，下发JWT令牌
    public String login(LoginDTO loginDTO);
    // 处理退出登录的请求
    public Result logout(String token);
}
