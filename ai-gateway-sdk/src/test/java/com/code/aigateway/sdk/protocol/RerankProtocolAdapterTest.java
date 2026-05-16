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
 * Rerank 协议适配器测试
 */
class RerankProtocolAdapterTest {

    private RerankProtocolAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new RerankProtocolAdapter(objectMapper);
    }

    // ===================== 基本属性 =====================

    @Test
    @DisplayName("getProtocolType 返回 RERANK")
    void getProtocolType() {
        assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.RERANK);
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
        @DisplayName("字符串文档列表基本解析")
        void parse_stringDocuments() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "What is AI?",
                    "documents", List.of("AI is technology", "AI is artificial")
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getModel()).isEqualTo("rerank-v3.5");
            assertThat(result.getRequestProtocol()).isEqualTo("rerank");
            assertThat(result.getResponseProtocol()).isEqualTo("rerank");
            assertThat(result.getStream()).isFalse();
            assertThat(result.getRerankQuery()).isEqualTo("What is AI?");
            assertThat(result.getRerankDocuments()).containsExactly("AI is technology", "AI is artificial");
        }

        @Test
        @DisplayName("对象格式文档解析（{ \"text\": \"...\" }）")
        void parse_objectDocuments() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test query",
                    "documents", List.of(
                            Map.of("text", "doc one"),
                            Map.of("text", "doc two")
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getRerankDocuments()).containsExactly("doc one", "doc two");
        }

        @Test
        @DisplayName("可选参数解析：top_n + return_documents")
        void parse_withOptionalParams() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test",
                    "documents", List.of("doc1", "doc2"),
                    "top_n", 5,
                    "return_documents", true
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getRerankTopN()).isEqualTo(5);
            assertThat(result.getRerankReturnDocuments()).isTrue();
        }

        @Test
        @DisplayName("缺少 model 抛出异常")
        void parse_missingModel() {
            Map<String, Object> request = Map.of(
                    "query", "test",
                    "documents", List.of("doc1")
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("model is required");
        }

        @Test
        @DisplayName("缺少 query 抛出异常")
        void parse_missingQuery() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "documents", List.of("doc1")
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("query is required");
        }

        @Test
        @DisplayName("空 query 抛出异常")
        void parse_blankQuery() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "  ",
                    "documents", List.of("doc1")
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("query is required");
        }

        @Test
        @DisplayName("缺少 documents 抛出异常")
        void parse_missingDocuments() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test"
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("documents is required");
        }

        @Test
        @DisplayName("空 documents 列表抛出异常")
        void parse_emptyDocuments() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test",
                    "documents", List.of()
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("documents must not be empty");
        }

        @Test
        @DisplayName("无效 top_n 类型抛出异常")
        void parse_invalidTopN() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test",
                    "documents", List.of("doc1"),
                    "top_n", "not-a-number"
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("top_n must be an integer");
        }

        @Test
        @DisplayName("无效 return_documents 类型抛出异常")
        void parse_invalidReturnDocuments() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test",
                    "documents", List.of("doc1"),
                    "return_documents", "yes"
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("return_documents must be a boolean");
        }

        @Test
        @DisplayName("documents 中含不支持类型的元素抛出异常")
        void parse_unsupportedDocumentType() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test",
                    "documents", List.of(123)
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("documents[0] must be a string or");
        }

        @Test
        @DisplayName("documents 中对象缺少 text 字段抛出异常")
        void parse_objectWithoutTextField() {
            Map<String, Object> request = Map.of(
                    "model", "rerank-v3.5",
                    "query", "test",
                    "documents", List.of(Map.of("content", "some text"))
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .hasMessageContaining("documents[0] object must have a 'text' field");
        }
    }

    // ===================== 响应编码 =====================

    @Nested
    @DisplayName("encodeResponse 响应编码")
    class EncodeResponseTests {

        @Test
        @DisplayName("完整 Rerank 响应编码")
        void encodeResponse_fullResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("rerank-123");
            response.setModel("rerank-v3.5");

            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(100);
            usage.setTotalTokens(100);
            response.setUsage(usage);

            UnifiedResponse.RerankResult r1 = new UnifiedResponse.RerankResult();
            r1.setIndex(0);
            r1.setRelevanceScore(0.95);
            r1.setDocument("relevant doc");

            UnifiedResponse.RerankResult r2 = new UnifiedResponse.RerankResult();
            r2.setIndex(1);
            r2.setRelevanceScore(0.3);

            response.setRerankResults(List.of(r1, r2));

            Object result = adapter.encodeResponse(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("id")).isEqualTo("rerank-123");
            assertThat(map.get("model")).isEqualTo("rerank-v3.5");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) map.get("results");
            assertThat(results).hasSize(2);
            assertThat(results.get(0).get("relevance_score")).isEqualTo(0.95);
            assertThat(results.get(0)).containsKey("document");
            assertThat(results.get(1).get("relevance_score")).isEqualTo(0.3);
            assertThat(results.get(1)).doesNotContainKey("document");
        }

        @Test
        @DisplayName("无 id 和无 rerankResults 的响应")
        void encodeResponse_minimalResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setModel("rerank-v3.5");

            Object result = adapter.encodeResponse(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map).doesNotContainKey("id");
            assertThat(map.get("model")).isEqualTo("rerank-v3.5");

            @SuppressWarnings("unchecked")
            List<?> results = (List<?>) map.get("results");
            assertThat(results).isEmpty();
        }
    }

    // ===================== 错误编码 =====================

    @Nested
    @DisplayName("buildError 错误编码")
    class BuildErrorTests {

        @Test
        @DisplayName("带 param 的错误")
        void buildError_withParam() {
            Object result = adapter.buildError("invalid query", "invalid_request_error", "INVALID", "query");

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) map.get("error");

            assertThat(error.get("message")).isEqualTo("invalid query");
            assertThat(error.get("param")).isEqualTo("query");
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
