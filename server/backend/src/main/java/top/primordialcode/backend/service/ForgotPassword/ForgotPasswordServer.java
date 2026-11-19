package top.primordialcode.backend.service.ForgotPassword;

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
import top.primordialcode.backend.dto.ForgotPasswordDTO;
import top.primordialcode.backend.dto.SendResetCodeDTO;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.ForgotPassword.impl.ForgotPasswordServerImpl;
import top.primordialcode.backend.service.Redis.RedisStringServer;
import top.primordialcode.backend.service.Redis.RedisVerifyCodeServer;
import top.primordialcode.backend.utils.JwtRevocationUtil;
import top.primordialcode.backend.utils.MaskUtil;
import top.primordialcode.backend.utils.PasswordValidateUtil;
import top.primordialcode.backend.utils.RateLimiterUtil;
import top.primordialcode.backend.utils.VerifyCodeUtil;
import top.primordialcode.backend.vo.forgot.ForgotPasswordCodeVO;

import java.time.Duration;

/**
 * 忘记密码服务
 *
 * 流程：
 * 1. 用户提交邮箱 → 生成6位验证码写入Redis(键 forgot:password:code:[user_email]，存活3分钟) → 发送邮件
 * 2. 用户提交 邮箱 + 验证码 + 新密码 → 原子核验并删除验证码 → 新密码加密落库
 * 3. 密码重置成功后，吊销该账号的全部凭证：
 *    - 所有已签发的 JWT 按"签发时间戳"写入 epoch:jwt:blacklist:[签发时间] 黑名单(TTL = Token剩余有效期)
 *    - 作废上传凭证(user_auth.upload_credential 置空)，客户端需重新登录才能继续上传
 */
@Slf4j
@Service
public class ForgotPasswordServer implements ForgotPasswordServerImpl {

    /** 验证码在Redis中的键前缀 */
    private static final String CODE_KEY_PREFIX = "forgot:password:code:";

    /** 验证码有效期：3分钟 */
    private static final Duration CODE_TTL = Duration.ofMinutes(3);

