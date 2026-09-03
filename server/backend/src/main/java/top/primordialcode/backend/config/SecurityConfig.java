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
                                        "/api/auth/register",
                                        "/api/auth/sendCode",
                                        "/api/auth/verify-key",
                                        "/ws/**",
                                        // 数据查询接口支持秘钥认证(无token)，在Controller/Service层手动校验身份
                                        "/api/data/realtime",
                                        "/api/data/history",
                                        "/api/data/dates"
                                ).permitAll()

                                // ========== ROLE_USER角色专属接口 ==========
                                .requestMatchers(
                                        "/api/auth/logout",
                                        "/api/data/upload",
                                        "/index/home",
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