package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * OpenAI Embeddings 协议适配器测试
 */
class OpenAiEmbeddingProtocolAdapterTest {

    private OpenAiEmbeddingProtocolAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new OpenAiEmbeddingProtocolAdapter(objectMapper);
    }

    // ===================== 基本属性 =====================

    @Test
    @DisplayName("getProtocolType 返回 OPENAI_EMBEDDING")
    void getProtocolType() {
        assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.OPENAI_EMBEDDING);
    }

    @Test
    @DisplayName("isSse 返回 false")
    void isSse_shouldReturnFalse() {
        assertThat(adapter.isSse()).isFalse();
    }

    @Test
    @DisplayName("encodeStreamEvent 返回空列表（非流式协议）")
    void encodeStreamEvent_shouldReturnEmpty() {
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text");
        assertThat(adapter.encodeStreamEvent(event, null)).isEmpty();
    }

    // ===================== 请求解析 =====================

    @Nested
    @DisplayName("parse 请求解析")
    class ParseTests {

        @Test
        @DisplayName("字符串输入基本解析")
        void parse_stringInput() {
            Map<String, Object> request = Map.of(
                    "model", "text-embedding-3-small",
                    "input", "Hello world"
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getModel()).isEqualTo("text-embedding-3-small");
            assertThat(result.getRequestProtocol()).isEqualTo("openai-embedding");
            assertThat(result.getResponseProtocol()).isEqualTo("openai-embedding");
            assertThat(result.getStream()).isFalse();
            assertThat(result.getEmbeddingInput()).isEqualTo("Hello world");
        }

        @Test
        @DisplayName("列表输入解析")
        void parse_listInput() {
            Map<String, Object> request = Map.of(
                    "model", "text-embedding-3-small",
                    "input", List.of("Hello", "World")
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getEmbeddingInput()).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> input = (List<String>) result.getEmbeddingInput();
            assertThat(input).containsExactly("Hello", "World");
        }

        @Test
        @DisplayName("可选参数解析：dimensions + encoding_format + user")
        void parse_withOptionalParams() {
            Map<String, Object> request = Map.of(
                    "model", "text-embedding-3-small",
                    "input", "test",
                    "dimensions", 512,
                    "encoding_format", "base64",
                    "user", "user-123"
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getEmbeddingDimensions()).isEqualTo(512);
            assertThat(result.getEmbeddingEncodingFormat()).isEqualTo("base64");
            assertThat(result.getMetadata()).containsEntry("user", "user-123");
        }

        @Test
        @DisplayName("缺少 model 抛出异常")
        void parse_missingModel() {
            Map<String, Object> request = Map.of("input", "Hello");

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("model is required");
        }

        @Test
        @DisplayName("缺少 input 抛出异常")
        void parse_missingInput() {
            Map<String, Object> request = Map.of("model", "text-embedding-3-small");

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("input is required");
        }

        @Test
        @DisplayName("input 类型无效（数字）抛出异常")
        void parse_invalidInputType() {
            Map<String, Object> request = Map.of(
                    "model", "text-embedding-3-small",
                    "input", 123
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("input must be a string or array");
        }

        @Test
        @DisplayName("dimensions 非数字抛出异常")
        void parse_invalidDimensions() {
            Map<String, Object> request = Map.of(
                    "model", "text-embedding-3-small",
                    "input", "test",
                    "dimensions", "not-a-number"
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("dimensions must be an integer");
        }

        @Test
        @DisplayName("encoding_format 非字符串抛出异常")
        void parse_invalidEncodingFormat() {
            Map<String, Object> request = Map.of(
                    "model", "text-embedding-3-small",
                    "input", "test",
                    "encoding_format", 123
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("encoding_format must be a string");
        }
    }

    // ===================== 响应编码 =====================

    @Nested
    @DisplayName("encodeResponse 响应编码")
    class EncodeResponseTests {

        @Test
        @DisplayName("完整 Embedding 响应编码")
        void encodeResponse_fullResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("emb-123");
            response.setModel("text-embedding-3-small");

            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(10);
            usage.setTotalTokens(10);
            response.setUsage(usage);

            UnifiedResponse.EmbeddingData data = new UnifiedResponse.EmbeddingData();
            data.setIndex(0);
            data.setEmbedding(new UnifiedResponse.EmbeddingValue.FloatArray(new double[]{0.1, 0.2, 0.3}));
            response.setEmbeddingData(List.of(data));

            Object result = adapter.encodeResponse(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("object")).isEqualTo("list");
            assertThat(map.get("id")).isEqualTo("emb-123");
            assertThat(map.get("model")).isEqualTo("text-embedding-3-small");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) map.get("data");
            assertThat(dataList).hasSize(1);
            assertThat(dataList.get(0).get("object")).isEqualTo("embedding");
            assertThat(dataList.get(0).get("index")).isEqualTo(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> usageMap = (Map<String, Object>) map.get("usage");
            assertThat(usageMap.get("prompt_tokens")).isEqualTo(10);
            assertThat(usageMap.get("total_tokens")).isEqualTo(10);
        }

        @Test
        @DisplayName("空 embeddingData 编码")
        void encodeResponse_emptyData() {
            UnifiedResponse response = new UnifiedResponse();
            response.setModel("text-embedding-3-small");

            Object result = adapter.encodeResponse(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("object")).isEqualTo("list");

            @SuppressWarnings("unchecked")
            List<?> dataList = (List<?>) map.get("data");
            assertThat(dataList).isEmpty();
        }
    }

    // ===================== 错误编码 =====================

    @Nested
    @DisplayName("buildError 错误编码")
    class BuildErrorTests {

        @Test
        @DisplayName("带 param 的错误")
        void buildError_withParam() {
            Object result = adapter.buildError("bad input", "invalid_request_error", "INVALID", "input");

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) map.get("error");

            assertThat(error.get("message")).isEqualTo("bad input");
            assertThat(error.get("param")).isEqualTo("input");
        }

        @Test
        @DisplayName("无 param 的错误")
        void buildError_withoutParam() {
            Object result = adapter.buildError("server error", "server_error", "500", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) map.get("error");

            assertThat(error).doesNotContainKey("param");
        }
    }

    @Test
    @DisplayName("mapErrorType 各错误码映射")
    void mapErrorType() {
        assertThat(adapter.mapErrorType(ErrorCode.INVALID_REQUEST)).isEqualTo("invalid_request_error");
        assertThat(adapter.mapErrorType(ErrorCode.AUTH_FAILED)).isEqualTo("authentication_error");
        assertThat(adapter.mapErrorType(ErrorCode.RATE_LIMITED)).isEqualTo("rate_limit_error");
        assertThat(adapter.mapErrorType(ErrorCode.PROVIDER_TIMEOUT)).isEqualTo("server_error");
        assertThat(adapter.mapErrorType(ErrorCode.INTERNAL_ERROR)).isEqualTo("server_error");
    }
}
