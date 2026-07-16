package top.primordialcode.backend.service.Register;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Register.impl.VerifyCodeServerImpl;
import top.primordialcode.backend.service.Redis.RedisStringServer;

import java.time.Duration;

import static top.primordialcode.backend.utils.VerifyCodeUtil.generateCode;

@Slf4j
@Service
public class VerifyCodeServer implements VerifyCodeServerImpl {
    @Autowired
    RedisStringServer redisStringServer;
    @Autowired
    private UserAuthMapper userAuthMapper;

    /**
     *
     * 生成验证码并将验证码设置TTL存活时长后保存到redis中
     * @param email 生成对应用户邮箱的专属验证码
     * @return 返回生成的验证码，仅用于发送邮件，不可用于验证身份
     */
    @Override
    public String savecode(String email) {
        // 生成验证码
        String code = generateCode();
        // 存储到Redis
        redisStringServer.set(
                "email:code:"+email,
                code,
                Duration.ofMinutes(5) //ttl为5,5分钟内有效
        );
        log.info("已生成用户:"+email+"的验证码:"+code);
        return code;
    }

    /**
     * 根据用户邮箱从redis中查询验证码
     * @param email 用户邮箱
     * @return 查询的验证码结果（结果可能为null)
     */
    @Override
    public String selectcode(String email) {
        return redisStringServer.get("email:code:"+email);
    }

    /**
     * 删除用户的验证码（一般用于验证成功时）
     * @param email 用户邮箱
     * @return 删除的成功与否
     */
    @Override
    public boolean deletecode(String email) {
        try {
            log.info("清除用户："+email+"的验证码");
            redisStringServer.del("email:code:"+email);
            return true;
        }catch (Exception e){
            log.error("清除用户验证码发生错误",e);
            return false;
        }
    }

    /**
     * 查询指定用户是否存在
     * @param email 用户邮箱
     * @return true:存在 flase:不存在
     */
    @Override
    public boolean existsByEmail(String email) {
        return userAuthMapper.existsByEmail(email);
    }
}
