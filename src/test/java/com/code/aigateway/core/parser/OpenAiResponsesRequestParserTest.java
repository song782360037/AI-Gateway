package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiResponsesRequestParserTest {

    private final OpenAiResponsesRequestParser parser = new OpenAiResponsesRequestParser();

    @Test
    void parse_mapsReasoningToGenerationConfigAndMetadata() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInstructions("你是一个助手");
        request.setInput(List.of(message("user", "你好")));
        request.setReasoning(Map.of("effort", "medium"));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getGenerationConfig());
        assertEquals("medium", unifiedRequest.getGenerationConfig().getReasoningEffort());
        assertEquals("medium", ((Map<?, ?>) unifiedRequest.getMetadata().get("openai_reasoning")).get("effort"));
    }

    @Test
    void parse_stopString_mapsToStopSequences() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInput(List.of(message("user", "你好")));
        request.setStop("DONE");

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getGenerationConfig());
        assertEquals(List.of("DONE"), unifiedRequest.getGenerationConfig().getStopSequences());
    }

    @Test
    void parse_toolChoiceObjectWithFunctionType_mapsToSpecificToolChoice() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInput(List.of(message("user", "你好")));
        request.setTools(List.of(functionTool("get_weather")));
        request.setToolChoice(Map.of("type", "function", "name", "get_weather"));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getToolChoice());
        assertEquals("specific", unifiedRequest.getToolChoice().getType());
        assertEquals("get_weather", unifiedRequest.getToolChoice().getToolName());
    }

    @Test
    void parse_toolChoiceObjectWithInvalidType_throwsInvalidRequest() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInput(List.of(message("user", "你好")));
        request.setTools(List.of(functionTool("get_weather")));
        request.setToolChoice(Map.of("type", "xxx", "name", "get_weather"));

        GatewayException ex = assertThrows(GatewayException.class, () -> parser.parse(request));

        assertEquals("tool_choice.type", ex.getParam());
    }

    @Test
    void parse_toolChoiceObjectWithUnknownName_throwsInvalidRequest() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInput(List.of(message("user", "你好")));
        request.setTools(List.of(functionTool("get_weather")));
        request.setToolChoice(Map.of("type", "function", "name", "missing_tool"));

        GatewayException ex = assertThrows(GatewayException.class, () -> parser.parse(request));

        assertEquals("tool_choice.name", ex.getParam());
    }

    @Test
    void parse_toolChoiceObjectWithBlankName_throwsInvalidRequest() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInput(List.of(message("user", "你好")));
        request.setTools(List.of(functionTool("get_weather")));
        request.setToolChoice(Map.of("type", "function", "name", " "));

        GatewayException ex = assertThrows(GatewayException.class, () -> parser.parse(request));

        assertEquals("tool_choice.name", ex.getParam());
    }

    private OpenAiResponsesRequest.InputItem message(String role, String content) {
        OpenAiResponsesRequest.InputItem item = new OpenAiResponsesRequest.InputItem();
        item.setType("message");
        item.setRole(role);
        item.setContent(content);
        return item;
    }

    private OpenAiResponsesRequest.ToolDef functionTool(String name) {
        OpenAiResponsesRequest.ToolDef tool = new OpenAiResponsesRequest.ToolDef();
        tool.setType("function");

        OpenAiResponsesRequest.FunctionDef function = new OpenAiResponsesRequest.FunctionDef();
        function.setName(name);
        function.setDescription("测试工具");
        function.setParameters(Map.of("type", "object"));
        tool.setFunction(function);
        return tool;
    }
}
