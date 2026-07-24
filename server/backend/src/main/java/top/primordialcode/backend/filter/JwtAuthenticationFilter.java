package top.primordialcode.backend.filter;


import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.primordialcode.backend.utils.JwtUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil
    ){
        this.jwtUtil = jwtUtil;

    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 获取Authorization请求头
        String header = request.getHeader("Authorization");

        if(header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder  //true:当前请求还没有建立认证身份，可以继续执行 JWT 认证。false:当前请求已经有认证信息了，不需要再次通过 JWT 认证。
                .getContext()
                .getAuthentication() == null) {

            String token = header.substring(7);
            try {
                //解析JWT
                String email = jwtUtil.parse(token);
                //创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,

                                Collections.emptyList()
                        );

                //保存用户信息
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);


                log.info(
                        "认证状态：" + authentication.isAuthenticated()
                );

                log.info(
                        "当前用户：" + authentication.getPrincipal()
                );

                log.info(
                        "当前权限：" + authentication.getAuthorities()
                );
            }
            catch(Exception e){
                log.error("Jwt鉴权报错：",e);

                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                return;

            }
        }


        // 继续执行请求
        // 把当前请求继续交给过滤器链中后面的过滤器处理。
        filterChain.doFilter(
                request,
                response
        );
    }

}