# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 开发命令

- Java 21，Spring Boot 3.3.5，WebFlux（响应式）
- 构建（含前端）：`mvn clean package`
- 全量测试：`mvn test`（推荐，自动处理模块依赖顺序）
- 单测类：`mvn test -pl ai-gateway-app`（前提：SDK 已安装到本地仓库 `mvn install -pl ai-gateway-sdk -DskipTests`）
- 单测方法：`mvn -Dtest=ClassName#methodName -pl ai-gateway-app test`
- 本地启动：`mvn spring-boot:run -Plocal -pl ai-gateway-app`（使用 `application-local.yml`）
- 监控端点：`GET /actuator/health`、`GET /actuator/prometheus`
- 前端开发：进入 `ai-gateway-app/frontend-vue/` 后执行 `npm run dev`、`npm run build`、`npm run preview`
- SDK 测试：`mvn test -pl ai-gateway-sdk`
- SDK 安装到本地仓库：`mvn install -pl ai-gateway-sdk -DskipTests`（当 ai-gateway-app 单独测试找不到 SDK 类时执行）

## 核心架构

多协议 AI API 网关：接受 OpenAI / Anthropic / Gemini 等协议请求，统一内部模型处理，路由到后端 Provider，按原始协议格式返回。

### 多协议适配（策略模式）

```text
[请求协议] → ProtocolAdapter.parse() → UnifiedRequest
                                              ↓
                                         ChatGatewayService
                                         (parse→route→capability→provider call→encode)
                                              ↓
[响应协议] ← ProtocolAdapter.encode()  ← UnifiedResponse
```

| 协议 | 端点 | 流式格式 |
|------|------|---------|
| OpenAI Chat | `POST /v1/chat/completions` | SSE + `[DONE]` 终止符 |
| OpenAI Responses | `POST /v1/responses` | 命名 SSE 事件 |
| Anthropic Messages | `POST /v1/messages` | 命名 SSE（message_start, content_block_delta 等） |
| Gemini | `POST /v1beta/models/{model}:generateContent` | NDJSON（非 SSE） |

协议适配器分为两层：
- **SDK 层**（`ai-gateway-sdk/.../sdk/protocol/`）：`ProtocolAdapter.java` — 核心接口（parse/encode/buildError），依赖仅 Jackson + Lombok + SLF4J
- **App 层**（`ai-gateway-app/.../core/protocol/`）：`ProtocolAdapter.java` — Spring-aware 接口，返回 `Flux<ServerSentEvent<String>>`，通过 `AbstractSdkProtocolAdapter` 桥接 SDK 实例

关键文件：
- `ai-gateway-sdk/.../sdk/protocol/ProtocolAdapter.java` — SDK 核心接口
- `ai-gateway-sdk/.../sdk/protocol/AbstractProtocolAdapter.java` — SDK 共享基类
- `ai-gateway-app/.../core/protocol/ProtocolAdapter.java` — App 层 Spring-aware 接口
- `ai-gateway-app/.../core/protocol/AbstractSdkProtocolAdapter.java` — SDK ↔ App 桥接层（类型转换 + SSE 桥接）
- `ai-gateway-app/.../core/protocol/ProtocolResolver.java` — 从 URL 路径或 exchange 属性解析协议
- App 层四个实现：`OpenAiChatProtocolAdapter`、`OpenAiResponsesProtocolAdapter`、`AnthropicProtocolAdapter`、`GeminiProtocolAdapter`（均委托 SDK 对应类）
- `ai-gateway-sdk/.../sdk/model/UnifiedRequest.java` 等 — SDK 版协议无关模型
- `ai-gateway-app/.../core/model/UnifiedRequest.java` 等 — App 版模型（含 Spring 相关扩展字段）
- `ai-gateway-sdk/.../sdk/model/ProtocolType.java` — 协议类型枚举（唯一定义，App 直接复用）

Controller 是薄层：注入对应 ProtocolAdapter，调用 ChatGatewayService，处理 SSE/NDJSON vs JSON 响应。

### 双层模型路由

1. **`PersistentModelRouter`**（`@Primary`）：从 `RoutingSnapshotHolder`（内存 `AtomicReference`）读取，数据来源于数据库。若快照为空或别名未找到，回退到 YAML。
2. **`ConfigBasedModelRouter`**（回退）：读取 `application.yml` 中 `gateway.modelAliases` + `gateway.providers`。
3. **智能路由**：`auto_route_config` + `auto_route_candidate` 维护候选模型和评分策略，默认策略为 `SMART_SCORE`。

