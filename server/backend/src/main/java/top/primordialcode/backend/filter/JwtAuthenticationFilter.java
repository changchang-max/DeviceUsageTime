package top.primordialcode.backend.filter;


import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.primordialcode.backend.service.Redis.RedisStringServer;
import top.primordialcode.backend.utils.JwtUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    // 此处是为了使用jwt相关功能
    private final JwtUtil jwtUtil;

    // 此处是为了使用redis相关功能，用Redis做token黑名单
    @Autowired
    RedisStringServer redisStringServer;

    // 编写构造方法，构造器注入。为了让Spring自动注入JwtUtil。可以替换为Autowired，但构造器注入的好处是final 保证依赖不会被修改
    public JwtAuthenticationFilter(
            JwtUtil jwtUtil
    ){
        this.jwtUtil = jwtUtil;

    }

    // 该方法的作用是实现授权功能
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 获取Authorization请求头，其值中包含Jwt的token
        String header = request.getHeader("Authorization");

        // 判断jwtToken的合法性
        if(header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder  //true:当前请求还没有建立认证身份，可以继续执行 JWT 认证。false:当前请求已经有认证信息了，不需要再次通过 JWT 认证。
                .getContext()
                .getAuthentication() == null) {

            String token = header.substring(7);

            // 判断是否在黑名单中
            boolean inBlackList = redisStringServer.existKey("jwt:blacklist:"+token);
            if (inBlackList){
                // 设置返回值为401
                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );
                //如果在黑名单中则直接返回。无需解析
                return;
            }


            try {
                //解析JWT
                String email = jwtUtil.parse(token);
                //创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,//标识用户是谁
                                null,//用户凭证，一般设为null

                                Collections.emptyList()//为该用户授予的角色。因为该文章的功能不需要分角色，所以传入了空的列表
                        );

                //保存用户信息
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

                // 日志记录当前状态（非必须添加）
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

                //设置返回值
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