# AI Gateway SDK

多协议 AI API 翻译 SDK，支持 OpenAI / Anthropic / Gemini 协议的请求解析和响应编码。

## 设计原则

- **零 Spring 依赖**：仅依赖 Jackson + Lombok + SLF4J，可被任何 Java 21+ 项目使用
- **协议无关模型**：`UnifiedRequest` / `UnifiedResponse` / `UnifiedStreamEvent` 统一内部表示
- **双端对称**：`parse()` 将协议请求转为统一模型，`encode()` 将统一模型转回协议格式

## 模块结构

```
sdk/
├── error/        # ErrorCode、ProtocolException
├── model/        # Unified* 协议无关模型、ProtocolType 枚举
├── protocol/     # ProtocolAdapter 接口、四个协议实现、Parser、工具类
└── registry/     # ProtocolRegistry（不可变、线程安全）
```

## 快速接入

```java
// 1. 创建适配器
OpenAiChatProtocolAdapter adapter = new OpenAiChatProtocolAdapter(new ObjectMapper());

// 2. 解析请求
Map<String, Object> rawRequest = Map.of("model", "gpt-4", "messages", List.of(...));
UnifiedRequest unified = adapter.parse(rawRequest);

// 3. 编码响应
UnifiedResponse response = ...;
Object body = adapter.encodeResponse(response);
```

## 依赖

```xml
<dependency>
    <groupId>com.code.aigateway</groupId>
    <artifactId>ai-gateway-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 支持的协议

| 协议 | ProtocolType | 流式格式 |
|------|-------------|---------|
| OpenAI Chat | `OPENAI_CHAT` | SSE + `[DONE]` |
| OpenAI Responses | `OPENAI_RESPONSES` | 命名 SSE 事件 |
| Anthropic Messages | `ANTHROPIC` | 命名 SSE 事件 |
| Google Gemini | `GEMINI` | NDJSON（非 SSE） |