运行时配置刷新流程：DB → 解密 API Key（AES-256-GCM）→ 构建 `RoutingConfigSnapshot` → 原子交换到 `RoutingSnapshotHolder` → 预热 Redis。

关键点：
- `provider_config.supported_protocols` 控制 Provider 支持的协议集合。
- `model_redirect_config.match_type` 支持 `EXACT` / `GLOB` / `REGEX`。
- `supported_model` 维护对外模型列表和前端模型支持配置。
- 路由排序主要由 Provider 优先级和运行时治理状态共同决定。

关键文件：`core/router/PersistentModelRouter.java`、`core/runtime/RoutingSnapshotHolder.java`、`admin/service/RuntimeConfigRefreshService.java`

### 双层鉴权

**Layer 1 — API Key 鉴权**（`ApiKeyAuthWebFilter`，拦截 `/v1/**` 和 `/v1beta/**`）：
- 从 `Authorization: Bearer` 或 `X-Api-Key` 提取 `ak-` 前缀密钥
- SHA-256 哈希后在 `api_key_config` 表中查找，校验状态、过期、每日额度、RPM、小时限额、累计额度
- 失败时返回**调用方协议格式的 401 错误**
- 开关：`gateway.auth.enabled`

**Layer 2 — Admin Session 鉴权**（`AdminSessionAuthWebFilter` + `AdminSecurityConfig`）：
- `/admin/login`、`/admin/logout`、`/admin/bootstrap/**` 处理登录、退出和首次初始化
- 其余 `/admin/**` 需要 HttpOnly Cookie 中的会话令牌
- 原始 session token 只保存在 Cookie 中，数据库 `admin_session.session_token_hash` 保存哈希
- 修改用户名/密码等敏感操作会轮换会话，并使其他设备会话失效
- 后台写操作通过 `AdminCsrfWebFilter` 做 CSRF Cookie/Header 校验

### 统计采集（异步非阻塞）

`RequestStatsContextFilter` → Controller 设置 requestInfo → `ChatGatewayService` 调用 `RequestStatsCollector.collectSuccess/collectError` → Reactor `Sinks.Many` 批量异步刷写，写入 `request_log` + 更新 `request_stat_hourly` 聚合表。`ModelPriceTable` 提供静态价格查找用于成本估算。

请求日志已包含响应协议、请求路径、API Key 前缀、候选数、尝试数、Failover 次数、重试次数、熔断跳过、限流触发、上游状态码、上游错误类型、终止阶段、缓存 Token 等治理与归因字段。

### Provider 客户端架构

`AbstractProviderClient` 提供共享基础设施：运行时配置解析、WebClient 构建、指数退避重试、流式重试（仅首个 token 前重试）、错误码映射（429→RATE_LIMIT, 5xx→SERVER_ERROR）。

四个实现：
- `OpenAiProviderClient` — POST `/v1/chat/completions`
- `OpenAiResponsesProviderClient` — POST `/v1/responses`
- `AnthropicProviderClient` — POST `/v1/messages`（x-api-key header, 6 事件 SSE 状态机）
- `GeminiProviderClient` — POST `/v1beta/models/{model}:generateContent`（x-goog-api-key header, NDJSON）

### Admin 管理后台

Vue 3 + TypeScript + Pinia + Element Plus 前端（`frontend-vue/`），Maven 构建时自动打包到 `static/frontend-vue`。

后端 Admin 接口统一使用 `R<T>` 包装响应（`{success, code, message, data}`）。

主要功能：
- 登录/初始化：`/admin/login`、`/admin/logout`、`/admin/bootstrap/status`、`/admin/bootstrap/setup`
- 仪表盘：请求量、Token、成本、RPM/TPM、模型排行、最近请求、缓存 Token
- Provider 配置管理：Provider CRUD、启用/禁用、连通性测试、排序、支持协议
- 模型配置：智能路由、候选评分、模型支持管理
- API Key 管理：生成、状态、过期、每日额度、RPM、小时限额、累计额度
- 请求日志：链路追踪、重试/Failover/限流/错误详情
- 运行时状态：快照版本、刷新来源、同步状态、手动刷新
- 账号操作：修改用户名、修改密码、退出登录

