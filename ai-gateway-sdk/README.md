# AI Gateway SDK

多协议 AI API 翻译 SDK，支持 OpenAI / Anthropic / Gemini / OpenAI Responses 协议的请求解析、响应编码、流式事件编码和错误构建。

**零 Spring 依赖** -- 仅依赖 Jackson + Lombok + SLF4J，可被任何 Java 21+ 项目独立使用。

## 适用场景

- 在自己的服务中接入多种 AI API 协议，统一内部处理逻辑
- 将一种协议格式的请求转换为另一种协议格式的响应
- 构建 AI API 网关 / 代理 / 路由层
- 需要同时兼容 OpenAI、Anthropic、Gemini 等不同客户端 SDK

## 安装

```xml
<dependency>
    <groupId>com.code.aigateway</groupId>
    <artifactId>ai-gateway-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 支持的协议

| 协议 | ProtocolType | 流式格式 | 说明 |
|------|-------------|---------|------|
| OpenAI Chat | `OPENAI_CHAT` | SSE + `[DONE]` | `/v1/chat/completions` |
| OpenAI Responses | `OPENAI_RESPONSES` | 命名 SSE 事件 | `/v1/responses` |
| Anthropic Messages | `ANTHROPIC` | 命名 SSE 事件 | `/v1/messages` |
| Google Gemini | `GEMINI` | NDJSON（非 SSE） | `/v1beta/models/{model}:generateContent` |

## 快速入门

### 方式一：AiGatewaySdk 门面（推荐）

`AiGatewaySdk` 提供一行式 API，自动注册全部四个协议适配器，适合大多数场景。

```java
import com.code.aigateway.sdk.AiGatewaySdk;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

// 1. 初始化（自动注册全部协议适配器）
ObjectMapper objectMapper = new ObjectMapper();
AiGatewaySdk sdk = new AiGatewaySdk(objectMapper);

// 2. 解析请求：JSON 字符串 → UnifiedRequest
String openAiJson = """
    {
        "model": "gpt-4",
        "messages": [
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": "Hello!"}
        ],
        "stream": true,
        "temperature": 0.7
    }""";

UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, openAiJson);
System.out.println(request.getModel());    // gpt-4
System.out.println(request.getStream());   // true

// 3. 编码响应：UnifiedResponse → JSON 字符串
UnifiedResponse response = new UnifiedResponse();
response.setId("chatcmpl-123");
response.setModel("gpt-4");
response.setCreated(System.currentTimeMillis() / 1000);
response.setOutputs(List.of(
    new UnifiedOutput("assistant", List.of(
        new UnifiedPart("text", "Hello! How can I help you today?", null, null, null, null)
    )))
);

String responseJson = sdk.encodeResponse(ProtocolType.OPENAI_CHAT, response);
// 输出 OpenAI 格式：{"id":"chatcmpl-123","object":"chat.completion","model":"gpt-4",...}

// 4. 构建错误响应
String errorJson = sdk.buildError(
    ProtocolType.OPENAI_CHAT,
    "Rate limit exceeded",
    ErrorCode.RATE_LIMITED
);
// 输出 OpenAI 格式错误：{"error":{"message":"Rate limit exceeded","type":"rate_limit_error",...}}
```

### 解析不同协议的请求

```java
// 解析 Anthropic 请求
Map<String, Object> anthropicRequest = Map.of(
    "model", "claude-sonnet-4-6",
    "max_tokens", 1024,
    "messages", List.of(
        Map.of("role", "user", "content", "Explain quantum computing")
    )
);

UnifiedRequest request = sdk.parse(ProtocolType.ANTHROPIC, anthropicRequest);
// UnifiedRequest 内部模型与协议无关，可统一处理
```

### 跨协议翻译

```java
// 接收 OpenAI 格式请求，以 Anthropic 格式返回响应
String openAiJson = "{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";

UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, openAiJson);

// 构建统一响应
UnifiedResponse response = buildResponseFromLlm(request);

