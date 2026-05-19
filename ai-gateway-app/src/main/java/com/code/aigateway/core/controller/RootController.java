package com.code.aigateway.core.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 根路径控制器
 *
 * <p>处理 GET / 请求，返回服务状态信息。
 * 用于 Claude Code SDK 等客户端的连通性探测。</p>
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Mono<ResponseEntity<Map<String, String>>> root() {
        return Mono.just(ResponseEntity.ok(Map.of("service", "AI-Gateway", "status", "running")));
    }
}
