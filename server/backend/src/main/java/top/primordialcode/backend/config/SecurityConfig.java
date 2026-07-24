package top.primordialcode.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import top.primordialcode.backend.filter.JwtAuthenticationFilter;

@Configuration
// 排除 Spring Security 默认的用户自动配置，避免创建 InMemoryUserDetailsManager
// 这是因为我们使用 JWT 无状态认证，不需要默认的内存用户
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ){
        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;

    }


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
                                //登录注册不需要JWT
                                //匹配相应的http请求
                                .requestMatchers(
                                        "/api/auth/login",
                                        "/api/auth/register",
                                        "/api/auth/sendCode"
                                )
                                // 允许所有人访问
                                .permitAll()

                                //其他接口需要认证

                                .anyRequest() // 前面没有匹配到的所有请求。
                                .authenticated() // 要求当前请求已经通过身份认证。
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