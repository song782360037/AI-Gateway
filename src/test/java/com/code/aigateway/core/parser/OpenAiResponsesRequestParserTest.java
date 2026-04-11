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

    /** 扁平格式的工具定义（Responses API 推荐格式） */
    private OpenAiResponsesRequest.ToolDef flatFunctionTool(String name) {
        OpenAiResponsesRequest.ToolDef tool = new OpenAiResponsesRequest.ToolDef();
        tool.setType("function");
        tool.setName(name);
        tool.setDescription("扁平工具");
        tool.setParameters(Map.of("type", "object"));
        return tool;
    }

    @Test
    void parse_flatToolDef_mapsToUnifiedTool() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(message("user", "调用工具")));

        // 使用扁平格式的工具定义（无 function 嵌套）
        request.setTools(List.of(flatFunctionTool("get_weather")));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getTools());
        assertEquals(1, unifiedRequest.getTools().size());
        assertEquals("get_weather", unifiedRequest.getTools().get(0).getName());
        assertEquals("扁平工具", unifiedRequest.getTools().get(0).getDescription());
        assertEquals(Map.of("type", "object"), unifiedRequest.getTools().get(0).getInputSchema());
    }

    @Test
    void parse_nestedToolDef_mapsToUnifiedTool() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(message("user", "调用工具")));

        // 使用嵌套格式的工具定义（Chat Completions 兼容）
        request.setTools(List.of(functionTool("get_weather")));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getTools());
        assertEquals(1, unifiedRequest.getTools().size());
        assertEquals("get_weather", unifiedRequest.getTools().get(0).getName());
        assertEquals("测试工具", unifiedRequest.getTools().get(0).getDescription());
    }

    @Test
    void parse_mixedToolDef_prefersNestedFunction() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(message("user", "调用工具")));

        // 同时设置扁平字段和嵌套 function，嵌套优先
        OpenAiResponsesRequest.ToolDef tool = new OpenAiResponsesRequest.ToolDef();
        tool.setType("function");
        tool.setName("flat_name");
        tool.setDescription("扁平描述");
        OpenAiResponsesRequest.FunctionDef fn = new OpenAiResponsesRequest.FunctionDef();
        fn.setName("nested_name");
        fn.setDescription("嵌套描述");
        fn.setParameters(Map.of("type", "object"));
        tool.setFunction(fn);

        request.setTools(List.of(tool));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertEquals(1, unifiedRequest.getTools().size());
        // 嵌套 function 字段优先
        assertEquals("nested_name", unifiedRequest.getTools().get(0).getName());
        assertEquals("嵌套描述", unifiedRequest.getTools().get(0).getDescription());
    }

    @Test
    void parse_toolDefWithBlankName_isSkipped() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(message("user", "调用工具")));

        // name 为空的工具应被跳过
        OpenAiResponsesRequest.ToolDef tool = new OpenAiResponsesRequest.ToolDef();
        tool.setType("function");
        tool.setName("  ");
        tool.setDescription("无名称工具");
        request.setTools(List.of(tool));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getTools());
        assertEquals(0, unifiedRequest.getTools().size());
    }
}
