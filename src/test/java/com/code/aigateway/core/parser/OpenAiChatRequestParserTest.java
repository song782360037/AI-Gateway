package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiChatRequestParserTest {

    private final OpenAiChatRequestParser parser = new OpenAiChatRequestParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parse_mergesLeadingStringSystemMessages_andPreservesLaterSystemMessages() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(
                message("system", "系统提示 1"),
                multimodalMessage("system", List.of(Map.of("type", "text", "text", "保留的 system 内容"))),
                message("user", "你好"),
                message("system", "系统提示 2")
        ));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertEquals("系统提示 1\n\n系统提示 2", unifiedRequest.getSystemPrompt());
        assertEquals("openai-chat", unifiedRequest.getRequestProtocol());
        assertEquals("openai-chat", unifiedRequest.getResponseProtocol());
        assertEquals(2, unifiedRequest.getMessages().size());

        UnifiedMessage preservedSystem = unifiedRequest.getMessages().get(0);
        assertEquals("system", preservedSystem.getRole());
        assertEquals("保留的 system 内容", preservedSystem.getParts().get(0).getText());

        UnifiedMessage userMessage = unifiedRequest.getMessages().get(1);
        assertEquals("user", userMessage.getRole());
        assertEquals("你好", userMessage.getParts().get(0).getText());
    }

    @Test
    void parse_mapsStructuredOutputAssistantToolCallsAndMetadata() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMetadata(Map.of("traceId", "trace-1"));
        request.setResponseFormat(jsonSchemaResponseFormat());
        request.setMessages(List.of(assistantMessageWithToolCalls()));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getResponseFormat());
        assertEquals("json_schema", unifiedRequest.getResponseFormat().getType());
        assertEquals("weather_output", unifiedRequest.getResponseFormat().getName());
        assertEquals(Boolean.TRUE, unifiedRequest.getResponseFormat().getStrict());
        assertEquals("object", unifiedRequest.getResponseFormat().getSchema().get("type"));
        assertEquals("trace-1", unifiedRequest.getMetadata().get("traceId"));
        assertEquals(1, unifiedRequest.getMessages().size());
        assertNotNull(unifiedRequest.getMessages().get(0).getToolCalls());
        assertEquals(1, unifiedRequest.getMessages().get(0).getToolCalls().size());
        assertEquals("call_1", unifiedRequest.getMessages().get(0).getToolCalls().get(0).getId());
        assertEquals("get_weather", unifiedRequest.getMessages().get(0).getToolCalls().get(0).getToolName());
        assertEquals("{\"city\":\"Shanghai\"}", unifiedRequest.getMessages().get(0).getToolCalls().get(0).getArgumentsJson());
    }

    @Test
    void parse_mapsImageUrlDetailAndDataUrl() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(multimodalMessage("user", List.of(
                Map.of("type", "text", "text", "请分析图片"),
                Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "https://example.com/image.png", "detail", "high")
                ),
                Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:image/png;base64,QUJDRA==")
                )
        ))));

        UnifiedRequest unifiedRequest = parser.parse(request);
        List<UnifiedPart> parts = unifiedRequest.getMessages().get(0).getParts();

        assertEquals(3, parts.size());
        assertEquals("text", parts.get(0).getType());
        assertEquals("请分析图片", parts.get(0).getText());
        assertEquals("image", parts.get(1).getType());
        assertEquals("https://example.com/image.png", parts.get(1).getUrl());
        assertEquals("high", parts.get(1).getAttributes().get("detail"));
        assertEquals("image", parts.get(2).getType());
        assertEquals("image/png", parts.get(2).getMimeType());
        assertEquals("QUJDRA==", parts.get(2).getBase64Data());
        assertNull(parts.get(2).getUrl());
    }

    @Test
    void parse_throwsInvalidRequest_whenSpecificToolChoiceReferencesUnknownTool() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setTools(List.of(tool("get_weather")));
        request.setMessages(List.of(message("user", "今天天气怎么样")));
        request.setToolChoice(Map.of(
                "type", "function",
                "function", Map.of("name", "missing_tool")
        ));

        GatewayException exception = assertThrows(GatewayException.class, () -> parser.parse(request));

        assertEquals("tool_choice.function.name must match one of tools", exception.getMessage());
        assertEquals("tool_choice.function.name", exception.getParam());
    }

    @Test
    void parse_acceptsSingleStringStop_andPrefersMaxCompletionTokensWhenConflict() throws Exception {
        String json = """
                {
                  "model": "gpt-4o-mini",
                  "messages": [
                    {"role": "user", "content": "你好"}
                  ],
                  "stop": "END",
                  "max_tokens": 128,
                  "max_completion_tokens": 64,
                  "parallel_tool_calls": true
                }
                """;
        OpenAiChatCompletionRequest request = objectMapper.readValue(json, OpenAiChatCompletionRequest.class);

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertEquals(List.of("END"), unifiedRequest.getGenerationConfig().getStopSequences());
        assertEquals(64, unifiedRequest.getGenerationConfig().getMaxOutputTokens());
        assertEquals(Boolean.TRUE, unifiedRequest.getGenerationConfig().getParallelToolCalls());
    }

    @Test
    void parse_throwsInvalidRequest_whenJsonSchemaSchemaMissing() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(message("user", "你好")));
        request.setResponseFormat(invalidJsonSchemaResponseFormat());

        GatewayException exception = assertThrows(GatewayException.class, () -> parser.parse(request));

        assertEquals("response_format.json_schema.schema is required", exception.getMessage());
        assertEquals("response_format.json_schema.schema", exception.getParam());
    }

    @Test
    void parse_throwsInvalidRequest_whenJsonSchemaNameMissing() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(message("user", "你好")));

        OpenAiChatCompletionRequest.OpenAiResponseFormat responseFormat = new OpenAiChatCompletionRequest.OpenAiResponseFormat();
        responseFormat.setType("json_schema");
        OpenAiChatCompletionRequest.JsonSchemaSpec jsonSchema = new OpenAiChatCompletionRequest.JsonSchemaSpec();
        jsonSchema.setSchema(Map.of("type", "object"));
        responseFormat.setJsonSchema(jsonSchema);
        request.setResponseFormat(responseFormat);

        GatewayException exception = assertThrows(GatewayException.class, () -> parser.parse(request));

        assertEquals("response_format.json_schema.name is required", exception.getMessage());
        assertEquals("response_format.json_schema.name", exception.getParam());
    }

    private OpenAiChatCompletionRequest.OpenAiMessage message(String role, String content) {
        OpenAiChatCompletionRequest.OpenAiMessage message = new OpenAiChatCompletionRequest.OpenAiMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private OpenAiChatCompletionRequest.OpenAiMessage multimodalMessage(String role, List<Map<String, Object>> content) {
        OpenAiChatCompletionRequest.OpenAiMessage message = new OpenAiChatCompletionRequest.OpenAiMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private OpenAiChatCompletionRequest.OpenAiMessage assistantMessageWithToolCalls() {
        OpenAiChatCompletionRequest.OpenAiMessage message = new OpenAiChatCompletionRequest.OpenAiMessage();
        message.setRole("assistant");
        message.setContent("我将调用工具");

        OpenAiChatCompletionRequest.OpenAiToolCall toolCall = new OpenAiChatCompletionRequest.OpenAiToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");

        OpenAiChatCompletionRequest.FunctionCall functionCall = new OpenAiChatCompletionRequest.FunctionCall();
        functionCall.setName("get_weather");
        functionCall.setArguments("{\"city\":\"Shanghai\"}");
        toolCall.setFunction(functionCall);

        message.setToolCalls(List.of(toolCall));
        return message;
    }

    private OpenAiChatCompletionRequest.OpenAiResponseFormat jsonSchemaResponseFormat() {
        OpenAiChatCompletionRequest.OpenAiResponseFormat responseFormat = new OpenAiChatCompletionRequest.OpenAiResponseFormat();
        responseFormat.setType("json_schema");

        OpenAiChatCompletionRequest.JsonSchemaSpec jsonSchema = new OpenAiChatCompletionRequest.JsonSchemaSpec();
        jsonSchema.setName("weather_output");
        jsonSchema.setStrict(true);
        jsonSchema.setSchema(Map.of("type", "object"));
        responseFormat.setJsonSchema(jsonSchema);
        return responseFormat;
    }

    private OpenAiChatCompletionRequest.OpenAiResponseFormat invalidJsonSchemaResponseFormat() {
        OpenAiChatCompletionRequest.OpenAiResponseFormat responseFormat = new OpenAiChatCompletionRequest.OpenAiResponseFormat();
        responseFormat.setType("json_schema");

        OpenAiChatCompletionRequest.JsonSchemaSpec jsonSchema = new OpenAiChatCompletionRequest.JsonSchemaSpec();
        jsonSchema.setName("missing-schema-test");
        responseFormat.setJsonSchema(jsonSchema);
        return responseFormat;
    }

    private OpenAiChatCompletionRequest.OpenAiTool tool(String name) {
        OpenAiChatCompletionRequest.OpenAiTool tool = new OpenAiChatCompletionRequest.OpenAiTool();
        tool.setType("function");

        OpenAiChatCompletionRequest.OpenAiTool.FunctionDef function = new OpenAiChatCompletionRequest.OpenAiTool.FunctionDef();
        function.setName(name);
        function.setDescription("查询天气");
        function.setParameters(Map.of("type", "object"));
        tool.setFunction(function);
        return tool;
    }
}
