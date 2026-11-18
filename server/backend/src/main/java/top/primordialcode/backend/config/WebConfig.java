package top.primordialcode.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * 1. 路径前缀：controller.api 包统一加 /api，controller.index 包统一加 /index，
 *    因此各 Controller 的 @RequestMapping 里不要再重复写前缀；
 * 2. 跨域：本地开发时前端跑在 vite(3000)，后端跑在 8080，需要放行跨域；
 *    生产环境由 Nginx 同域反代，把 cors.allowed-origins 置空即可关闭。
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许的跨域来源，逗号分隔；默认只放行本地开发端口 */
    @Value("${cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    /**
     * 按包名自动追加路径前缀
     *
     * @param configurer 路径匹配配置器
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // controller.api 包自动添加 /api
        configurer.addPathPrefix(
                "/api",
                clazz -> clazz.getPackageName().startsWith("top.primordialcode.backend.controller.api")
        );
        // controller.index 包自动添加 /index
        configurer.addPathPrefix(
                "/index",
                clazz -> clazz.getPackageName().startsWith("top.primordialcode.backend.controller.index")
        );
    }

    /**
     * 注册 CORS 规则
     *
     * @param registry 跨域注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            // 生产环境不需要跨域(同域反代)
            return;
        }
        String[] origins = allowedOrigins.split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        log.info("CORS 已放行来源: {}", allowedOrigins);
    }
}