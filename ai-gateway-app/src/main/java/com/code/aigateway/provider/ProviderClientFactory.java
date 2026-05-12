package com.code.aigateway.provider;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;

/**
 * 提供商客户端工厂
 * <p>
 * 启动时将所有 ProviderClient 实例构建为 EnumMap，支持 O(1) 查找，
 * 并在检测到重复 ProviderType 时快速失败。
 * </p>
 *
 * @author sst
 */
@Slf4j
@Component
public class ProviderClientFactory {

    private final EnumMap<ProviderType, ProviderClient> clientMap = new EnumMap<>(ProviderType.class);

    public ProviderClientFactory(List<ProviderClient> providerClients) {
        // init() 前暂存，init() 后不再持有列表引用
        for (ProviderClient client : providerClients) {
            ProviderType type = client.getProviderType();
            ProviderClient existing = clientMap.put(type, client);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate ProviderType detected: " + type
                                + ", existing: " + existing.getClass().getSimpleName()
                                + ", duplicate: " + client.getClass().getSimpleName());
            }
        }
    }

    /**
     * 启动日志：输出已注册的 Provider 客户端列表
     */
    @PostConstruct
    public void init() {
        log.info("[ProviderClientFactory] Registered {} provider clients: {}",
                clientMap.size(),
                clientMap.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue().getClass().getSimpleName())
                        .toList());
    }

    /**
     * 根据提供商类型获取对应的客户端（O(1) 查找）
     *
     * @param providerType 提供商类型
     * @return 对应的提供商客户端
     * @throws GatewayException 当找不到对应客户端时抛出异常
     */
    public ProviderClient getClient(ProviderType providerType) {
        ProviderClient client = clientMap.get(providerType);
        if (client == null) {
            throw new GatewayException(
                    ErrorCode.PROVIDER_NOT_FOUND,
                    "provider client not found: " + providerType
            );
        }
        return client;
    }
}
