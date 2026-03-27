package com.code.aigateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * 前端静态资源配置
 *
 * <p>将构建后的 Vue 管理台资源暴露为静态文件，避免运行时依赖进程工作目录。</p>
 */
@Configuration
public class FrontendWebConfig implements WebFluxConfigurer {

    /**
     * 注册静态资源映射。
     *
     * <p>/frontend-vue/** 映射到 classpath:/static/frontend-vue/ 下的文件。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/frontend-vue/**")
                .addResourceLocations("classpath:/static/frontend-vue/");
    }
}
