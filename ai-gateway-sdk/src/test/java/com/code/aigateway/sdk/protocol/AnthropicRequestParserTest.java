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

@DisplayName("AnthropicRequestParser 测试")
class AnthropicRequestParserTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private AnthropicRequestParser parser;

    @BeforeEach
    void setUp() {
        parser = new AnthropicRequestParser(mapper);
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
        @DisplayName("正常解析 model, stream, max_tokens")
        void basicFields() {
            Map<String, Object> req = Map.of("model", "claude-3-sonnet-20240229",
                    "max_tokens", 1024, "messages", List.of());

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getRequestProtocol()).isEqualTo("anthropic");
            assertThat(result.getModel()).isEqualTo("claude-3-sonnet-20240229");
            assertThat(result.getGenerationConfig().getMaxOutputTokens()).isEqualTo(1024);
            assertThat(result.getStream()).isFalse();
        }

        @Test
        @DisplayName("stream=true 正确解析")
        void streamTrue() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "stream", true);
            assertThat(parser.parse(req).getStream()).isTrue();
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
        @DisplayName("简单 user 文本消息（字符串 content）")
        void simpleUserText() {
            Map<String, Object> msg = Map.of("role", "user", "content", "Hello");
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getMessages()).hasSize(1);
            UnifiedMessage um = result.getMessages().get(0);
            assertThat(um.getRole()).isEqualTo("user");
            assertThat(um.getParts()).hasSize(1);
            assertThat(um.getParts().get(0).getText()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("assistant 消息含 tool_use 块")
        void assistantWithToolUse() {
            Map<String, Object> toolBlock = Map.of("type", "tool_use", "id", "toolu_01",
                    "name", "get_weather", "input", Map.of("city", "Beijing"));
            Map<String, Object> msg = Map.of("role", "assistant", "content", List.of(toolBlock));
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedRequest result = parser.parse(req);
            List<UnifiedMessage> msgs = result.getMessages();
            assertThat(msgs).hasSize(1);
            assertThat(msgs.get(0).getRole()).isEqualTo("assistant");
            assertThat(msgs.get(0).getToolCalls()).hasSize(1);
            UnifiedToolCall tc = msgs.get(0).getToolCalls().get(0);
            assertThat(tc.getId()).isEqualTo("toolu_01");
            assertThat(tc.getToolName()).isEqualTo("get_weather");
            assertThat(tc.getArgumentsJson()).contains("Beijing");
        }

        @Test
        @DisplayName("assistant 消息含 thinking 块")
        void assistantWithThinking() {
            Map<String, Object> thinkingBlock = Map.of("type", "thinking", "thinking", "Let me think...");
            Map<String, Object> msg = Map.of("role", "assistant", "content", List.of(thinkingBlock));
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedRequest result = parser.parse(req);
            List<UnifiedPart> parts = result.getMessages().get(0).getParts();
            assertThat(parts).hasSize(1);
            assertThat(parts.get(0).getType()).isEqualTo("thinking");
            assertThat(parts.get(0).getText()).isEqualTo("Let me think...");
        }

        @Test
        @DisplayName("user 消息含 tool_result 块")
        void userToolResult() {
            Map<String, Object> tr = Map.of("type", "tool_result", "tool_use_id", "toolu_01",
                    "content", "Sunny, 25°C");
            Map<String, Object> msg = Map.of("role", "user", "content", List.of(tr));
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedRequest result = parser.parse(req);
            List<UnifiedMessage> msgs = result.getMessages();
            assertThat(msgs).hasSize(1);
            assertThat(msgs.get(0).getRole()).isEqualTo("tool");
            assertThat(msgs.get(0).getToolCallId()).isEqualTo("toolu_01");
        }

        @Test
        @DisplayName("user 消息含数组格式 tool_result content")
        void toolResultArrayContent() {
            Map<String, Object> contentPart = Map.of("type", "text", "text", "result text");
            Map<String, Object> tr = Map.of("type", "tool_result", "tool_use_id", "toolu_02",
                    "content", List.of(contentPart));
            Map<String, Object> msg = Map.of("role", "user", "content", List.of(tr));
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getMessages().get(0).getParts().get(0).getText()).isEqualTo("result text");
        }
    }

    @Nested
    @DisplayName("系统提示词")
    class SystemPrompt {

        @Test
        @DisplayName("字符串格式 system prompt")
        void stringSystemPrompt() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(),
                    "system", "You are a helpful assistant");
            assertThat(parser.parse(req).getSystemPrompt()).isEqualTo("You are a helpful assistant");
        }

        @Test
        @DisplayName("数组格式 system prompt")
        void arraySystemPrompt() {
            List<Map<String, Object>> systemBlocks = List.of(
                    Map.of("type", "text", "text", "Part 1"),
                    Map.of("type", "text", "text", "Part 2")
            );
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "system", systemBlocks);
            assertThat(parser.parse(req).getSystemPrompt()).isEqualTo("Part 1\nPart 2");
        }

        @Test
        @DisplayName("空 system prompt 返回 null")
        void emptySystem() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "system", "");
            assertThat(parser.parse(req).getSystemPrompt()).isNull();
        }
    }

    @Nested
    @DisplayName("工具定义")
    class Tools {

        @Test
        @DisplayName("解析 Anthropic 工具定义（input_schema）")
        void anthropicTools() {
            Map<String, Object> schema = Map.of("type", "object", "properties", Map.of());
            Map<String, Object> tool = Map.of("name", "get_weather", "description", "Get weather",
                    "input_schema", schema);
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "tools", List.of(tool));

            UnifiedRequest result = parser.parse(req);
            assertThat(result.getTools()).hasSize(1);
            assertThat(result.getTools().get(0).getName()).isEqualTo("get_weather");
            assertThat(result.getTools().get(0).getInputSchema()).isEqualTo(schema);
        }

        @Test
        @DisplayName("空工具列表")
        void emptyTools() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "tools", List.of());
            assertThat(parser.parse(req).getTools()).isEmpty();
        }
    }

    @Nested
    @DisplayName("工具选择")
    class ToolChoice {

        @Test
        @DisplayName("auto 字符串")
        void autoString() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "tool_choice", "auto");
            assertThat(parser.parse(req).getToolChoice().getType()).isEqualTo("auto");
        }

        @Test
        @DisplayName("对象格式指定工具名")
        void specificTool() {
            Map<String, Object> tc = Map.of("type", "tool", "name", "get_weather");
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "tool_choice", tc);
            UnifiedToolChoice choice = parser.parse(req).getToolChoice();
            assertThat(choice.getType()).isEqualTo("specific");
            assertThat(choice.getToolName()).isEqualTo("get_weather");
        }

        @Test
        @DisplayName("无效字符串 tool_choice 抛出异常")
        void invalidToolChoice() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "tool_choice", "invalid");
            assertThatThrownBy(() -> parser.parse(req)).isInstanceOf(ProtocolException.class);
        }
    }

    @Nested
    @DisplayName("生成配置")
    class GenerationConfig {

        @Test
        @DisplayName("thinking 启用时映射到 reasoning")
        void thinkingEnabled() {
            Map<String, Object> thinking = Map.of("type", "enabled", "budget_tokens", 1024);
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(), "thinking", thinking);
            UnifiedGenerationConfig cfg = parser.parse(req).getGenerationConfig();
            assertThat(cfg.getReasoning().getEnabled()).isTrue();
            assertThat(cfg.getReasoning().getBudgetTokens()).isEqualTo(1024);
        }

        @Test
        @DisplayName("stop_sequences 正确解析")
        void stopSequences() {
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(),
                    "stop_sequences", List.of("END", "STOP"));
            assertThat(parser.parse(req).getGenerationConfig().getStopSequences())
                    .containsExactly("END", "STOP");
        }
    }

    @Nested
    @DisplayName("图片解析")
    class ImageParsing {

        @Test
        @DisplayName("base64 图片")
        void base64Image() {
            Map<String, Object> source = Map.of("type", "base64", "media_type", "image/png",
                    "data", "base64data");
            Map<String, Object> block = Map.of("type", "image", "source", source);
            Map<String, Object> msg = Map.of("role", "user", "content", List.of(block));
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedPart part = parser.parse(req).getMessages().get(0).getParts().get(0);
            assertThat(part.getType()).isEqualTo("image");
            assertThat(part.getMimeType()).isEqualTo("image/png");
            assertThat(part.getBase64Data()).isEqualTo("base64data");
        }

        @Test
        @DisplayName("URL 图片")
        void urlImage() {
            Map<String, Object> source = Map.of("type", "url", "url", "https://example.com/img.png");
            Map<String, Object> block = Map.of("type", "image", "source", source);
            Map<String, Object> msg = Map.of("role", "user", "content", List.of(block));
            Map<String, Object> req = Map.of("model", "claude-3", "messages", List.of(msg));

            UnifiedPart part = parser.parse(req).getMessages().get(0).getParts().get(0);
            assertThat(part.getType()).isEqualTo("image");
            assertThat(part.getUrl()).isEqualTo("https://example.com/img.png");
        }
    }
}
