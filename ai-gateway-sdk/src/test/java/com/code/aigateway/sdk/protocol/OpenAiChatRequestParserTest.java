package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OpenAiChatRequestParser 测试")
class OpenAiChatRequestParserTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private OpenAiChatRequestParser parser;

    @BeforeEach
    void setUp() {
        parser = new OpenAiChatRequestParser(mapper);
    }

    @Test
    @DisplayName("null 请求抛出异常")
    void nullRequest() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("基础字段解析")
    class BasicFields {

        @Test
        @DisplayName("正常解析 model, stream, temperature")
        void basicFields() {
            Map<String, Object> req = new java.util.LinkedHashMap<>();
            req.put("model", "gpt-4o");
            req.put("messages", List.of());
            req.put("temperature", 0.7);
            req.put("stream", true);

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getRequestProtocol()).isEqualTo("openai-chat");
            assertThat(result.getModel()).isEqualTo("gpt-4o");
            assertThat(result.getStream()).isTrue();
            assertThat(result.getGenerationConfig().getTemperature()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("缺失 model 抛出异常")
        void missingModel() {
            assertThatThrownBy(() -> parser.parse(Map.of("messages", List.of())))
                    .isInstanceOf(ProtocolException.class);
        }
    }

    @Nested
    @DisplayName("消息解析")
    class MessageParsing {

        @Test
        @DisplayName("简单 user 文本消息")
        void simpleUserText() {
            Map<String, Object> msg = Map.of("role", "user", "content", "Hello");
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(msg));

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
            assertThat(result.getMessages().get(0).getParts().get(0).getText()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("system 消息收集为 systemPrompt")
        void systemMessage() {
            Map<String, Object> sys = Map.of("role", "system", "content", "You are helpful");
            Map<String, Object> user = Map.of("role", "user", "content", "Hi");
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(sys, user));

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getSystemPrompt()).isEqualTo("You are helpful");
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("多 system 消息合并（\\n\\n 分隔）")
        void multipleSystemMessages() {
            Map<String, Object> sys1 = Map.of("role", "system", "content", "Part 1");
            Map<String, Object> sys2 = Map.of("role", "system", "content", "Part 2");
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(sys1, sys2));
            assertThat(parser.parse(req).getSystemPrompt()).isEqualTo("Part 1\n\nPart 2");
        }

        @Test
        @DisplayName("消息含 tool_calls")
        void messageWithToolCalls() {
            Map<String, Object> function = Map.of("name", "get_weather", "arguments", "{\"city\":\"Beijing\"}");
            Map<String, Object> tc = Map.of("id", "call_01", "type", "function", "function", function);
            Map<String, Object> msg = Map.of("role", "assistant", "tool_calls", List.of(tc));
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(msg));

            List<UnifiedToolCall> calls = parser.parse(req).getMessages().get(0).getToolCalls();
            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).getId()).isEqualTo("call_01");
            assertThat(calls.get(0).getToolName()).isEqualTo("get_weather");
            assertThat(calls.get(0).getArgumentsJson()).isEqualTo("{\"city\":\"Beijing\"}");
        }

        @Test
        @DisplayName("消息 content 为数组格式")
        void contentAsArray() {
            List<Map<String, Object>> content = List.of(
                    Map.of("type", "text", "text", "What is this?"),
                    Map.of("type", "image_url", "image_url", Map.of("url", "https://example.com/img.png"))
            );
            Map<String, Object> msg = Map.of("role", "user", "content", content);
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(msg));

            List<UnifiedPart> parts = parser.parse(req).getMessages().get(0).getParts();
            assertThat(parts).hasSize(2);
            assertThat(parts.get(0).getType()).isEqualTo("text");
            assertThat(parts.get(1).getType()).isEqualTo("image");
            assertThat(parts.get(1).getUrl()).isEqualTo("https://example.com/img.png");
        }

        @Test
        @DisplayName("消息含 tool_call_id（tool 角色）")
        void messageWithToolCallId() {
            Map<String, Object> msg = Map.of("role", "tool", "content", "result", "tool_call_id", "call_01");
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(msg));

            UnifiedMessage um = parser.parse(req).getMessages().get(0);
            assertThat(um.getRole()).isEqualTo("tool");
            assertThat(um.getToolCallId()).isEqualTo("call_01");
        }
    }

    @Nested
    @DisplayName("生成配置")
    class GenerationConfig {

        @Test
        @DisplayName("max_completion_tokens 优先于 max_tokens")
        void maxCompletionTokensPriority() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(),
                    "max_tokens", 500, "max_completion_tokens", 1000);
            assertThat(parser.parse(req).getGenerationConfig().getMaxOutputTokens()).isEqualTo(1000);
        }

        @Test
        @DisplayName("仅 max_tokens 时使用 max_tokens")
        void onlyMaxTokens() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "max_tokens", 500);
            assertThat(parser.parse(req).getGenerationConfig().getMaxOutputTokens()).isEqualTo(500);
        }

        @Test
        @DisplayName("reasoning_effort 映射到 reasoning")
        void reasoningEffort() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "reasoning_effort", "high");
            UnifiedGenerationConfig cfg = parser.parse(req).getGenerationConfig();
            assertThat(cfg.getReasoning().getEnabled()).isTrue();
            assertThat(cfg.getReasoning().getEffort()).isEqualTo("high");
        }

        @Test
        @DisplayName("stop 字符串转为 List")
        void stopAsString() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "stop", "END");
            assertThat(parser.parse(req).getGenerationConfig().getStopSequences()).containsExactly("END");
        }

        @Test
        @DisplayName("stop 数组")
        void stopAsArray() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(),
                    "stop", List.of("END", "STOP"));
            assertThat(parser.parse(req).getGenerationConfig().getStopSequences()).containsExactly("END", "STOP");
        }
    }

    @Nested
    @DisplayName("工具定义")
    class Tools {

        @Test
        @DisplayName("解析 OpenAI 工具定义")
        void openaiTools() {
            Map<String, Object> function = Map.of("name", "get_weather",
                    "description", "Get weather",
                    "parameters", Map.of("type", "object"));
            Map<String, Object> tool = Map.of("type", "function", "function", function);
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "tools", List.of(tool));

            List<UnifiedTool> tools = parser.parse(req).getTools();
            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).getName()).isEqualTo("get_weather");
            assertThat(tools.get(0).getInputSchema()).isNotNull();
        }

        @Test
        @DisplayName("空工具列表")
        void emptyTools() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "tools", List.of());
            assertThat(parser.parse(req).getTools()).isEmpty();
        }
    }

    @Nested
    @DisplayName("工具选择")
    class ToolChoice {

        @Test
        @DisplayName("auto 字符串")
        void autoString() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "tool_choice", "auto");
            assertThat(parser.parse(req).getToolChoice().getType()).isEqualTo("auto");
        }

        @Test
        @DisplayName("none 字符串")
        void noneString() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "tool_choice", "none");
            assertThat(parser.parse(req).getToolChoice().getType()).isEqualTo("none");
        }

        @Test
        @DisplayName("对象格式指定工具名")
        void specificTool() {
            Map<String, Object> func = Map.of("name", "get_weather");
            Map<String, Object> tc = Map.of("type", "function", "function", func);
            Map<String, Object> function = Map.of("name", "get_weather",
                    "parameters", Map.of("type", "object"));
            Map<String, Object> tool = Map.of("type", "function", "function", function);
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(),
                    "tools", List.of(tool), "tool_choice", tc);

            UnifiedToolChoice result = parser.parse(req).getToolChoice();
            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo("specific");
        }

        @Test
        @DisplayName("无效字符串 tool_choice 抛出异常")
        void invalidToolChoice() {
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "tool_choice", "invalid");
            assertThatThrownBy(() -> parser.parse(req)).isInstanceOf(ProtocolException.class);
        }

        @Test
        @DisplayName("特定工具名不在 tools 中抛出异常")
        void toolChoiceNotInTools() {
            Map<String, Object> func = Map.of("name", "nonexistent");
            Map<String, Object> tc = Map.of("type", "function", "function", func);
            Map<String, Object> function = Map.of("name", "real_tool",
                    "parameters", Map.of("type", "object"));
            Map<String, Object> tool = Map.of("type", "function", "function", function);
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(),
                    "tools", List.of(tool), "tool_choice", tc);

            assertThatThrownBy(() -> parser.parse(req)).isInstanceOf(ProtocolException.class);
        }
    }

    @Nested
    @DisplayName("响应格式")
    class ResponseFormat {

        @Test
        @DisplayName("json_object 格式")
        void jsonObject() {
            Map<String, Object> rf = Map.of("type", "json_object");
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "response_format", rf);

            UnifiedResponseFormat format = parser.parse(req).getResponseFormat();
            assertThat(format.getType()).isEqualTo("json_object");
        }

        @Test
        @DisplayName("json_schema 格式含 schema 定义")
        void jsonSchema() {
            Map<String, Object> schema = Map.of("name", "math_response", "strict", true,
                    "schema", Map.of("type", "object"));
            Map<String, Object> rf = Map.of("type", "json_schema", "json_schema", schema);
            Map<String, Object> req = Map.of("model", "gpt-4o", "messages", List.of(), "response_format", rf);

            UnifiedResponseFormat format = parser.parse(req).getResponseFormat();
            assertThat(format.getType()).isEqualTo("json_schema");
            assertThat(format.getName()).isEqualTo("math_response");
            assertThat(format.getStrict()).isTrue();
        }
    }
}
