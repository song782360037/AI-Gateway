package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProtocolUtils 工具类测试")
class ProtocolUtilsTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("toStringMap")
    class ToStringMap {

        @Test
        @DisplayName("正常 Map 转换")
        void normalMap() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "value");
            input.put("num", 42);
            Map<String, Object> result = ProtocolUtils.toStringMap(input);
            assertThat(result).containsEntry("key", "value");
            assertThat(result).containsEntry("num", 42);
        }

        @Test
        @DisplayName("混合键类型的 Map，非 String 键应被过滤")
        void mixedKeys() {
            Map<Object, Object> input = new HashMap<>();
            input.put("str", "value");
            input.put(1, "should-be-filtered");
            Map<String, Object> result = ProtocolUtils.toStringMap(input);
            assertThat(result).containsEntry("str", "value");
            assertThat(result).hasSize(1).containsEntry("str", "value");
        }

        @Test
        @DisplayName("空 Map")
        void emptyMap() {
            assertThat(ProtocolUtils.toStringMap(Map.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("toMap")
    class ToMapMethod {

        @Test
        @DisplayName("Map 输入直接转为 Map<String, Object>")
        void mapInput() {
            Map<String, Object> input = Map.of("model", "gpt-4");
            Map<String, Object> result = ProtocolUtils.toMap(mapper, input, "req");
            assertThat(result).containsEntry("model", "gpt-4");
        }

        @Test
        @DisplayName("POJO 输入通过 Jackson convertValue 转换")
        void pojoInput() {
            record Req(String model, int maxTokens) {}
            Map<String, Object> result = ProtocolUtils.toMap(mapper, new Req("gpt-4", 100), "req");
            assertThat(result).containsEntry("model", "gpt-4").containsEntry("maxTokens", 100);
        }

        @Test
        @DisplayName("非法输入（非 Map 非 POJO）抛出异常")
        void invalidInput() {
            assertThatThrownBy(() -> ProtocolUtils.toMap(mapper, "invalid", "req"))
                    .isInstanceOf(ProtocolException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("requireString")
    class RequireString {

        @Test
        @DisplayName("正常字符串值")
        void validString() {
            String result = ProtocolUtils.requireString(Map.of("key", "value"), "key", "missing");
            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("缺失键抛出异常")
        void missingKey() {
            assertThatThrownBy(() -> ProtocolUtils.requireString(Map.of(), "key", "key is required"))
                    .isInstanceOf(ProtocolException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("空白字符串抛出异常")
        void blankString() {
            assertThatThrownBy(() -> ProtocolUtils.requireString(Map.of("key", "   "), "key", "key is blank"))
                    .isInstanceOf(ProtocolException.class);
        }
    }

    @Nested
    @DisplayName("requireList")
    class RequireList {

        @Test
        @DisplayName("正常 Map 列表")
        void validList() {
            Map<String, Object> input = Map.of("items", List.of(Map.of("name", "a"), Map.of("name", "b")));
            List<Map<String, Object>> result = ProtocolUtils.requireList(input, "items", "items required");
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("name", "a");
        }

        @Test
        @DisplayName("非 List 值抛出异常")
        void notList() {
            assertThatThrownBy(() -> ProtocolUtils.requireList(Map.of("items", "not-list"), "items", "bad"))
                    .isInstanceOf(ProtocolException.class);
        }

        @Test
        @DisplayName("缺失键抛出异常")
        void missingKey() {
            assertThatThrownBy(() -> ProtocolUtils.requireList(Map.of(), "items", "items required"))
                    .isInstanceOf(ProtocolException.class);
        }
    }

    @Nested
    @DisplayName("textPart")
    class TextPart {

        @Test
        @DisplayName("创建文本 part")
        void createTextPart() {
            UnifiedPart part = ProtocolUtils.textPart("hello");
            assertThat(part.getType()).isEqualTo("text");
            assertThat(part.getText()).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("stringify")
    class Stringify {

        @Test
        @DisplayName("正常序列化")
        void normalSerialization() {
            String json = ProtocolUtils.stringify(mapper, Map.of("key", "value"));
            assertThat(json).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        @DisplayName("序列化失败返回 {}")
        void serializationFailure() {
            Object circular = new Object() {
                final Object self = this;
            };
            String result = ProtocolUtils.stringify(mapper, circular);
            assertThat(result).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("parseDataUri")
    class ParseDataUri {

        @Test
        @DisplayName("data: URI 含 base64")
        void dataUriWithBase64() {
            UnifiedPart part = ProtocolUtils.parseDataUri("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk");
            assertThat(part.getType()).isEqualTo("image");
            assertThat(part.getMimeType()).isEqualTo("image/png");
            assertThat(part.getBase64Data()).startsWith("iVBORw0KGgo");
        }

        @Test
        @DisplayName("普通 URL（非 data:）")
        void regularUrl() {
            UnifiedPart part = ProtocolUtils.parseDataUri("https://example.com/image.png");
            assertThat(part.getType()).isEqualTo("image");
            assertThat(part.getUrl()).isEqualTo("https://example.com/image.png");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null 或空 URL")
        void nullOrEmptyUrl(String url) {
            UnifiedPart part = ProtocolUtils.parseDataUri(url);
            assertThat(part.getType()).isEqualTo("image");
            // null 生成 null URL，空字符串保留为空字符串
            if (url == null) {
                assertThat(part.getUrl()).isNull();
            } else {
                assertThat(part.getUrl()).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("buildStreamErrorBody")
    class BuildStreamErrorBody {

        @Test
        @DisplayName("ProtocolException 转换为错误响应")
        void protocolException() {
            ProtocolAdapter adapter = new ProtocolAdapter() {
                @Override public ProtocolType getProtocolType() { return ProtocolType.OPENAI_CHAT; }
                @Override public boolean isSse() { return true; }
                @Override public Object buildError(String message, String errorType, String code, String param) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("message", message);
                    err.put("errorType", errorType);
                    err.put("code", code);
                    err.put("param", param);
                    return err;
                }
                @Override public String mapErrorType(ErrorCode errorCode) { return errorCode.name(); }
                @Override public UnifiedRequest parse(Object rawRequest) { return null; }
                @Override public Object encodeResponse(UnifiedResponse response) { return null; }
                @Override
                public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) { return null; }
            };
            ProtocolException pe = new ProtocolException(ErrorCode.INVALID_REQUEST, "bad request", "model");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) ProtocolUtils.buildStreamErrorBody(pe, adapter);
            assertThat(result).containsEntry("message", "bad request");
        }

        @Test
        @DisplayName("普通异常回退到 INTERNAL_ERROR")
        void genericException() {
            ProtocolAdapter adapter = new ProtocolAdapter() {
                @Override public ProtocolType getProtocolType() { return ProtocolType.ANTHROPIC; }
                @Override public boolean isSse() { return true; }
                @Override public Object buildError(String message, String errorType, String code, String param) {
                    return Map.of("message", message, "errorType", errorType, "code", code);
                }
                @Override public String mapErrorType(ErrorCode errorCode) { return errorCode.name(); }
                @Override public UnifiedRequest parse(Object rawRequest) { return null; }
                @Override public Object encodeResponse(UnifiedResponse response) { return null; }
                @Override
                public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) { return null; }
            };
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) ProtocolUtils.buildStreamErrorBody(
                    new RuntimeException("oops"), adapter);
            assertThat(result).containsEntry("code", ErrorCode.INTERNAL_ERROR.name());
        }
    }
}
