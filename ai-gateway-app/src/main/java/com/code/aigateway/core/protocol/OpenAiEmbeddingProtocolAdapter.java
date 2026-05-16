package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.model.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * OpenAI Embeddings 协议适配器（App 层）
 * <p>
 * 委托 SDK 的 {@link com.code.aigateway.sdk.protocol.OpenAiEmbeddingProtocolAdapter}。
 * </p>
 */
@Component
public class OpenAiEmbeddingProtocolAdapter extends AbstractSdkProtocolAdapter {

    private final com.code.aigateway.sdk.protocol.OpenAiEmbeddingProtocolAdapter sdkAdapter;

    public OpenAiEmbeddingProtocolAdapter(ObjectMapper objectMapper,
                                           com.code.aigateway.sdk.protocol.OpenAiEmbeddingProtocolAdapter sdkAdapter) {
        super(objectMapper);
        this.sdkAdapter = sdkAdapter;
    }

    @Override
    protected com.code.aigateway.sdk.protocol.OpenAiEmbeddingProtocolAdapter sdkAdapter() {
        return sdkAdapter;
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OPENAI_EMBEDDING;
    }
}
