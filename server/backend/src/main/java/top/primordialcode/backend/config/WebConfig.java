package top.primordialcode.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public void configurePathMatch(PathMatchConfigurer configurer){
        // controller.api包自动添加/api
        configurer.addPathPrefix(
                "/api",
                global_api -> global_api.getPackageName().startsWith("top.primordialcode.backend.controller.api")
        );
    }
}
