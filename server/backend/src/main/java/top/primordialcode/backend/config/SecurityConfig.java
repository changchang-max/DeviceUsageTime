package top.primordialcode.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import top.primordialcode.backend.filter.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
// 排除 Spring Security 默认的用户自动配置，避免创建 InMemoryUserDetailsManager
// 这是因为我们使用 JWT 无状态认证，不需要默认的内存用户
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    //编写构造方法，让Spring识别到需要JwtAuthenticationFilter对象时自动注入进来
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ){
        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;

    }

    /*这是一个Spring Security Filter Chain（Spring Security 过滤器链）
    * 它通过 Servlet Filter 机制，在请求到达 Controller 之前拦截 HTTP 请求，然后判断请求是否允许继续执行。*/
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        //关闭csrf
        http.csrf(AbstractHttpConfigurer::disable)
                //JWT是无状态认证，不需要Session保存用户。关闭session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                //配置接口权限
                .authorizeHttpRequests(
                        auth -> auth
                                // ========== 公开接口(无需认证) ==========
                                .requestMatchers(
                                        "/api/auth/login",
                                        "/api/auth/login/",
                                        "/api/auth/register",
                                        "/api/auth/register/",
                                        "/api/auth/sendCode",
                                        "/api/auth/sendCode/",
                                        "/api/auth/verify-key",
                                        "/api/auth/verify-key/",
                                        // 忘记密码：获取重置验证码(3分钟有效)与凭验证码重置密码，均无需登录
                                        "/api/auth/forgot-password/code",
                                        "/api/auth/forgot-password/code/",
                                        "/api/auth/forgot-password/reset",
                                        "/api/auth/forgot-password/reset/",
                                        // 修改密码接口同时支持已登录(Token认证)与未登录(请求体传user_email)两种场景，
                                        // 因此需放行匿名访问，是否携带Token由Controller/Service层自行判断
                                        "/api/auth/change-password",
                                        "/api/auth/change-password/",
                                        "/ws/**",
                                        // 数据查询接口支持秘钥认证(无token)，在Controller/Service层手动校验身份
                                        "/api/data/realtime",
                                        "/api/data/history",
                                        "/api/data/titles",
                                        "/api/data/dates",
                                        // 数据上传接口使用上传凭证认证(无token)，在Controller/Service层手动校验身份
                                        "/api/data/upload",
                                        "/api/data/upload/",
                                        // 健康检查：只暴露 health(不含敏感细节)
                                        "/actuator/health",
                                        "/actuator/health/**"
                                ).permitAll()

                                // ========== ROLE_USER角色专属接口 ==========
                                .requestMatchers(
                                        "/api/auth/logout",
                                        "/api/auth/logout/",
                                        "/api/auth/deregister",//注销用户
                                        "/api/auth/deregister/",
                                        "/index/home",
                                        "/index/home/",
                                        "/api/user/**"
                                ).hasAuthority("ROLE_USER")

                                // ========== ROLE_VISITOR角色专属接口 ==========
                                // 当前ROLE_VISITOR角色暂无专属接口
                                
                                // ========== 多角色共享接口 ==========
                                // 如果将来有需要ROLE_USER和ROLE_VISITOR都能访问的接口,在此配置
                                // 例如: .requestMatchers("/api/public/**").hasAnyAuthority("ROLE_USER", "ROLE_VISITOR")

                                // ========== 其他未匹配的接口 ==========
                                .anyRequest().denyAll()
                )
                // 未认证/无权限时统一返回 JSON，避免前端只拿到一个空的 403。
                // 前端 request.ts 对 401 会清 Token 并跳登录页，对 403 只弹"无权限访问"，
                // 一旦这里返回空体，用户就完全看不出失败原因。
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 匿名用户访问受保护接口：返回 401 + JSON
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"code\":401,\"message\":\"未授权,请先登录\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 只对接口路径返回 403 + JSON
                            String uri = request.getRequestURI();
                            if (uri != null && uri.startsWith("/api")) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write(
                                        "{\"code\":403,\"message\":\"无权限访问该接口\",\"data\":null}");
                                return;
                            }
                            // 非接口路径（如前端路由 /register）交还给容器默认处理，
                            // 避免把 SPA 页面请求也变成 403 JSON
                            response.sendError(HttpServletResponse.SC_NOT_FOUND);
                        })
                )


                //添加JWT过滤器
                .addFilterBefore(
                        // 让jwtAuthenticationFilter这个自定义过滤器在UsernamePasswordAuthenticationFilter之前执行
                        jwtAuthenticationFilter,
                        // 它主要用于处理传统的：用户名+密码的登录方式
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}