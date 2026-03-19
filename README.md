# AI Gateway

一个基于 Spring Boot WebFlux 构建的 AI 服务统一网关，支持多提供商接入和智能路由。

## 功能特性

- **多提供商支持**: 支持 OpenAI、Anthropic、Gemini 等 AI 服务提供商
- **统一 API**: 提供统一的 OpenAI 兼容 API 接口
- **智能路由**: 基于配置的模型路由和别名映射
- **流式响应**: 完整支持 SSE (Server-Sent Events) 流式输出
- **能力检查**: 自动验证请求与目标模型的能力兼容性
- **监控指标**: 集成 Prometheus 和 Actuator 监控

## 技术栈

- Java 21
- Spring Boot 3.3.5
- Spring WebFlux (响应式编程)
- Micrometer (Prometheus 指标)

## 项目结构

```
src/main/java/com/code/aigateway/
├── api/                    # API 请求/响应模型
├── config/                 # 配置类
├── core/                   # 核心业务逻辑
│   ├── capability/         # 能力检查
│   ├── controller/         # 控制器
│   ├── encoder/            # 响应编码器
│   ├── error/              # 错误处理
│   ├── model/              # 统一模型
│   ├── parser/             # 请求解析器
│   ├── router/             # 模型路由
│   └── service/            # 业务服务
└── provider/               # 提供商客户端实现
    ├── anthropic/
    ├── gemini/
    └── openai/
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+

### 配置

在 `application.yml` 中配置网关：

```yaml
gateway:
  auth:
    enabled: true
    api-keys:
      - your-api-key-here
  
  model-aliases:
    gpt-4:
      provider: openai
      model: gpt-4-turbo
    claude-3:
      provider: anthropic
      model: claude-3-opus
  
  providers:
    openai:
      enabled: true
      base-url: https://api.openai.com
      api-key: sk-xxx
    anthropic:
      enabled: true
      base-url: https://api.anthropic.com
      api-key: sk-ant-xxx
    gemini:
      enabled: true
      base-url: https://generativelanguage.googleapis.com
      api-key: xxx
```

### 构建运行

```bash
# 构建
mvn clean package

# 运行
java -jar target/AI-Gateway-1.0-SNAPSHOT.jar
```

### API 使用

发送聊天请求：

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "gpt-4",
    "messages": [
      {"role": "user", "content": "Hello!"}
    ],
    "stream": false
  }'
```

流式请求：

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "gpt-4",
    "messages": [
      {"role": "user", "content": "Hello!"}
    ],
    "stream": true
  }'
```

## 监控端点

- 健康检查: `GET /actuator/health`
- Prometheus 指标: `GET /actuator/prometheus`

## License

MIT
