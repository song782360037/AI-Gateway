package com.code.aigateway.core.router;

import lombok.Builder;
import lombok.Value;

import java.util.List;

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

    /** 提供商 API Key，运行时使用的是解密后的明文 */
    String providerApiKey;

    /** 提供商超时时间，单位秒 */
    Integer providerTimeoutSeconds;

    /** 提供商配置优先级（路由排序唯一依据） */
    Integer providerPriority;

    /** 提供商支持的下游协议列表，空表示支持所有 */
    List<String> supportedProtocols;
}
