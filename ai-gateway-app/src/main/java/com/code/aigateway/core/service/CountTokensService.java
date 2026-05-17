package com.code.aigateway.core.service;

import com.code.aigateway.api.request.AnthropicMessagesRequest;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.protocol.AnthropicProtocolAdapter;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.token.TokenEstimator;
import com.code.aigateway.provider.AbstractProviderClient;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.code.aigateway.provider.ProviderType;
import com.code.aigateway.provider.anthropic.AnthropicProviderClient;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.UnifiedRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Token 计数编排服务
 * <p>
 * 根据上游 Provider 类型选择计数策略：
 * <ul>
 *   <li>Anthropic 上游：透传到原生 count_tokens 端点（精确）</li>
 *   <li>其他上游：本地 jtokkit 估算（近似）</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CountTokensService {

    private final ModelRouter modelRouter;
    private final ProviderClientFactory providerClientFactory;
    private final TokenEstimator tokenEstimator;

    /**
     * 计算请求的 input token 数
     *
     * @param request 原始 Anthropic 格式请求
     * @param adapter Anthropic 协议适配器
     * @return 包含 input_tokens 字段的响应 Map
     */
    public Mono<Map<String, Object>> countTokens(AnthropicMessagesRequest request, AnthropicProtocolAdapter adapter) {
        // 1. 协议适配器解析为统一请求
        UnifiedRequest unified = adapter.parse(request);

        // 2. 路由获取单个候选（不需要 failover）
        RouteResult route = modelRouter.route(unified);
        if (route == null) {
            return Mono.error(new GatewayException(ErrorCode.MODEL_NOT_FOUND,
                    "No available provider for model: " + unified.getModel()));
        }

        // 3. 注入运行时上下文（API Key、BaseUrl、CorrelationId 等）
        applyRouteContext(unified, route);

        // 4. 按上游类型分发
        if (route.getProviderType() == ProviderType.ANTHROPIC) {
            return countTokensViaAnthropic(unified);
        } else {
            return countTokensViaEstimation(unified, route.getTargetModel());
        }
    }

    /**
     * Anthropic 上游：透传到原生 count_tokens 端点，失败时降级到本地估算
     */
    private Mono<Map<String, Object>> countTokensViaAnthropic(UnifiedRequest unified) {
        ProviderClient client = providerClientFactory.getClient(ProviderType.ANTHROPIC);
        if (!(client instanceof AnthropicProviderClient anthropicClient)) {
            log.warn("[CountTokens] Provider 类型不匹配，回退到本地估算: {}", client.getClass().getSimpleName());
            return countTokensViaEstimation(unified, unified.getModel());
        }
        return anthropicClient.countTokens(unified)
                .map(count -> Map.<String, Object>of("input_tokens", count))
                .onErrorResume(error -> {
                    log.warn("[CountTokens] Anthropic 精确计数失败，降级到本地估算: {}", error.getMessage());
                    return countTokensViaEstimation(unified, unified.getModel());
                });
    }

    /**
     * 非 Anthropic 上游：本地 tokenizer 估算
     */
    private Mono<Map<String, Object>> countTokensViaEstimation(UnifiedRequest unified, String targetModel) {
        int estimated = tokenEstimator.estimate(unified, targetModel);
        return Mono.just(Map.<String, Object>of("input_tokens", estimated));
    }

    /**
     * 将路由结果注入 UnifiedRequest 的执行上下文
     * <p>复用 AbstractGatewayService.applyBasicRouteContext 构建基础上下文，避免隐性耦合。</p>
     */
    private void applyRouteContext(UnifiedRequest req, RouteResult route) {
        AbstractGatewayService.applyBasicRouteContext(req, route, UUID.randomUUID().toString());

        // 注入 thinking 兼容模式，供 Provider Client 的 isSimplifiedThinkingMode 读取
        if (route.getThinkingCompatMode() != null) {
            req.getMetadata().put(AbstractProviderClient.META_THINKING_COMPAT_MODE, route.getThinkingCompatMode());
        }
    }
}
