package com.code.aigateway.core.controller;

import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * OpenAI 兼容的模型列表接口
 *
 * <p>提供 GET /v1/models 端点，返回 OpenAI 格式的模型列表，
 * 供客户端（如 ChatGPT-Next-Web、OpenAI SDK 等）自动获取支持的模型。</p>
 *
 * <p>数据来源为运行时快照，确保与路由配置一致且无阻塞数据库访问。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class OpenAiModelsController {

    private final RoutingSnapshotHolder routingSnapshotHolder;

    @GetMapping("/models")
    public Mono<ResponseEntity<OpenAiModelsResponse>> listModels() {
        return Mono.fromCallable(() -> {
            RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
            List<OpenAiModelItem> items = snapshot != null
                    ? snapshot.getSupportedModels().stream()
                        .map(entry -> new OpenAiModelItem(
                                entry.modelId(),
                                "model",
                                entry.createdEpochSeconds(),
                                entry.ownedBy()))
                        .toList()
                    : Collections.emptyList();
            return OpenAiModelsResponse.of(items);
        }).map(ResponseEntity::ok);
    }

    /**
     * OpenAI /v1/models 响应格式
     */
    public record OpenAiModelsResponse(
            String object,
            List<OpenAiModelItem> data
    ) {
        public static OpenAiModelsResponse of(List<OpenAiModelItem> data) {
            return new OpenAiModelsResponse("list", data);
        }
    }

    /**
     * OpenAI /v1/models 单个模型条目
     */
    public record OpenAiModelItem(
            String id,
            String object,
            long created,
            String owned_by
    ) {}
}
