package com.code.aigateway.provider;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提供商客户端工厂
 * <p>
 * 负责根据提供商类型获取对应的客户端实现。
 * Spring 会自动注入所有实现了 ProviderClient 接口的 Bean。
 * </p>
 *
 * @author sst
 */
@Component
@RequiredArgsConstructor
public class ProviderClientFactory {

    /**
     * 所有提供商客户端的列表，由 Spring 自动注入
     */
    private final List<ProviderClient> providerClients;

    /**
     * 根据提供商类型获取对应的客户端
     *
     * @param providerType 提供商类型
     * @return 对应的提供商客户端
     * @throws GatewayException 当找不到对应客户端时抛出异常
     */
    public ProviderClient getClient(ProviderType providerType) {
        return providerClients.stream()
                .filter(client -> client.getProviderType() == providerType)
                .findFirst()
                .orElseThrow(() -> new GatewayException(
                        ErrorCode.PROVIDER_NOT_FOUND,
                        "provider client not found: " + providerType
                ));
    }
}