关键前端文件：
- `frontend-vue/src/router/index.ts` — 路由与登录守卫
- `frontend-vue/src/layout/ConsoleLayout.vue` — 管理后台布局与账号操作
- `frontend-vue/src/views/login/LoginView.vue` — 登录与初始化
- `frontend-vue/src/views/dashboard/DashboardView.vue` — 仪表盘
- `frontend-vue/src/views/provider/ProviderManagementView.vue` — Provider 管理
- `frontend-vue/src/views/model-config/ModelConfigView.vue` — 模型配置入口
- `frontend-vue/src/views/api-key/ApiKeyConfigView.vue` — API Key 管理
- `frontend-vue/src/views/log/RequestLogView.vue` — 请求日志
- `frontend-vue/src/views/runtime/RuntimeStatusView.vue` — 运行时状态

### 数据库

MySQL + Flyway 迁移，MyBatis Mapper。

| 迁移 | 说明 |
|------|------|
| V1 | `provider_config`、`model_redirect_config` |
| V2 | `request_log`、`request_stat_hourly` |
| V3-V4 | `api_key_config` 与 RPM/小时限流字段 |
| V5-V8 | 路由配置精简、Provider 支持协议、路由匹配类型 |
| V9-V11 | 缓存 Token、取消数、请求链路追踪与治理字段 |
| V12 | `admin_user`、`admin_session` |
| V13-V16 | `auto_route_config`、`auto_route_candidate` 与智能评分策略 |
| V17-V18 | `supported_model` 与唯一约束修正 |

Provider API Key 使用 AES-256-GCM 加密存储（`provider_config.api_key_ciphertext` + `api_key_iv`），密钥配置在 `gateway.security.api-key-secret`。

## 修改时容易误判的点

- 新增协议或 Provider 时，通常需要同步修改：SDK 的 `ProtocolAdapter` 实现 + SDK 的 `ProtocolType` 枚举 + App 层的 `ProtocolAdapter` 实现（委托 SDK）+ Controller + Provider 客户端 + 路由配置 + Provider 支持协议。
- SDK 和 App 各自维护一套 Unified* 模型（SDK 版零 Spring 依赖，App 版含扩展字段）。新增字段时需同步两处，AbstractSdkProtocolAdapter 通过 ObjectMapper.convertValue 桥接。
- App 层的 `ErrorCode`（21个值）是 SDK `ErrorCode`（17个值）的超集。SDK 只定义协议转换所需最小集合（不含 CONFIG_* 等 Admin 专用错误码）。新增 App ErrorCode 时，若需映射到协议错误类型，同步在 SDK 添加对应枚举值。
- `ProtocolType` 在 SDK 中唯一定义，App 无独立协议枚举，直接复用。
- 流式输出逻辑在 `ChatGatewayService` 中，不是单纯靠 DTO 序列化；SSE/NDJSON 格式由 `ProtocolAdapter.encodeStreamEvent()` 和 `terminalStreamEvents()` 决定。
- 新增协议优先围绕 `core/model` 扩展，不要让 provider 专属 DTO 穿透到 controller/service 层。
- Gemini 协议使用 NDJSON 而非 SSE，其 ProtocolAdapter 的 `isSse()` 返回 false。
- Admin 鉴权当前是 HttpOnly Cookie + 数据库会话，不是 JWT；不要再引入本地 Token 存储。
- Admin 写接口要注意 CSRF Cookie/Header 机制，前端 API 调用需要保持凭据与 CSRF 头一致。
- 统计采集是异步的，`RequestStatsCollector` 使用 Reactor `Sinks`，不阻塞请求链路。
- 运行时路由配置通过 `RoutingSnapshotHolder` 的原子交换实现热更新，无需重启。
- `supported_model` 负责对外模型列表和前端模型支持，不应和 `model_redirect_config` 混淆。
- Auto Route 配置和传统模型重定向规则是两套数据结构，候选评分字段在 `auto_route_candidate`。
- `GatewayException` + `ErrorCode` 用于网关链路异常，`BizException` 用于 Admin 模块业务异常，两者的异常处理器分别处理。
- `GlobalExceptionHandler` 根据 `ProtocolResolver` 解析的协议，调用对应 `ProtocolAdapter.buildError()` 返回协议格式错误。
