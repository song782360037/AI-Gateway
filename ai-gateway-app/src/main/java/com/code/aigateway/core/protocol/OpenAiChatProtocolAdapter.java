package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.model.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * OpenAI Chat Completions 协议适配器
 * <p>
 * 委托 SDK 的 {@link com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter}，
 * 通过 {@link AbstractSdkProtocolAdapter} 完成类型转换和 SSE 桥接。
 * </p>
 */
@Component
public class OpenAiChatProtocolAdapter extends AbstractSdkProtocolAdapter {

    private final com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter sdkAdapter;

    public OpenAiChatProtocolAdapter(ObjectMapper objectMapper,
                                      com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter sdkAdapter) {
        super(objectMapper);
        this.sdkAdapter = sdkAdapter;
    }

    @Override
    protected com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter sdkAdapter() {
        return sdkAdapter;
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OPENAI_CHAT;
    }
}
