package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.model.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Google Gemini API 协议适配器
 * <p>
 * Gemini 使用 JSON 数组流（非 SSE），isSse() 返回 false。
 * 流式响应通过 streamGenerateContent 端点返回 NDJSON（换行分隔的 JSON）。
 * 委托 SDK 的 {@link com.code.aigateway.sdk.protocol.GeminiProtocolAdapter}，
 * 通过 {@link AbstractSdkProtocolAdapter} 完成类型转换和 SSE 桥接。
 * </p>
 */
@Component
public class GeminiProtocolAdapter extends AbstractSdkProtocolAdapter {

    private final com.code.aigateway.sdk.protocol.GeminiProtocolAdapter sdkAdapter;

    public GeminiProtocolAdapter(ObjectMapper objectMapper,
                                  com.code.aigateway.sdk.protocol.GeminiProtocolAdapter sdkAdapter) {
        super(objectMapper);
        this.sdkAdapter = sdkAdapter;
    }

    @Override
    protected com.code.aigateway.sdk.protocol.GeminiProtocolAdapter sdkAdapter() {
        return sdkAdapter;
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.GEMINI;
    }
}
