package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.model.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Rerank 协议适配器（App 层）
 * <p>
 * 委托 SDK 的 {@link com.code.aigateway.sdk.protocol.RerankProtocolAdapter}。
 * </p>
 */
@Component
public class RerankProtocolAdapter extends AbstractSdkProtocolAdapter {

    private final com.code.aigateway.sdk.protocol.RerankProtocolAdapter sdkAdapter;

    public RerankProtocolAdapter(ObjectMapper objectMapper,
                                  com.code.aigateway.sdk.protocol.RerankProtocolAdapter sdkAdapter) {
        super(objectMapper);
        this.sdkAdapter = sdkAdapter;
    }

    @Override
    protected com.code.aigateway.sdk.protocol.RerankProtocolAdapter sdkAdapter() {
        return sdkAdapter;
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.RERANK;
    }
}
