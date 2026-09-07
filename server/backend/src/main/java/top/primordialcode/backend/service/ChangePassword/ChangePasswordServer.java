package top.primordialcode.backend.service.ChangePassword;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.ChangePasswordDTO;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.ChangePassword.impl.ChangePasswordServerImpl;
import top.primordialcode.backend.service.Redis.RedisStringServer;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.utils.JwtUtil;
import top.primordialcode.backend.utils.PasswordValidateUtil;

import java.time.Duration;

/**
 * 修改密码服务
 * 1. 已登录场景：从Authorization请求头中的Token解析用户身份，请求体中的user_email被忽略
 * 2. 未登录场景：必须通过请求体传入user_email
 * 3. 校验原密码、新密码格式后更新数据库
 * 4. 修改成功后，将当前Token加入Redis黑名单使其立即失效(仅已登录场景)
 */
@Slf4j
@Service
public class ChangePasswordServer implements ChangePasswordServerImpl {

    @Autowired
    UserAuthMapper userAuthMapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    JwtTokenUtil jwtTokenUtil;
    @Autowired
    RedisStringServer redisStringServer;

    /**
     * 修改账号密码
     *
     * @param token             Authorization请求头值(形如 Bearer xxx)，未登录时可为null
     * @param changePasswordDTO 修改密码请求参数
     * @return 操作结果。成功返回"密码修改成功"；原密码错误/格式不合规/用户不存在等返回400
     */
    @Override
    public Result changePassword(String token, ChangePasswordDTO changePasswordDTO) {
        // 0. 请求体校验
        if (changePasswordDTO == null) {
            return Result.error(400, "请求参数不能为空", null);
        }

        // 1. 已登录场景：尝试从Token中解析用户身份
        // 仅ROLE_USER角色的登录Token可视为"已登录"；ROLE_VISITOR等其他Token不解析身份，走下方未登录流程
        String jwtToken = null;
        String user_email = null;
        if (token != null && !token.isBlank()) {
            try {
                jwtToken = jwtTokenUtil.getToken(token);
                // 只认登录用户(ROLE_USER)的Token(请求已通过JwtAuthenticationFilter，此处仅做角色判断)
                if (!"ROLE_USER".equals(jwtUtil.getRole(jwtToken))) {
                    jwtToken = null;
                } else {
                    user_email = jwtUtil.parse(jwtToken);
                }
            } catch (RuntimeException e) {
                log.warn("修改密码失败，Token无效: {}", e.getMessage());
                return Result.error(401, "Token无效或已过期", null);
            }
        }

        // 2. 未登录场景：必须通过请求体指定user_email
        if (user_email == null) {
            user_email = changePasswordDTO.getUser_email();
            if (user_email == null || user_email.isBlank()) {
                return Result.error(400, "未登录时user_email不能为空", null);
            }
        }
        // 说明：已登录状态下即使请求体传入了user_email，也以上方Token解析出的身份为准，此处user_email字段被忽略

        // 3. 校验原密码、新密码
        String old_password = changePasswordDTO.getOld_password();
        String new_password = changePasswordDTO.getNew_password();
        if (old_password == null || old_password.isBlank()) {
            return Result.error(400, "请输入原密码", null);
        }
        if (!PasswordValidateUtil.isValid(new_password)) {
            return Result.error(400, "新密码格式不合规(至少8位，且须同时包含字母和数字)", null);
        }

        // 4. 校验用户存在并核对原密码
        UserAuthEntity user = userAuthMapper.selectUserInfo(user_email);
        if (user == null) {
            log.warn("修改密码失败，用户不存在: {}", user_email);
            return Result.error(400, "用户不存在", null);
        }
        if (!passwordEncoder.matches(old_password, user.getUser_password())) {
            log.warn("修改密码失败，原密码错误: {}", user_email);
            return Result.error(400, "原密码错误", null);
        }

        // 5. 加密新密码并更新数据库
        String encodePassword = passwordEncoder.encode(new_password);
        try {
            int rows = userAuthMapper.updatePassword(user_email, encodePassword);
            if (rows != 1) {
                log.error("修改密码失败，数据库更新行数异常: user_email={}, rows={}", user_email, rows);
                return Result.error(500, "密码修改失败", null);
            }
            log.info("密码修改成功: {}", user_email);
        } catch (Exception e) {
            log.error("修改密码失败，数据库操作异常: user_email={}", user_email, e);
            return Result.error(500, "密码修改失败", null);
        }

        // 6. 已登录场景：将当前Token加入Redis黑名单使其立即失效(尽力而为，失败仅记录日志)
        if (jwtToken != null) {
            try {
                long expiration = jwtUtil.getExpiration(jwtToken);
                long ttl = expiration - System.currentTimeMillis();
                if (ttl > 0) {
                    redisStringServer.set(
                            "jwt:blacklist:" + jwtToken, "change-password", Duration.ofMillis(ttl));
                }
            } catch (Exception e) {
                log.warn("修改密码后拉黑当前Token失败(忽略): user_email={}, error={}", user_email, e.getMessage());
            }
        }

        return Result.success("密码修改成功");
    }
}
