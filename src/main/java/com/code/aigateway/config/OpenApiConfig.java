package com.code.aigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 文档配置
 *
 * <p>配置 API 文档的基本信息、认证方式和分组。</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Gateway API")
                        .description("多协议 AI API 网关 — 支持 OpenAI、Anthropic、Gemini 协议")
                        .version("1.0.0"))
                // JWT Bearer Token 认证（管理端接口）
                .addSecurityItem(new SecurityRequirement().addList("Bearer JWT"))
                .addSecurityItem(new SecurityRequirement().addList("API Key"))
                .schemaRequirement("Bearer JWT", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("管理后台 JWT Token，登录 /admin/login 获取"))
                .schemaRequirement("API Key", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("网关 API Key，格式: Bearer ak-xxx"))
                .addServersItem(new Server().url("/").description("当前服务"));
    }
}
