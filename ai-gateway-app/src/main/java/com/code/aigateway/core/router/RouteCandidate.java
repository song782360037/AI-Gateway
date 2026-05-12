package com.code.aigateway.core.router;

import com.code.aigateway.sdk.model.ProtocolType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * 路由候选规则
 *
 * <p>从 model_redirect_config 与 provider_config 聚合而来，
 * 用于路由热路径快速选择可用候选目标。</p>
 */
@Value
@Builder
public class RouteCandidate {

    /** 目标提供商类型，例如 OPENAI */
    String providerType;

    /** 目标提供商编码，例如 openai-main */
    String providerCode;

    /** 目标模型名称 */
    String targetModel;

    /** 提供商 API 基础地址 */
    String providerBaseUrl;

    /** 提供商 API Key，运行时使用的是解密后的明文；序列化时排除以防止泄露到 Redis/日志 */
    @JsonIgnore
    String providerApiKey;

    /** 提供商超时时间，单位秒 */
    Integer providerTimeoutSeconds;

    /** 提供商配置优先级 */
    Integer providerPriority;

    /** 提供商支持的下游协议列表，空表示支持所有 */
    List<String> supportedProtocols;

    /** 提供商级别自定义请求头（已与全局头合并，提供商级别覆盖全局同名头） */
    Map<String, String> customHeaders;

    Boolean supportsVision;
    Boolean supportsTools;
    Boolean supportsToolChoiceRequired;
    Boolean supportsReasoning;
    Boolean supportsJson;
    Boolean supportsStream;
    Integer maxInputTokens;
    Integer maxOutputTokens;
    Integer qualityScore;
    Integer latencyScore;
    Integer costScore;
    Integer toolScore;
    Integer visionScore;
    Integer reasoningScore;
    Integer reliabilityScore;
    Integer scoreBias;
    Integer weight;

    /**
     * 判断本候选是否支持指定请求协议。
     * <p>supportedProtocols 为空时表示支持所有协议。</p>
     */
    public boolean supportsProtocol(String requestProtocol) {
        if (requestProtocol == null) {
            return true;
        }
        if (supportedProtocols == null || supportedProtocols.isEmpty()) {
            return true;
        }
        String normalized = ProtocolType.normalize(requestProtocol);
        return supportedProtocols.stream()
                .anyMatch(s -> ProtocolType.normalize(s).equals(normalized));
    }
}
