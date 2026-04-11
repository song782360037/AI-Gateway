# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 开发命令

- Java 21，Spring Boot 3.3.5，WebFlux（响应式）
- 构建（含前端）：`mvn clean package`
- 仅后端构建（跳过前端）：`mvn clean package -Dfrontend.skip=true`（当前 pom 未支持此 profile，需手动注释 frontend-maven-plugin 或确保 `frontend-vue/dist` 已存在）
- 全量测试：`mvn test`
- 单测类：`mvn -Dtest=ClassName test`
- 单测方法：`mvn -Dtest=ClassName#methodName test`
- 本地启动：`mvn spring-boot:run -Plocal`（使用 `application-local.yml`）
- 监控端点：`GET /actuator/health`、`GET /actuator/prometheus`

## 核心架构

多协议 AI API 网关：接受 4 种协议请求，统一内部模型处理，路由到后端 Provider，按原始协议格式返回。

### 多协议适配（策略模式）

```
[请求协议] → ProtocolAdapter.parse() → UnifiedRequest
                                              ↓
                                         ChatGatewayService
                                         (parse→route→capability→provider call→encode)
                                              ↓
[响应协议] ← ProtocolAdapter.encode()  ← UnifiedResponse
```

| 协议 | 端点 | SSE 格式 |
|------|------|---------|
| OpenAI Chat | `POST /v1/chat/completions` | SSE + `[DONE]` 终止符 |
| OpenAI Responses | `POST /v1/responses` | 命名 SSE 事件 |
| Anthropic Messages | `POST /v1/messages` | 命名 SSE（message_start, content_block_delta 等） |
| Gemini | `POST /v1beta/models/{model}:generateContent` | NDJSON（非 SSE） |

关键文件：
- `core/protocol/ProtocolAdapter.java` — 协议适配器接口（parse/encode/buildError）
- `core/protocol/ProtocolResolver.java` — 从 URL 路径或 exchange 属性解析协议
- 四个实现：`OpenAiChatProtocolAdapter`、`OpenAiResponsesProtocolAdapter`、`AnthropicProtocolAdapter`、`GeminiProtocolAdapter`
- `core/model/UnifiedRequest.java` / `UnifiedResponse.java` / `UnifiedStreamEvent.java` — 协议无关核心模型

Controller 是薄层：注入对应 ProtocolAdapter，调用 ChatGatewayService，处理 SSE vs JSON 响应。

### 双层模型路由

1. **`PersistentModelRouter`**（`@Primary`）：从 `RoutingSnapshotHolder`（内存 `AtomicReference`）读取，数据来源于数据库。若快照为空或别名未找到，回退到 YAML。
2. **`ConfigBasedModelRouter`**（回退）：读取 `application.yml` 中 `gateway.modelAliases` + `gateway.providers`。

运行时配置刷新流程：DB → 解密 API Key（AES-256-GCM）→ 构建 `RoutingConfigSnapshot` → 原子交换到 `RoutingSnapshotHolder` → 预热 Redis。

关键文件：`core/router/PersistentModelRouter.java`、`core/runtime/RoutingSnapshotHolder.java`、`admin/service/RuntimeConfigRefreshService.java`

### 双层鉴权

**Layer 1 — API Key 鉴权**（`ApiKeyAuthWebFilter`，拦截 `/v1/**` 和 `/v1beta/**`）：
- 从 `Authorization: Bearer` 或 `X-Api-Key` 提取 `ak-` 前缀密钥
- SHA-256 哈希后在 `api_key_config` 表中查找，校验状态/过期/用量限制
- 失败时返回**调用方协议格式的 401 错误**
- 开关：`gateway.auth.enabled`

**Layer 2 — JWT Admin 鉴权**（`JwtAuthWebFilter` + `AdminSecurityConfig`）：
- `/admin/login` 免认证，其余 `/admin/**` 需要 JWT
- `JwtUtil` 每次启动自动生成 HMAC-SHA256 密钥（重启后 token 失效）

### 统计采集（异步非阻塞）

`RequestStatsContextFilter` → Controller 设置 requestInfo → `ChatGatewayService` 调用 `RequestStatsCollector.collectSuccess/collectError` → Reactor `Sinks.Many` 批量异步刷写（100 条或 5 秒），写入 `request_log` + 更新 `request_stat_hourly` 聚合表。`ModelPriceTable` 提供静态价格查找用于成本估算。

### Provider 客户端架构

`AbstractProviderClient` 提供共享基础设施：运行时配置解析、WebClient 构建、指数退避重试、流式重试（仅首个 token 前重试）、错误码映射（429→RATE_LIMIT, 5xx→SERVER_ERROR）。

四个实现：
- `OpenAiProviderClient` — POST `/v1/chat/completions`
- `OpenAiResponsesProviderClient` — POST `/v1/responses`
- `AnthropicProviderClient` — POST `/v1/messages`（x-api-key header, 6 事件 SSE 状态机）
- `GeminiProviderClient` — POST `/v1beta/models/{model}:generateContent`（x-goog-api-key header, NDJSON）

### Admin 管理后台

Vue 3 + Element Plus 前端（`frontend-vue/`），Maven 构建时自动打包到 `static/frontend-vue`。

后端 CRUD：
- `ProviderConfigController` — Provider 配置管理
- `ModelRedirectConfigController` — 模型路由规则管理
- `ApiKeyConfigController` — API Key 管理（生成 `ak-` 前缀密钥，SHA-256 存储）
- `DashboardController` — 仪表盘（今日/总量请求、Token、成本、RPM/TPM、模型排行）
- `RuntimeConfigController` — 运行时配置状态与手动刷新

所有 Admin 接口统一使用 `R<T>` 包装响应（`{success, code, message, data}`）。

### 数据库

MySQL + Flyway 迁移，MyBatis Mapper。

| 迁移 | 表 |
|------|-----|
| V1 | `provider_config`、`model_redirect_config` |
| V2 | `request_log`、`request_stat_hourly` |
| V3 | `api_key_config` |

Provider API Key 使用 AES-256-GCM 加密存储（`provider_config.api_key_ciphertext` + `api_key_iv`），密钥配置在 `gateway.security.api-key-secret`。

## 修改时容易误判的点

- 新增协议或 Provider 时，通常需要同步修改：`ProtocolAdapter` 实现、`core/protocol/`、Controller、Parser、Encoder、`ProviderType` 枚举、`ProviderClientFactory`、`AbstractProviderClient`（认证方式）、路由配置。
- 流式输出逻辑在 `ChatGatewayService` 中，不是单纯靠 DTO 序列化；SSE 格式由 `ProtocolAdapter.encodeStreamEvent()` 和 `terminalStreamEvents()` 决定。
- 新增协议优先围绕 `core/model` 扩展，不要让 provider 专属 DTO 穿透到 controller/service 层。
- Gemini 协议使用 NDJSON 而非 SSE，其 ProtocolAdapter 的 `isSse()` 返回 false。
- 统计采集是异步的，`RequestStatsCollector` 使用 Reactor `Sinks`，不阻塞请求链路。
- 运行时路由配置通过 `RoutingSnapshotHolder` 的原子交换实现热更新，无需重启。
- `GatewayException` + `ErrorCode` 用于网关链路异常，`BizException` 用于 Admin 模块业务异常，两者的异常处理器分别处理。
- `GlobalExceptionHandler` 根据 `ProtocolResolver` 解析的协议，调用对应 `ProtocolAdapter.buildError()` 返回协议格式错误。
