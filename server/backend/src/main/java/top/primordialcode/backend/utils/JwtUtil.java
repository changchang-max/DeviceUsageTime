package top.primordialcode.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.primordialcode.backend.entity.UserAuthEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
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
        log.info("生成jwt，用户邮箱为："+user_email);
        String token = Jwts.builder()
                //设置主题,一般以主键作为主题，因为唯一
                .subject(user_email)

                //设置过期时间
                .expiration(new Date(
                        System.currentTimeMillis()+EXPRIATION))
                // 将Jwt签名。因为Jwt不是加密，而是签名
                .signWith(SECRET_KEY)
                // 编码生成最终数据
                .compact();
        System.out.println(token);
        return token;
    }

    /**
     * 验证JwtToken
     * @param token 前端保存的token
     * @return 根据token解析出来的用户邮箱(String)
     */
    public String parse(String token){
        // 使用 JWT 解析器解析传入的 token
        // token 一般是前端请求携带的 Authorization: Bearer xxx 中的 xxx 部分
        Claims claims =
                Jwts.parser()

                        // 设置 JWT 验证使用的密钥
                        // 解析 JWT 时，会使用这个 SECRET_KEY 对 token 的签名部分进行校验
                        // 如果签名不正确，说明 token 被篡改，会抛出异常
                        .verifyWith(SECRET_KEY)

                        // 构建 JWT 解析器对象
                        // 此时解析器已经知道如何验证 token 的签名
                        .build()

                        // 解析签名后的 JWT
                        // 会执行：
                        // 1. Base64 解码 Header
                        // 2. Base64 解码 Payload
                        // 3. 使用 SECRET_KEY 验证 Signature
                        // 4. 检查 token 是否过期
                        .parseSignedClaims(token)

                        // 获取 JWT 的 Payload 部分
                        // 返回的是 Claims 对象，本质上就是一个 Map
                        // 里面保存了 subject、expiration、自定义字段等信息
                        .getPayload();


        // 获取 Payload 中的 subject 字段
        // 你生成 JWT 时：
        // .subject(user.getUser_email())
        //
        // 所以这里获取的是用户邮箱
        // 例如：
        // {
        //    "sub": "12345678@qq.com",
        //    "exp": 1750000000
        // }
        //
        // 返回：
        // "12345678@qq.com"
        String subject = claims.getSubject();
        System.out.println("验证后得到的主题："+subject);
        return subject;
    }

    /**
     * 根据token得到token的过期时间
     * @param token jwtToken
     * @return 过期时间的毫秒时间戳。单位是毫秒
     */
    public long getExpiration(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .getTime();
    }
}