package top.primordialcode.backend.service.Register;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.dto.UserRegisterDTO;
import top.primordialcode.backend.service.Register.impl.MailServerImpl;
import top.primordialcode.backend.utils.GenKeyUtil;

import java.time.Instant;

@Slf4j
@Service
public class MailServer implements MailServerImpl {
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private VerifyCodeServer verifyCodeServer;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserAuthMapper userAuthMapper;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public Result send_email(String to) throws MessagingException {
        // 非空检测
        if (to == null) {
            return Result.error(400,"邮件发送目标不能为空",null);
        }

        // 生成验证码并存到Redis
        String code = verifyCodeServer.savecode(to);

        //发送验证码
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,true);

        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject("您的DeviceUsageTime验证码");
        helper.setText("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>邮箱验证码</title>
                    <style>
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                            font-family: "Segoe UI", "Microsoft YaHei", sans-serif;
                        }
                
                        body {
                            background: linear-gradient(135deg, #4f8cff, #7c5cff);
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 100vh;
                        }
                
                        .card {
                            width: 420px;
                            background: #fff;
                            border-radius: 18px;
                            padding: 40px;
                            box-shadow: 0 20px 50px rgba(0,0,0,.15);
                            text-align: center;
                            display: flex;
                            flex-direction: column;
                        }
                
                        .logo {
                            width: 70px;
                            height: 70px;
                            margin: auto;
                            border-radius: 50%;
                            background: linear-gradient(135deg,#4f8cff,#7c5cff);
                            color: white;
                            font-size: 32px;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            margin-bottom: 20px;
                        }
                
                        h2 {
                            color: #333;
                            margin-bottom: 12px;
                        }
                
                        p {
                            color: #666;
                            line-height: 1.7;
                            font-size: 15px;
                        }
                
                        .code {
                            margin: 35px 0;
                            letter-spacing: 10px;
                            font-size: 42px;
                            font-weight: bold;
                            color: #4f8cff;
                            background: #f5f8ff;
                            border: 2px dashed #7ea9ff;
                            border-radius: 14px;
                            padding: 22px 0;
                            user-select: all;
                        }
                
                        .tip {
                            color: #888;
                            font-size: 14px;
                            line-height: 1.8;
                            text-align: left;
                            background: #fafafa;
                            border-radius: 10px;
                            padding: 18px;
                        }
                
                        .footer {
                            margin-top: 28px;
                            color: #aaa;
                            font-size: 13px;
                        }
                
                        .footer a {
                            color: #4f8cff;
                            text-decoration: none;
                        }
                
                        .footer a:hover {
                            text-decoration: underline;
                        }
                    </style>
                </head>
                <body>
                
                <div class="card">
                
                    <div class="logo">✉</div>
                
                    <h2>邮箱验证码</h2>
                
                    <p>
                        您正在进行邮箱验证，请使用以下验证码完成操作。
                    </p>
                
                    <div class="code">
                """ +code+"""
                    </div>
                
                    <div class="tip">
                        <strong>温馨提示</strong><br><br>
                        • 验证码有效期为 <b>5 分钟</b>。<br>
                        • 请勿将验证码泄露给任何人。<br>
                        • 若非本人操作，请忽略本次验证码。
                    </div>
                
                    <div class="footer">
                        © 2026 DeviceUsageTime
                    </div>
                
                </div>
                
                </body>
                </html>
                """,true);

        mailSender.send(message);

        return Result.success();
    }


    /**
     * 在数据库层面实现注册功能。将用户的email，加密后的密码和key写入MySQL数据库
     * @param user_email 用户邮箱
     * @param user_password 明文密码
     * @return 处理结果.注册成功时Data返回userRegister。注册失败时Data返回Exception
     */
    @Override
    public Result register(String user_email, String user_password,String user_code) {
        String sys_code = verifyCodeServer.selectcode(user_email);
        //非空判断
        if (sys_code == null) {
            return Result.error(400,"验证码不存在或已过期",null);
        } else if (!user_code.equals(sys_code)) {
            return Result.error(400,"验证码错误",null);
        }
        // 检验用户是否已注册
        if (verifyCodeServer.existsByEmail(user_email)){
            return Result.error(400,"用户已存在，无需重复注册",null);
        }


        // 对用户明文密码进行哈希加密
        String encodePassword = passwordEncoder.encode(user_password);
        // 生成32位的随机秘钥
        String user_key = GenKeyUtil.generateKey(32);

        // 获取当前时间
        Instant createdAt = Instant.now();

        // 创建实体对象,将数据存入数据库
        UserAuthEntity userAuthEntity = new UserAuthEntity();
        userAuthEntity.setUser_email(user_email);
        userAuthEntity.setUser_password(encodePassword);
        userAuthEntity.setUser_key(user_key);
        userAuthEntity.setCreated_at(createdAt);



        try {
            log.info("正在注册用户："+user_email);
            int line = userAuthMapper.insert(userAuthEntity);
            log.info("用户："+user_email+"注册成功！");

            // 注册成功后删除Redis的验证码
            verifyCodeServer.deletecode(sys_code);

            // 创建一个UserRegister实体，用于返回注册信息
            UserRegisterDTO userRegisterDTO = new UserRegisterDTO();
            userRegisterDTO.setEmail(user_email);
            userRegisterDTO.setUser_key(user_key);
            userRegisterDTO.setCreatedAt(Instant.now());

            return new Result(201,"注册成功", userRegisterDTO);
        }catch (Exception e){
            log.error("用户："+user_email+"注册失败");
            return new Result(500,"注册失败",null);
        }
    }

}