// 编码为 Anthropic 格式返回
String anthropicResponse = sdk.encodeResponse(ProtocolType.ANTHROPIC, response);
// 输出 Anthropic 格式：{"id":"msg_xxx","type":"message","role":"assistant","content":[...],...}
```

---

## 高级用法

### 方式二：直接使用 ProtocolAdapter

当你需要更精细的控制时，可以直接使用底层适配器。

```java
import com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter;
import com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper objectMapper = new ObjectMapper();

// 创建单个适配器
OpenAiChatProtocolAdapter adapter = new OpenAiChatProtocolAdapter(objectMapper);

// 解析请求
Map<String, Object> rawRequest = Map.of(
    "model", "gpt-4",
    "messages", List.of(Map.of("role", "user", "content", "Hello"))
);
UnifiedRequest request = adapter.parse(rawRequest);

// 编码响应（返回可序列化的 Object，而非 JSON 字符串）
UnifiedResponse response = new UnifiedResponse();
response.setId("chatcmpl-456");
response.setModel("gpt-4");
Object body = adapter.encodeResponse(response);
// body 是 Map 结构，可用 objectMapper 序列化或直接返回
```

### 方式三：自定义 ProtocolRegistry

只注册你需要的协议适配器，或注册自定义实现。

```java
import com.code.aigateway.sdk.registry.ProtocolRegistry;
import com.code.aigateway.sdk.AiGatewaySdk;

// 只注册 OpenAI Chat 和 Anthropic 两个协议
ProtocolRegistry registry = ProtocolRegistry.builder()
    .register(new OpenAiChatProtocolAdapter(objectMapper))
    .register(new AnthropicProtocolAdapter(objectMapper))
    .build();

AiGatewaySdk sdk = new AiGatewaySdk(registry, objectMapper);

// 验证已注册的协议
System.out.println(sdk.registry().getRegisteredProtocols());
// [OPENAI_CHAT, ANTHROPIC]

// 未注册的协议会抛出 NoSuchElementException
// sdk.parse(ProtocolType.GEMINI, json); // throws!
```

### 流式事件编码

流式场景下，将 LLM 返回的文本增量（delta）编码为协议特定的 SSE/NDJSON 事件。

```java
import com.code.aigateway.sdk.protocol.*;
import com.code.aigateway.sdk.model.*;

ProtocolAdapter adapter = new OpenAiChatProtocolAdapter(objectMapper);

// 创建流式编码上下文
StreamEncodeContext context = new StreamEncodeContext();
context.setResponseId("chatcmpl-stream-1");
context.setCreated(System.currentTimeMillis() / 1000);
context.setModel("gpt-4");

// 编码文本增量
UnifiedStreamEvent textDelta = UnifiedStreamEvent.textDelta("Hello");
List<EncodedEvent> events = adapter.encodeStreamEvent(textDelta, context);
for (EncodedEvent event : events) {
    // SSE 格式输出
    if (event.eventName() != null) {
        System.out.println("event: " + event.eventName());
    }
    System.out.println("data: " + event.data());
}

// 编码流结束事件
UnifiedStreamEvent done = UnifiedStreamEvent.done("stop",
    new UnifiedUsage(10, 0, 0, 5, 15));
List<EncodedEvent> doneEvents = adapter.encodeStreamEvent(done, context);

// OpenAI Chat 还需要发送 [DONE] 终止符
List<EncodedEvent> terminal = adapter.terminalStreamEvents(context);
```

**Anthropic 流式事件示例：**

```java
ProtocolAdapter anthropicAdapter = new AnthropicProtocolAdapter(objectMapper);

StreamEncodeContext context = new StreamEncodeContext();
context.setResponseId("msg_stream_001");
context.setModel("claude-sonnet-4-6");
context.setCreated(System.currentTimeMillis() / 1000);

// Anthropic 需要先发送 message_start
List<EncodedEvent> initEvents = anthropicAdapter.initialStreamEvents(context);
// → data: {"type":"message_start","message":{...}}

