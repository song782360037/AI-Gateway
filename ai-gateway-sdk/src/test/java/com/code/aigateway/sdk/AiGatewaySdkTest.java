package com.code.aigateway.sdk;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter;
import com.code.aigateway.sdk.protocol.GeminiProtocolAdapter;
import com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter;
import com.code.aigateway.sdk.protocol.OpenAiEmbeddingProtocolAdapter;
import com.code.aigateway.sdk.protocol.OpenAiResponsesProtocolAdapter;
import com.code.aigateway.sdk.protocol.ProtocolAdapter;
import com.code.aigateway.sdk.protocol.RerankProtocolAdapter;
import com.code.aigateway.sdk.registry.ProtocolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * AI Gateway SDK 门面测试
 */
class AiGatewaySdkTest {

    private ObjectMapper objectMapper;
    private AiGatewaySdk sdk;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sdk = new AiGatewaySdk(objectMapper);
    }
    
    // 辅助方法：创建 EmbeddingData
    private UnifiedResponse.EmbeddingData createEmbeddingData(int index, double[] values) {
        UnifiedResponse.EmbeddingData data = new UnifiedResponse.EmbeddingData();
        data.setIndex(index);
        data.setEmbedding(new UnifiedResponse.EmbeddingValue.FloatArray(values));
        return data;
    }
    
    // 辅助方法：创建 RerankResult
    private UnifiedResponse.RerankResult createRerankResult(int index, double score, String document) {
        UnifiedResponse.RerankResult result = new UnifiedResponse.RerankResult();
        result.setIndex(index);
        result.setRelevanceScore(score);
        result.setDocument(document);
        return result;
    }

    // ===================== 构造 =====================

    @Nested
    @DisplayName("构造测试")
    class ConstructionTests {

        @Test
        @DisplayName("默认构造注册全部六个协议适配器")
        void defaultConstructor_registersAllProtocols() {
            assertThat(sdk.registry().getRegisteredProtocols())
                    .containsExactlyInAnyOrder(
                            ProtocolType.OPENAI_CHAT,
                            ProtocolType.ANTHROPIC,
                            ProtocolType.GEMINI,
                            ProtocolType.OPENAI_RESPONSES,
                            ProtocolType.OPENAI_EMBEDDING,
                            ProtocolType.RERANK
                    );
        }

        @Test
        @DisplayName("自定义注册表构造")
        void customRegistryConstructor() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .register(new OpenAiChatProtocolAdapter(objectMapper))
                    .build();
            AiGatewaySdk customSdk = new AiGatewaySdk(registry, objectMapper);

            assertThat(customSdk.registry().getRegisteredProtocols())
                    .containsExactly(ProtocolType.OPENAI_CHAT);
        }

        @Test
        @DisplayName("null ObjectMapper 抛出 NullPointerException")
        void nullObjectMapper_throwsNPE() {
            assertThatThrownBy(() -> new AiGatewaySdk(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null Registry 抛出 NullPointerException")
        void nullRegistry_throwsNPE() {
            assertThatThrownBy(() -> new AiGatewaySdk(null, objectMapper))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ===================== adapter() =====================

    @Nested
    @DisplayName("adapter() 查询测试")
    class AdapterTests {

        @Test
        @DisplayName("获取 OpenAI Chat 适配器")
        void adapter_openAiChat() {
            ProtocolAdapter adapter = sdk.adapter(ProtocolType.OPENAI_CHAT);
            assertThat(adapter).isInstanceOf(OpenAiChatProtocolAdapter.class);
        }

        @Test
        @DisplayName("获取 Anthropic 适配器")
        void adapter_anthropic() {
            ProtocolAdapter adapter = sdk.adapter(ProtocolType.ANTHROPIC);
            assertThat(adapter).isInstanceOf(AnthropicProtocolAdapter.class);
        }

        @Test
        @DisplayName("获取 Gemini 适配器")
        void adapter_gemini() {
            ProtocolAdapter adapter = sdk.adapter(ProtocolType.GEMINI);
            assertThat(adapter).isInstanceOf(GeminiProtocolAdapter.class);
        }

        @Test
        @DisplayName("获取 OpenAI Responses 适配器")
        void adapter_openAiResponses() {
            ProtocolAdapter adapter = sdk.adapter(ProtocolType.OPENAI_RESPONSES);
            assertThat(adapter).isInstanceOf(OpenAiResponsesProtocolAdapter.class);
        }

        @Test
        @DisplayName("null 协议抛 NullPointerException")
        void adapter_null_throwsNPE() {
            assertThatThrownBy(() -> sdk.adapter(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("获取 OpenAI Embedding 适配器")
        void adapter_openAiEmbedding() {
            ProtocolAdapter adapter = sdk.adapter(ProtocolType.OPENAI_EMBEDDING);
            assertThat(adapter).isInstanceOf(OpenAiEmbeddingProtocolAdapter.class);
        }

        @Test
        @DisplayName("获取 Rerank 适配器")
        void adapter_rerank() {
            ProtocolAdapter adapter = sdk.adapter(ProtocolType.RERANK);
            assertThat(adapter).isInstanceOf(RerankProtocolAdapter.class);
        }
    }

    // ===================== parse() =====================

    @Nested
    @DisplayName("parse() 解析测试")
    class ParseTests {

        @Test
        @DisplayName("parse(ProtocolType, String) 解析 OpenAI Chat 请求")
        void parse_openAiChat_fromJson() {
            String json = """
                    {
                        "model": "gpt-4",
                        "messages": [
                            {"role": "user", "content": "Hello"}
                        ],
                        "stream": true
                    }""";

            UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, json);

            assertThat(request.getModel()).isEqualTo("gpt-4");
            assertThat(request.getStream()).isTrue();
        }

        @Test
        @DisplayName("parse(ProtocolType, Map) 解析 Anthropic 请求")
        void parse_anthropic_fromMap() {
            Map<String, Object> rawMap = Map.of(
                    "model", "claude-sonnet-4-6",
                    "max_tokens", 1024,
                    "messages", List.of(Map.of("role", "user", "content", "Hi"))
            );

            UnifiedRequest request = sdk.parse(ProtocolType.ANTHROPIC, rawMap);

            assertThat(request.getModel()).isEqualTo("claude-sonnet-4-6");
        }

        @Test
        @DisplayName("parse 无效 JSON 抛出 IllegalArgumentException")
        void parse_invalidJson_throwsException() {
            assertThatThrownBy(() -> sdk.parse(ProtocolType.OPENAI_CHAT, "not json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("failed to parse JSON");
        }

        @Test
        @DisplayName("parse null protocol 抛出 NullPointerException")
        void parse_nullProtocol_throwsNPE() {
            assertThatThrownBy(() -> sdk.parse(null, "{}"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("parse null rawJson 抛出 NullPointerException")
        void parse_nullJson_throwsNPE() {
            assertThatThrownBy(() -> sdk.parse(ProtocolType.OPENAI_CHAT, (String) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("parse null Map 抛出 NullPointerException")
        void parse_nullMap_throwsNPE() {
            assertThatThrownBy(() -> sdk.parse(ProtocolType.OPENAI_CHAT, (Map<String, Object>) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ===================== encodeResponse() =====================

    @Nested
    @DisplayName("encodeResponse() 编码测试")
    class EncodeResponseTests {

        @Test
        @DisplayName("编码 OpenAI Chat 响应")
        void encodeResponse_openAiChat() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("chatcmpl-123");
            response.setModel("gpt-4");
            response.setCreated(1700000000L);

            String json = sdk.encodeResponse(ProtocolType.OPENAI_CHAT, response);

            assertThat(json).contains("chatcmpl-123").contains("gpt-4");
        }

        @Test
        @DisplayName("编码 Anthropic 响应")
        void encodeResponse_anthropic() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("msg_456");
            response.setModel("claude-sonnet-4-6");

            String json = sdk.encodeResponse(ProtocolType.ANTHROPIC, response);

            assertThat(json).contains("msg_456");
        }

        @Test
        @DisplayName("null 协议抛出 NullPointerException")
        void encodeResponse_nullProtocol_throwsNPE() {
            assertThatThrownBy(() -> sdk.encodeResponse(null, new UnifiedResponse()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null 响应抛出 NullPointerException")
        void encodeResponse_nullResponse_throwsNPE() {
            assertThatThrownBy(() -> sdk.encodeResponse(ProtocolType.OPENAI_CHAT, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ===================== buildError() =====================

    @Nested
    @DisplayName("buildError() 错误构建测试")
    class BuildErrorTests {

        @Test
        @DisplayName("用 ErrorCode 构建 OpenAI Chat 错误")
        void buildError_withErrorCode_openAiChat() {
            String error = sdk.buildError(ProtocolType.OPENAI_CHAT, "model not found", ErrorCode.MODEL_NOT_FOUND);

            assertThat(error).contains("model not found").contains("MODEL_NOT_FOUND");
        }

        @Test
        @DisplayName("用 ErrorCode 构建 Anthropic 错误")
        void buildError_withErrorCode_anthropic() {
            String error = sdk.buildError(ProtocolType.ANTHROPIC, "auth failed", ErrorCode.AUTH_FAILED);

            assertThat(error).contains("auth failed");
        }

        @Test
        @DisplayName("用自定义 errorType 构建错误")
        void buildError_withCustomType() {
            String error = sdk.buildError(ProtocolType.GEMINI, "timeout", "upstream_timeout", "504");

            assertThat(error).contains("timeout");
        }

        @Test
        @DisplayName("null 协议抛 NullPointerException")
        void buildError_nullProtocolWithErrorCode_throwsNPE() {
            assertThatThrownBy(() -> sdk.buildError(null, "msg", ErrorCode.INTERNAL_ERROR))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null ErrorCode 抛 NullPointerException")
        void buildError_nullErrorCode_throwsNPE() {
            assertThatThrownBy(() -> sdk.buildError(ProtocolType.OPENAI_CHAT, "msg", (ErrorCode) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ===================== 集成流程 =====================

    @Nested
    @DisplayName("集成流程测试")
    class IntegrationTests {

        @Test
        @DisplayName("完整流程：parse → encodeResponse")
        void parseThenEncode_completeFlow() {
            // 1. 解析 OpenAI Chat 请求
            String requestJson = """
                    {
                        "model": "gpt-4",
                        "messages": [{"role": "user", "content": "Hello"}]
                    }""";
            UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, requestJson);
            assertThat(request.getModel()).isEqualTo("gpt-4");

            // 2. 构建响应
            UnifiedResponse response = new UnifiedResponse();
            response.setId("resp-1");
            response.setModel(request.getModel());

            // 3. 编码为 Anthropic 格式
            String anthropicJson = sdk.encodeResponse(ProtocolType.ANTHROPIC, response);
            assertThat(anthropicJson).contains("resp-1");
        }

        @Test
        @DisplayName("跨协议错误处理")
        void crossProtocol_errorHandling() {
            // 用 Anthropic 错误格式响应 OpenAI 请求错误
            String error = sdk.buildError(ProtocolType.ANTHROPIC,
                    "API key exhausted", ErrorCode.RATE_LIMITED);

            assertThat(error).isNotEmpty();
            assertThat(error).contains("exhausted");
        }

        @Test
        @DisplayName("完整流程：Embedding parse → encodeResponse")
        void embedding_parseThenEncode_completeFlow() {
            // 1. 解析 Embedding 请求
            String requestJson = """
                    {
                        "model": "text-embedding-ada-002",
                        "input": "Hello world"
                    }""";
            UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_EMBEDDING, requestJson);
            assertThat(request.getModel()).isEqualTo("text-embedding-ada-002");
            assertThat(request.getEmbeddingInput()).isEqualTo("Hello world");

            // 2. 构建响应
            UnifiedResponse response = new UnifiedResponse();
            response.setId("embedding-1");
            response.setModel("text-embedding-ada-002");
            List<UnifiedResponse.EmbeddingData> embeddingData = List.of(
                    createEmbeddingData(0, new double[]{0.1, 0.2, 0.3})
            );
            response.setEmbeddingData(embeddingData);

            // 3. 编码响应
            String encoded = sdk.encodeResponse(ProtocolType.OPENAI_EMBEDDING, response);
            assertThat(encoded).contains("embedding-1");
            assertThat(encoded).contains("text-embedding-ada-002");
            assertThat(encoded).contains("0.1");
        }

        @Test
        @DisplayName("完整流程：Rerank parse → encodeResponse")
        void rerank_parseThenEncode_completeFlow() {
            // 1. 解析 Rerank 请求
            String requestJson = """
                    {
                        "model": "rerank-english-v2.0",
                        "query": "What is the capital of France?",
                        "documents": ["Paris is the capital of France.", "London is the capital of England."]
                    }""";
            UnifiedRequest request = sdk.parse(ProtocolType.RERANK, requestJson);
            assertThat(request.getModel()).isEqualTo("rerank-english-v2.0");
            assertThat(request.getRerankQuery()).isEqualTo("What is the capital of France?");
            assertThat(request.getRerankDocuments()).hasSize(2);

            // 2. 构建响应
            UnifiedResponse response = new UnifiedResponse();
            response.setId("rerank-1");
            response.setModel("rerank-english-v2.0");
            List<UnifiedResponse.RerankResult> rerankResults = List.of(
                    createRerankResult(0, 0.95, "Paris is the capital of France."),
                    createRerankResult(1, 0.05, "London is the capital of England.")
            );
            response.setRerankResults(rerankResults);

            // 3. 编码响应
            String encoded = sdk.encodeResponse(ProtocolType.RERANK, response);
            assertThat(encoded).contains("rerank-1");
            assertThat(encoded).contains("rerank-english-v2.0");
            assertThat(encoded).contains("0.95");
        }
    }
}
