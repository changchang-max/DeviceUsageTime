package top.primordialcode.backend.utils;

import org.springframework.stereotype.Component;
import top.primordialcode.backend.common.Result;

@Component
public class JwtTokenUtil {

    private final JwtUtil jwtUtil;

    public JwtTokenUtil(JwtUtil jwtUtil){
        this.jwtUtil = jwtUtil;
    }

    /**
     * 根据原始Authorization值解析出token
     * @param Authorization 请求头参数Authorization的值
     * @return 解析成功返回token，失败时抛出异常
     */
    public String getSubject(String Authorization){

        if (Authorization == null) {
            throw new RuntimeException("Authorization请求头不存在");
        }

        if (!Authorization.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization格式错误");
        }

        if (Authorization.length() <= 7) {
            throw new RuntimeException("Token为空");
        }

        String token = Authorization.substring(7);

        return jwtUtil.parse(token);
    }

    public String getToken(String Authorization){

        if (Authorization == null) {
            throw new RuntimeException("Authorization请求头不存在");
        }

        if (!Authorization.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization格式错误");
        }

        if (Authorization.length() <= 7) {
            throw new RuntimeException("Token为空");
        }

        return Authorization.substring(7);
    }
}
