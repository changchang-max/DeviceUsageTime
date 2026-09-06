package top.primordialcode.backend.service.Deregister;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.HistoryDataMapper;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Deregister.impl.DeregisterServerImpl;
import top.primordialcode.backend.service.Redis.RedisStringServer;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.utils.JwtUtil;

import java.time.Duration;

/**
 * 注销账号服务
 * 二次校验登录密码通过后：
 * 1. 在同一个事务中永久删除用户记录及其全部关联数据(app_window_titles / app_usage_records / daily_statistics / data_date_index / user_auth)
 * 2. 清理该用户在Redis中的实时热数据(尽力而为，失败仅记录日志)
 * 3. 将当前Token加入Redis黑名单使其立即失效
 */
@Slf4j
@Service
public class DeregisterServer implements DeregisterServerImpl {

    @Autowired
    UserAuthMapper userAuthMapper;
    @Autowired
    HistoryDataMapper historyDataMapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    RedisStringServer redisStringServer;
    @Autowired
    JwtTokenUtil jwtTokenUtil;

    /**
     * 注销当前账号(操作不可逆)
     *
     * @param token         请求头中的 Authorization 值(形如 Bearer xxx)
     * @param user_password 用户输入的登录密码(二次确认)
     * @return 操作结果。成功返回"账号已注销"；密码错误/用户不存在返回400；数据库删除失败时抛异常回滚并由全局异常处理器返回500
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deregister(String token, String user_password) {
        String jwtToken = "";
        // 1. 校验请求头中的Token
        try {
            jwtToken = jwtTokenUtil.getToken(token);
        }catch (RuntimeException e){
            if (e.getMessage().equals("Authorization请求头不存在")){
                return Result.error(400,"Authorization请求头不存在",null);
            } else if (e.getMessage().equals("Authorization格式错误")) {
                return Result.error(401,"Authorization格式错误",null);
            } else if (e.getMessage().equals("Token为空")) {
                return Result.error(401,"Token为空",null);
            }
        }
        if (jwtToken.isEmpty()){
            return Result.error(500,"服务器内部错误",null);
        }

        // 2. 校验二次确认密码非空
        if (user_password == null || user_password.isBlank()) {
            return Result.error(400, "请输入登录密码", null);
        }

        // 3. 从Token解析当前用户邮箱(请求已通过JwtAuthenticationFilter认证，此处仅防御性解析)
        String user_email;
        try {
            user_email = jwtUtil.parse(jwtToken);
        } catch (Exception e) {
            log.warn("注销账号失败，Token解析异常: {}", e.getMessage());
            return Result.error(401, "Token无效或已过期", null);
        }

        // 4. 校验用户存在，并核对登录密码(二次确认)
        UserAuthEntity user = userAuthMapper.selectUserInfo(user_email);
        if (user == null) {
            log.warn("注销账号失败，用户不存在: {}", user_email);
            return Result.error(400, "用户不存在", null);
        }
        if (!passwordEncoder.matches(user_password, user.getUser_password())) {
            log.warn("注销账号失败，密码错误: {}", user_email);
            return Result.error(400, "密码错误", null);
        }

        // 5. 永久删除用户记录及全部关联数据(同一事务，任一步失败整体回滚)
        // 先删除引用app_usage_records的app_window_titles，再删各业务表，最后删user_auth
        int titleRows = historyDataMapper.deleteWindowTitlesByUserEmail(user_email);
        int recordRows = historyDataMapper.deleteAppRecordsByUserEmail(user_email);
        int statRows = historyDataMapper.deleteDailyStatisticsByUserEmail(user_email);
        int indexRows = historyDataMapper.deleteDateIndexesByUserEmail(user_email);
        int userRows = userAuthMapper.deleteByEmail(user_email);
        log.info("账号已注销，删除用户及其关联数据: user_email={}, user_auth={}, app_window_titles={}, " +
                        "app_usage_records={}, daily_statistics={}, data_date_index={}",
                user_email, userRows, titleRows, recordRows, statRows, indexRows);

        // 6. 清理该用户在Redis中的实时热数据(尽力而为，失败不阻塞注销流程)
        try {
            redisStringServer.deleteKeysByPattern("uploadDataDevice:*:" + user_email);
        } catch (Exception e) {
            log.warn("注销账号时清理Redis实时数据失败(忽略): user_email={}, error={}", user_email, e.getMessage());
        }

        // 7. 将当前Token加入Redis黑名单，使其立即失效
        try {
            long expiration = jwtUtil.getExpiration(jwtToken);
            long ttl = expiration - System.currentTimeMillis();
            if (ttl > 0) {
                redisStringServer.set(
                        "jwt:blacklist:" + jwtToken, "deregister", Duration.ofMillis(ttl));
            }
        } catch (Exception e) {
            log.warn("注销账号时拉黑当前Token失败(忽略): user_email={}, error={}", user_email, e.getMessage());
        }

        return Result.success("账号已注销");
    }
}
