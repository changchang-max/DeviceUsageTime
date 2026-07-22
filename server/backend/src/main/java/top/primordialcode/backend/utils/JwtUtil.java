package top.primordialcode.backend.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.primordialcode.backend.entity.UserAuthEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    // 设置秘钥
    @Value("${jwt.secret}")
    private String SECRET;

    @Value("${jwt.expiration}")
    private int EXPRIATION;

    // 转换秘钥类型
    private SecretKey SECRET_KEY;

    //使用此注解可以使SpringBoot在Bean创建完成后执行该方法
    @PostConstruct
    private void init(){
        SECRET_KEY = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 根据用户邮箱 ，生成唯一token
     * @param user_email 用户邮箱
     * @return 类型为String的token
     */
    public String generate(String user_email){

        return Jwts.builder()
                //设置主题,一般以主键作为主题，因为唯一
                .subject(user_email)

                //设置过期时间
                .expiration(new Date(
                        System.currentTimeMillis()+EXPRIATION))
                // 将Jwt签名。因为Jwt不是加密，而是签名
                .signWith(SECRET_KEY)
                // 编码生成最终数据
                .compact();
    }
}