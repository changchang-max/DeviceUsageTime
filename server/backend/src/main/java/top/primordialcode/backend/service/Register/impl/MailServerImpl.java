package top.primordialcode.backend.service.Register.impl;

import jakarta.mail.MessagingException;
import top.primordialcode.backend.common.Result;

public interface MailServerImpl {
    //发送验证码
    public Result send_email(String to) throws MessagingException;

    // 校验验证码，判断是否允许注册,并实现注册逻辑
    public Result register(String user_email,String user_password,String user_code);

}