    /** 同一邮箱重复获取验证码的最小间隔：60秒 */
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);

    /** 同一邮箱每日最多获取验证码次数 */
    private static final int SEND_DAILY_LIMIT = 10;

    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private RedisVerifyCodeServer redisVerifyCodeServer;
    @Autowired
    private RedisStringServer redisStringServer;
    @Autowired
    private RateLimiterUtil rateLimiterUtil;
    @Autowired
    private UserAuthMapper userAuthMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtRevocationUtil jwtRevocationUtil;

    @Value("${spring.mail.username}")
    private String from;

    /** JWT 有效期(毫秒)，与 jwt.expiration 配置一致，用于黑名单TTL */
    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMillis;

    /**
     * 发送重置密码验证码
     *
     * @param sendResetCodeDTO 请求参数(user_email必填)
     * @return 操作结果，成功时 data 为 ForgotPasswordCodeVO
     */
    @Override
    public Result sendResetCode(SendResetCodeDTO sendResetCodeDTO) {
        if (sendResetCodeDTO == null || sendResetCodeDTO.getUser_email() == null
                || sendResetCodeDTO.getUser_email().isBlank()) {
            return Result.error(400, "请输入邮箱", null);
        }
        String user_email = sendResetCodeDTO.getUser_email().trim();

        // 1. 邮箱必须已注册，避免把验证码发给无关邮箱(同时防止用本接口群发垃圾邮件)
        if (!userAuthMapper.existsByEmail(user_email)) {
            log.warn("发送重置密码验证码失败，用户不存在: {}", MaskUtil.maskEmail(user_email));
            return Result.error(400, "该邮箱未注册", null);
        }

        // 2. 限流：同一邮箱60秒内只允许获取一次，每日最多10次
        String intervalKey = "password:code:interval:" + user_email;
        if (redisStringServer.existKey(intervalKey)) {
            return Result.error(429, "验证码已发送，请稍后再试", null);
        }
        if (!rateLimiterUtil.tryAcquire("password-code-send", user_email, SEND_DAILY_LIMIT, Duration.ofDays(1))) {
            log.warn("发送重置密码验证码被限流: {}", MaskUtil.maskEmail(user_email));
            return Result.error(429, "今日获取验证码次数过多，请明天再试", null);
        }

        // 3. 生成验证码并写入Redis(3分钟有效，重复获取会覆盖旧验证码)
        String code = VerifyCodeUtil.generateCode();
        String codeKey = CODE_KEY_PREFIX + user_email;
        try {
            redisVerifyCodeServer.set(codeKey, code, CODE_TTL);
        } catch (Exception e) {
            log.error("重置密码验证码写入Redis失败: {}", MaskUtil.maskEmail(user_email), e);
            return Result.error(500, "验证码生成失败，请稍后重试", null);
        }
        redisStringServer.set(intervalKey, "1", SEND_INTERVAL);

        // 4. 发送邮件；发送失败则立即作废刚写入的验证码
        try {
            sendResetCodeMail(user_email, code);
        } catch (MessagingException | RuntimeException e) {
            redisVerifyCodeServer.del(codeKey);
            log.error("重置密码验证码邮件发送失败: {}", MaskUtil.maskEmail(user_email), e);
            return Result.error(500, "验证码发送失败，请稍后重试", null);
        }

        log.info("已发送重置密码验证码: user_email={}, 有效期={}秒",
                MaskUtil.maskEmail(user_email), CODE_TTL.toSeconds());
        return Result.success("验证码已发送，3分钟内有效",
                new ForgotPasswordCodeVO(user_email, CODE_TTL.toSeconds()));
    }

    /**
     * 校验验证码并重置密码
     *
     * @param forgotPasswordDTO 请求参数(user_email/code/new_password必填)
     * @return 操作结果
     */
    @Override
    public Result resetPassword(ForgotPasswordDTO forgotPasswordDTO) {
        if (forgotPasswordDTO == null) {
            return Result.error(400, "请求参数不能为空", null);
        }
        String user_email = forgotPasswordDTO.getUser_email() == null
                ? null : forgotPasswordDTO.getUser_email().trim();
        String code = forgotPasswordDTO.getCode() == null
                ? null : forgotPasswordDTO.getCode().trim();
        String new_password = forgotPasswordDTO.getNew_password();

        if (user_email == null || user_email.isBlank()) {
            return Result.error(400, "请输入邮箱", null);
        }
        if (code == null || code.isBlank()) {
            return Result.error(400, "请输入验证码", null);
        }
        if (!PasswordValidateUtil.isValid(new_password)) {
            return Result.error(400, "新密码格式不合规(至少8位，且须同时包含字母和数字)", null);
        }

        // 1. 原子核验并消费验证码：比对一致才删除，保证一次有效
        String codeKey = CODE_KEY_PREFIX + user_email;
        boolean codeMatched;
        try {
            codeMatched = redisVerifyCodeServer.verifyAndDelete(codeKey, code);
        } catch (Exception e) {
            log.error("重置密码时读取验证码失败: {}", MaskUtil.maskEmail(user_email), e);
            return Result.error(500, "服务暂时不可用，请稍后重试", null);
        }
        if (!codeMatched) {
            log.warn("密码重置失败，验证码错误或已过期: {}", MaskUtil.maskEmail(user_email));
            return Result.error(400, "验证码错误或已过期", null);
        }

        // 2. 核对用户存在后更新密码
        UserAuthEntity user = userAuthMapper.selectUserInfo(user_email);
        if (user == null) {
            log.warn("密码重置失败，用户不存在: {}", MaskUtil.maskEmail(user_email));
            return Result.error(400, "用户不存在", null);
        }
        try {
            int rows = userAuthMapper.updatePassword(user_email, passwordEncoder.encode(new_password));
            if (rows != 1) {
                log.error("密码重置失败，数据库更新行数异常: user_email={}, rows={}",
                        MaskUtil.maskEmail(user_email), rows);
                return Result.error(500, "密码重置失败", null);
            }
        } catch (Exception e) {
            log.error("密码重置失败，数据库操作异常: user_email={}", MaskUtil.maskEmail(user_email), e);
            return Result.error(500, "密码重置失败", null);
        }

        // 3. 吊销该账号全部凭证(尽力而为，失败不阻塞密码修改结果，但会记录告警)
        revokeAllCredentials(user_email);

        log.info("密码重置成功，账号全部凭证已失效: {}", MaskUtil.maskEmail(user_email));
        return Result.success("密码重置成功，请使用新密码重新登录");
    }

    /**
     * 验证码有效期(秒)
     *
     * @return 有效期秒数
     */
    @Override
    public long getVerifyCodeTtlSeconds() {
        return CODE_TTL.toSeconds();
    }

    /**
     * 吊销某个账号的全部凭证
     * 1. 记录全局失效时间点：该时间点之前签发的 JWT 在鉴权过滤器中被统一拒绝
     * 2. 按剩余有效期写入历史签发时间戳黑名单，兼容仅查询单Token黑名单的旧版本实例
     * 3. 作废上传凭证，客户端必须重新登录才能继续上传数据
     *
     * @param user_email 目标用户邮箱
     */
    private void revokeAllCredentials(String user_email) {
        try {
            jwtRevocationUtil.revokeAll(user_email, "reset-password", jwtExpirationMillis);
        } catch (Exception e) {
            log.warn("密码重置后吊销JWT失败(忽略): user_email={}, error={}",
                    MaskUtil.maskEmail(user_email), e.getMessage());
        }

        try {
            userAuthMapper.revokeUploadCredential(user_email);
        } catch (Exception e) {
            log.warn("密码重置后作废上传凭证失败(忽略): user_email={}, error={}",
                    MaskUtil.maskEmail(user_email), e.getMessage());
        }
    }

    /**
     * 发送重置密码验证码邮件
     *
     * @param to 收件邮箱
     * @param code 验证码
     * @throws MessagingException 邮件构造或发送失败
     */
    private void sendResetCodeMail(String to, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject("您的DeviceUsageTime密码重置验证码");
        helper.setText("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>密码重置验证码</title>
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

                    <div class="logo">🔑</div>

                    <h2>密码重置验证码</h2>

                    <p>
                        您正在重置 DeviceUsageTime 账号密码，请使用以下验证码完成操作。
                    </p>

                    <div class="code">
                """ + code + """
                    </div>

                    <div class="tip">
                        <strong>温馨提示</strong><br><br>
                        • 验证码有效期为 <b>3 分钟</b>，且仅可使用一次。<br>
                        • 重置成功后，该账号所有已登录设备将立即失效，需要重新登录。<br>
                        • 请勿将验证码泄露给任何人，若非本人操作请立即修改密码。
                    </div>

                    <div class="footer">
                        © 2026 DeviceUsageTime
                    </div>

                </div>

                </body>
                </html>
                """, true);

        mailSender.send(message);
    }
}