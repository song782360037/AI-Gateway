package com.code.aigateway.sdk.registry;

import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.protocol.ProtocolAdapter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * 协议注册表
 * <p>
 * 管理所有已注册的 ProtocolAdapter 实例，支持按协议类型查找。
 * 使用 Builder 模式构建，线程安全。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * ProtocolRegistry registry = ProtocolRegistry.builder()
 *     .register(new OpenAiChatProtocolAdapter(objectMapper))
 *     .register(new AnthropicProtocolAdapter(objectMapper))
 *     .register(new GeminiProtocolAdapter(objectMapper))
 *     .register(new OpenAiResponsesProtocolAdapter(objectMapper))
 *     .build();
 *
 * ProtocolAdapter adapter = registry.getAdapter(ProtocolType.OPENAI_CHAT);
 * </pre>
 * </p>
 */
public class ProtocolRegistry {

    private final Map<ProtocolType, ProtocolAdapter> adapters;

    private ProtocolRegistry(Map<ProtocolType, ProtocolAdapter> adapters) {
        this.adapters = Collections.unmodifiableMap(new LinkedHashMap<>(adapters));
    }

    /**
     * 获取指定协议类型的适配器
     *
     * @param protocolType 协议类型
     * @return 协议适配器
     * @throws NoSuchElementException 协议未注册时抛出
     */
    public ProtocolAdapter getAdapter(ProtocolType protocolType) {
        Objects.requireNonNull(protocolType, "protocolType must not be null");
        ProtocolAdapter adapter = adapters.get(protocolType);
        if (adapter == null) {
            throw new NoSuchElementException("no adapter registered for protocol: " + protocolType);
        }
        return adapter;
    }

    /**
     * 检查指定协议是否已注册
     */
    public boolean hasAdapter(ProtocolType protocolType) {
        return adapters.containsKey(protocolType);
    }

    /**
     * 获取所有已注册的协议类型
     */
    public Set<ProtocolType> getRegisteredProtocols() {
        return adapters.keySet();
    }

    /**
     * 获取所有已注册的适配器
     */
    public Collection<ProtocolAdapter> getAllAdapters() {
        return adapters.values();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 注册表构建器
     */
    public static class Builder {
        private final Map<ProtocolType, ProtocolAdapter> adapters = new LinkedHashMap<>();

        /**
         * 注册一个协议适配器
         *
         * @param adapter 协议适配器
         * @return this
         */
        public Builder register(ProtocolAdapter adapter) {
            Objects.requireNonNull(adapter, "adapter must not be null");
            ProtocolType protocolType = adapter.getProtocolType();
            Objects.requireNonNull(protocolType, "adapter.getProtocolType() must not be null");
            if (adapters.containsKey(protocolType)) {
                throw new IllegalStateException("duplicate adapter for protocol: " + protocolType);
            }
            adapters.put(protocolType, adapter);
            return this;
        }

        /**
         * 注册多个协议适配器
         */
        public Builder registerAll(ProtocolAdapter... adapters) {
            for (ProtocolAdapter adapter : adapters) {
                register(adapter);
            }
            return this;
        }

        /**
         * 构建不可变的注册表
         */
        public ProtocolRegistry build() {
            return new ProtocolRegistry(adapters);
        }
    }
}