// 然后发送 content_block_start + content_block_delta
UnifiedStreamEvent delta = UnifiedStreamEvent.textDelta("Hello");
List<EncodedEvent> deltaEvents = anthropicAdapter.encodeStreamEvent(delta, context);
// → data: {"type":"content_block_start",...}
// → data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"},...}
```

**Gemini 流式事件示例（NDJSON，非 SSE）：**

```java
ProtocolAdapter geminiAdapter = new GeminiProtocolAdapter(objectMapper);

StreamEncodeContext context = new StreamEncodeContext();
context.setResponseId("gemini-stream-1");
context.setModel("gemini-2.0-flash");

System.out.println(geminiAdapter.isSse()); // false → 使用 NDJSON

UnifiedStreamEvent delta = UnifiedStreamEvent.textDelta("Hello");
List<EncodedEvent> events = geminiAdapter.encodeStreamEvent(delta, context);
// 每行一个 JSON 对象，无 SSE event: 前缀
```

### 构建统一响应模型

```java
import com.code.aigateway.sdk.model.*;

// 构建包含文本输出的响应
UnifiedResponse response = new UnifiedResponse();
response.setId("resp-1");
response.setModel("gpt-4");
response.setProvider("openai");
response.setFinishReason("stop");
response.setCreated(System.currentTimeMillis() / 1000);

// 设置输出内容
UnifiedOutput output = new UnifiedOutput();
output.setRole("assistant");
output.setParts(List.of(
    new UnifiedPart("text", "The answer is 42.", null, null, null, null)
));
response.setOutputs(List.of(output));

// 设置 token 用量
UnifiedUsage usage = new UnifiedUsage();
usage.setInputTokens(25);
usage.setOutputTokens(10);
usage.setTotalTokens(35);
response.setUsage(usage);

// 提取文本
String text = response.collectText(); // "The answer is 42."
```

### 工具调用（Function Calling）

```java
// 解析包含工具定义的请求
String json = """
    {
        "model": "gpt-4",
        "messages": [{"role": "user", "content": "What's the weather in Beijing?"}],
        "tools": [
            {
                "type": "function",
                "function": {
                    "name": "get_weather",
                    "description": "Get current weather for a location",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "location": {"type": "string"}
                        },
                        "required": ["location"]
                    }
                }
            }
        ]
    }""";

UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, json);
// request.getTools() 包含工具定义
// request.getMessages() 包含对话历史
```

### 错误处理

```java
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;

try {
    UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, invalidJson);
} catch (IllegalArgumentException e) {
    // JSON 解析失败
    String error = sdk.buildError(ProtocolType.OPENAI_CHAT, e.getMessage(), ErrorCode.INVALID_REQUEST);
    // 返回协议格式的错误响应
} catch (ProtocolException e) {
    // 协议解析失败（缺少必填字段等）
    String error = sdk.buildError(ProtocolType.OPENAI_CHAT, e.getMessage(), e.getErrorCode());
}

// 自定义错误类型
String error = sdk.buildError(
    ProtocolType.ANTHROPIC,
    "upstream timeout",
    "upstream_timeout",   // errorType
    "504"                 // code
);
```

---

## 核心概念

### 架构流程

```
[协议请求 JSON/Map]
        │
        ▼
  ProtocolAdapter.parse()
        │
        ▼
  UnifiedRequest（协议无关）
        │
        ▼
  ┌─────────────────┐
  │  你的业务逻辑     │  ← 路由、调用 LLM、处理工具调用等
  └─────────────────┘
        │
        ▼
  UnifiedResponse / UnifiedStreamEvent
        │
        ▼
  ProtocolAdapter.encodeResponse() / encodeStreamEvent()
        │
        ▼
