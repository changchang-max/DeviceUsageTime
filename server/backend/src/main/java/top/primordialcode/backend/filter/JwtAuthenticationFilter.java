package top.primordialcode.backend.filter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.primordialcode.backend.service.Redis.RedisStringServer;
import top.primordialcode.backend.utils.JwtRevocationUtil;
import top.primordialcode.backend.utils.JwtUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * 每个请求执行一次，因此日志必须克制：
 * 只在 debug 级别输出认证结果，避免日志文件被逐请求的三条 info 撑爆。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    /** 用 Redis 做 token 黑名单 */
    @Autowired
    RedisStringServer redisStringServer;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 解析并校验 Authorization 头中的 JWT，通过则写入 SecurityContext
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7);

            // 黑名单命中(已退出登录)直接 401
            if (redisStringServer.existKey("jwt:blacklist:" + token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            try {
                String email = jwtUtil.parse(token);
                String role = jwtUtil.getRole(token);

                // 凭证吊销校验：密码重置/强制下线后，该时间点之前签发的Token一律拒绝
                if (isRevoked(token, email)) {
                    log.debug("JWT已被吊销: subject={}", email);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = (role != null && !role.isEmpty())
                        ? Collections.singletonList(new SimpleGrantedAuthority(role))
                        : Collections.emptyList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT认证通过: subject={}, authorities={}",
                        authentication.getPrincipal(), authentication.getAuthorities());
            } catch (ExpiredJwtException e) {
                log.debug("JWT token已过期: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (JwtException e) {
                log.debug("JWT token验证失败: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (Exception e) {
                log.error("JWT鉴权未知异常", e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断Token是否已被吊销
     *
     * 两种标记任一命中即视为失效：
     * 1. epoch:jwt:blacklist:[邮箱] → 该账号的全局失效时间点(比签发时间更早则吊销)
     * 2. epoch:jwt:blacklist:[签发时间]:[邮箱] → 历史签发时间戳黑名单(兼容旧版本实例)
     *
     * Redis 异常时放行(认证链路的降级策略)，只在日志中告警。
     *
     * @param token 原始Token
     * @param email Token所属用户邮箱
     * @return true：已被吊销
     */
    private boolean isRevoked(String token, String email) {
        try {
            // 单Token黑名单(退出登录等场景)
            if (redisStringServer.existKey("jwt:blacklist:" + token)) {
                return true;
            }
            if (email == null || email.isBlank()) {
                return false;
            }
            long issuedAt = jwtUtil.getIssuedAt(token);

            // 账号级全局失效时间点
            String epoch = redisStringServer.get(JwtRevocationUtil.EPOCH_BLACKLIST_PREFIX + email);
            if (epoch != null && !epoch.isBlank()) {
                try {
                    if (issuedAt <= Long.parseLong(epoch)) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.warn("全局失效时间点格式异常: subject={}", email);
                }
            }

            // 历史签发时间戳黑名单
            return redisStringServer.existKey(
                    JwtRevocationUtil.EPOCH_BLACKLIST_PREFIX + issuedAt + ":" + email);
        } catch (Exception e) {
            log.warn("凭证吊销校验异常，本次放行: error={}", e.getMessage());
            return false;
        }
    }
}