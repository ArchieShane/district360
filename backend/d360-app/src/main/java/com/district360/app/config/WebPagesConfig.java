package com.district360.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态页面入口。根路径的 index.html 由 Spring Boot 自动作为首页（v1 页面）；
 * V1.0 新前端（frontend-360）放在静态目录的 v2/ 下，Spring 不会自动把子目录映射到 index.html，这里补上：
 * {@code /v2} 重定向到 {@code /v2/}，{@code /v2/} 转发到 {@code /v2/index.html}（都在 context-path 之下，如 /district360/v2/）。
 */
@Configuration
public class WebPagesConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/v2", "/v2/");
        registry.addViewController("/v2/").setViewName("forward:/v2/index.html");
    }
}
