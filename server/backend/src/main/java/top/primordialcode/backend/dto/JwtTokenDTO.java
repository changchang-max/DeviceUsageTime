package top.primordialcode.backend.dto;

import lombok.Data;

@Data
public class JwtTokenDTO {
    //jwtToken(不含前缀)
    String token;
    // jwt主题（用户邮箱）
    String subject;
    // 过期时间（以秒为单位）
    Long expiration;
}
