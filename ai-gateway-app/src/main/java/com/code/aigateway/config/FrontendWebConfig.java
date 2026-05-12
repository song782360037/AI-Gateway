package com.code.aigateway.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

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

    /**
     * 将 /frontend-vue 与 /frontend-vue/ 直接映射到 index.html。
     *
     * <p>Spring WebFlux 的 ResourceHandler 默认不会将目录请求解析为 index.html，
     * 直接访问该路径会导致内部异常。通过 RouterFunction 优先匹配并返回 index.html，
     * 使 hash 路由的 Vue 应用能正常加载。</p>
     *
     * <p>同时将根路径 "/" 重定向到前端首页，方便直接通过 http://host:port 访问。</p>
     */
    @Bean
    public RouterFunction<ServerResponse> frontendIndexRouter() {
        ClassPathResource indexHtml = new ClassPathResource("static/frontend-vue/index.html");
        return RouterFunctions.route()
                .GET("/frontend-vue", request -> ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(indexHtml))
                .GET("/frontend-vue/", request -> ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(indexHtml))
                .GET("/", request -> ServerResponse.permanentRedirect(URI.create("/frontend-vue/")).build())
                .build();
    }
}
