package top.primordialcode.backend.service.Login;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.LoginDTO;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Login.impl.LoginServerImpl;
import top.primordialcode.backend.service.Redis.RedisStringServer;
import top.primordialcode.backend.utils.JwtUtil;

import java.time.Duration;

@Slf4j
@Service
public class LoginServer implements LoginServerImpl {
    @Autowired
    UserAuthMapper userAuthMapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    RedisStringServer redisStringServer;

    /**
     * 用户登录，检验密码是否正确，检验成功则返回token记住用户
     * @param loginDTO 用户登录时需要传入的数据，包含用户名和密码
     * @return 返回一个jwt的token，类型为String
     */
    @Override
    public String login(LoginDTO loginDTO) {
        //根据用户邮箱拿到用户实体类
        UserAuthEntity user = userAuthMapper.selectUserInfo(loginDTO.getUser_email());
        if(user == null){
            throw new RuntimeException("用户不存在");
        }

        if(!passwordEncoder.matches(loginDTO.getUser_password(), user.getUser_password())){
            //记录日志
            log.info(loginDTO.getUser_email()+"输入密码错误");
            throw new RuntimeException("密码错误");
        }

        // 密码正确时，允许登录，给浏览器返回一个带ROLE_USER角色的token
        return jwtUtil.generateWithRole(loginDTO.getUser_email(), "ROLE_USER");
    }

    /**
     * 根据用户token删除对应信息以此实现退出登录
     *
     * @return 状态信息
     */
    @Override
    public Result logout(String token) {
        if (token == null
                || !token.startsWith("Bearer ")
                || token.length() <= 7) {

            return Result.error(400, "用户未登录", null);
        }
        String jwtToken = token.substring(7);

        // 获取过期时间戳
        long expiration = jwtUtil.getExpiration(jwtToken);
        long ttl = expiration - System.currentTimeMillis();

        if (ttl>0){
            //将token添加至Redis黑名单中
            redisStringServer.set("jwt:blacklist:"+jwtToken,"logout", Duration.ofMillis(ttl));
        }

        return Result.success("退出登录成功");
    }
}
