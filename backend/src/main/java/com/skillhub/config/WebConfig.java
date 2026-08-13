package com.skillhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * 全局 CORS：生产前端经 nginx 同端口反代 /api，浏览器请求携带 Origin header，
 * Spring WebFlux 对无 CORS 配置的跨源请求默认返回 403 Invalid CORS request。
 * 统一放行任意源（匿名 API，无 cookie 凭据）。
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