[协议响应 JSON]
```

### 统一模型

SDK 使用协议无关的统一模型，所有协议的请求和响应都映射到同一套结构：

| 模型 | 说明 |
|------|------|
| `UnifiedRequest` | 统一请求：model、messages、tools、generationConfig、stream 等 |
| `UnifiedResponse` | 统一响应：id、model、outputs、usage、finishReason |
| `UnifiedStreamEvent` | 流式事件：textDelta、thinkingDelta、toolCall、done 等 |
| `UnifiedMessage` | 对话消息：role、parts、toolCalls |
| `UnifiedPart` | 内容片段：text / image / thinking |
| `UnifiedTool` | 工具定义：name、description、inputSchema |
| `UnifiedToolCall` | 工具调用：id、toolName、argumentsJson |
| `UnifiedGenerationConfig` | 生成参数：temperature、topP、maxOutputTokens、reasoning 等 |
| `UnifiedUsage` | Token 用量：inputTokens、outputTokens、cachedInputTokens |
| `ProtocolType` | 协议枚举：OPENAI_CHAT、OPENAI_RESPONSES、ANTHROPIC、GEMINI |

### 错误码

`ErrorCode` 枚举涵盖常见网关错误场景：

| 分类 | 错误码 |
|------|--------|
| 请求错误 | `INVALID_REQUEST`, `AUTH_FAILED` |
| 路由错误 | `MODEL_NOT_FOUND`, `PROVIDER_NOT_FOUND`, `PROVIDER_DISABLED`, `CAPABILITY_NOT_SUPPORTED` |
| 限流 | `RATE_LIMITED`, `PROVIDER_RATE_LIMIT` |
| 上游错误 | `PROVIDER_TIMEOUT`, `PROVIDER_AUTH_ERROR`, `PROVIDER_BAD_REQUEST`, `PROVIDER_SERVER_ERROR`, `PROVIDER_ERROR` |
| 熔断 | `PROVIDER_CIRCUIT_OPEN` |
| 配置错误 | `CONFIG_NOT_FOUND`, `CONFIG_CONFLICT`, `CONFIG_CONCURRENT_MODIFIED`, `CONFIG_REFRESH_FAILED` |
| 内部错误 | `STREAM_PARSE_ERROR`, `INTERNAL_ERROR` |

每个 `ErrorCode` 会被 `ProtocolAdapter.mapErrorType()` 自动映射为对应协议的错误类型字符串（如 OpenAI 的 `invalid_request_error`、Anthropic 的 `authentication_error`）。

## 设计原则

- **零 Spring 依赖**：仅 Jackson + Lombok + SLF4J，可嵌入任何 Java 应用
- **协议无关模型**：`UnifiedRequest` / `UnifiedResponse` / `UnifiedStreamEvent` 统一内部表示
- **双端对称**：`parse()` 将协议请求转为统一模型，`encode()` 将统一模型转回协议格式
- **不可变注册表**：`ProtocolRegistry` 使用 Builder 构建后不可变，线程安全
- **流式兼容**：统一抽象 SSE（OpenAI/Anthropic）和 NDJSON（Gemini）两种流式格式

## 模块结构

```
sdk/
├── AiGatewaySdk.java        # 门面类（一行式 API）
├── error/
│   ├── ErrorCode.java        # 错误码枚举
│   └── ProtocolException.java # 协议异常
├── model/
│   ├── ProtocolType.java     # 协议类型枚举
│   ├── UnifiedRequest.java   # 统一请求模型
│   ├── UnifiedResponse.java  # 统一响应模型
│   ├── UnifiedStreamEvent.java # 统一流式事件
│   ├── UnifiedMessage.java   # 消息模型
│   ├── UnifiedPart.java      # 内容片段
│   ├── UnifiedTool.java      # 工具定义
│   ├── UnifiedToolCall.java  # 工具调用
│   ├── UnifiedUsage.java     # Token 用量
│   └── ...
├── protocol/
│   ├── ProtocolAdapter.java  # 核心接口
│   ├── AbstractProtocolAdapter.java # 共享基类
│   ├── OpenAiChatProtocolAdapter.java
│   ├── OpenAiResponsesProtocolAdapter.java
│   ├── AnthropicProtocolAdapter.java
│   ├── GeminiProtocolAdapter.java
│   ├── EncodedEvent.java     # 编码事件（record）
│   ├── StreamEncodeContext.java # 流式编码上下文
│   └── ProtocolUtils.java    # 工具方法
└── registry/
    └── ProtocolRegistry.java # 不可变协议注册表
```
