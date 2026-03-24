package com.code.aigateway.core.router;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于配置的模型路由器
 * <p>
 * 根据 application.yml 中的配置进行模型路由：
 * <ol>
 *   <li>从 modelAliases 中查找模型别名映射</li>
 *   <li>根据别名中的 provider 找到对应的提供商配置</li>
 *   <li>验证提供商是否启用</li>
 *   <li>返回路由结果（提供商类型、实际模型名、API地址）</li>
 * </ol>
 * </p>
 *
 * @author sst
 */
@Component
@RequiredArgsConstructor
public class ConfigBasedModelRouter implements ModelRouter {

    /** 网关配置属性 */
    private final GatewayProperties gatewayProperties;

    /**
     * 执行模型路由
     * <p>
     * 根据请求中的模型名称，从配置中查找对应的提供商和实际模型。
     * 路由过程中会进行以下验证：
     * <ul>
     *   <li>模型名称不能为空</li>
     *   <li>模型别名必须存在</li>
     *   <li>别名中的 provider 和 model 必须合法</li>
     *   <li>提供商配置必须存在</li>
     *   <li>提供商必须处于启用状态</li>
     * </ul>
     * </p>
     *
     * @param request 统一的请求模型
     * @return 路由结果
     * @throws GatewayException 当路由验证失败时抛出
     */
    @Override
    public RouteResult route(UnifiedRequest request) {
        // 1. 验证模型名称不为空
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "model is required");
        }

        // 2. 查找模型别名配置
        GatewayProperties.ModelAliasProperties alias =
                gatewayProperties.getModelAliases() == null ? null : gatewayProperties.getModelAliases().get(request.getModel());

        // 3. 验证别名存在
        if (alias == null) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND,
                    "model alias not found: " + request.getModel());
        }
        if (alias.getProvider() == null || alias.getProvider().isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "provider alias is blank for model: " + request.getModel());
        }
        if (alias.getModel() == null || alias.getModel().isBlank()) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND,
                    "target model is blank for alias: " + request.getModel());
        }

        ProviderType providerType = resolveProviderType(alias.getProvider());

        // 4. 查找提供商配置
        GatewayProperties.ProviderProperties providerProperties =
                gatewayProperties.getProviders() == null ? null : gatewayProperties.getProviders().get(alias.getProvider());

        // 5. 验证提供商配置存在
        if (providerProperties == null) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "provider config not found: " + alias.getProvider());
        }

        // 6. 验证提供商已启用
        if (!providerProperties.isEnabled()) {
            throw new GatewayException(ErrorCode.PROVIDER_DISABLED,
                    "provider is disabled: " + alias.getProvider());
        }

        // 7. 构建并返回路由结果
        return RouteResult.builder()
                .providerType(providerType)
                .providerName(alias.getProvider())
                .targetModel(alias.getModel())
                .providerBaseUrl(providerProperties.getBaseUrl())
                .providerVersion(providerProperties.getVersion())
                .providerTimeoutSeconds(providerProperties.getTimeoutSeconds())
                .build();
    }

    /**
     * 解析 provider 类型，并把底层枚举异常转换为网关异常，
     * 避免把配置错误暴露成通用 500。
     *
     * @param providerName provider 名称
     * @return provider 类型
     */
    private ProviderType resolveProviderType(String providerName) {
        try {
            return ProviderType.from(providerName);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "unsupported provider type: " + providerName);
        }
    }
}
