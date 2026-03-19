package com.code.aigateway;

import com.code.aigateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 启动类
 * @author sst
 * @date 2026/3/11 16:59
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
