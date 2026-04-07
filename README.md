# AI Gateway

基于 Spring Boot WebFlux 的多协议 AI API 网关，支持 OpenAI / Anthropic / Gemini 等多提供商接入，统一路由、鉴权、统计与管理。

## 项目架构
<img src="img/架构图.png">

## 功能特性

### 多协议适配

接受 4 种协议请求，统一内部模型处理，按原始协议格式返回：

| 协议 | 端点 | SSE 格式 |
|------|------|---------|
| OpenAI Chat | `POST /v1/chat/completions` | SSE + `[DONE]` |
| OpenAI Responses | `POST /v1/responses` | 命名 SSE 事件 |
| Anthropic Messages | `POST /v1/messages` | 命名 SSE（message_start 等） |
| Gemini | `POST /v1beta/models/{model}:generateContent` | NDJSON |

### 双层模型路由

- **持久化路由**：数据库配置 → 内存快照原子交换，运行时热更新无需重启
- **YAML 回退**：`application.yml` 静态配置兜底

### 双层鉴权

- **API Key 鉴权**（Layer 1）：`ak-` 前缀密钥，SHA-256 哈希存储，校验状态/过期/用量
- **JWT Admin 鉴权**（Layer 2）：HMAC-SHA256，登录后签发 Token

### 管理后台

Vue 3 + Element Plus 前端，Maven 构建时自动打包：

- 仪表盘（请求量 / Token / 成本 / RPM / TPM）
- 接入通道管理（Provider CRUD）
- 模型路由规则管理
- API Key 管理（生成/限流/过期）
- 请求日志查询
- 运行时快照状态与手动刷新

### 其他

- **流式/非流式**：完整 SSE 流式 + JSON 非流式响应
- **异步统计**：Reactor `Sinks` 批量刷写请求日志 + 聚合统计
- **安全存储**：Provider API Key 使用 AES-256-GCM 加密
- **生产级防护**：限流、熔断、故障转移
- **监控**：Actuator + Prometheus 端点

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21, Spring Boot 3.3.5, WebFlux（响应式） |
| 数据库 | MySQL + Flyway 迁移 + MyBatis |
| 前端 | Vue 3, TypeScript, Element Plus, Vite |
| 构建 | Maven（前端通过 frontend-maven-plugin 集成） |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+
- MySQL 8.0+
- Node.js 18+（仅前端开发时需要）

### 配置

编辑 `src/main/resources/application-local.yml`：

```yaml
gateway:
  auth:
    enabled: true
  security:
    api-key-secret: your-aes-256-secret-key

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aigateway
    username: root
    password: your-password
```

### 构建运行

```bash
# 构建（含前端）
mvn clean package

# 本地启动
mvn spring-boot:run -Plocal

# 仅后端构建（跳过前端）
mvn clean package -Dfrontend.skip=true
```

### API 使用

**OpenAI Chat 兼容接口：**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ak-your-api-key" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'
```

**Anthropic Messages 接口：**

```bash
curl -X POST http://localhost:8080/v1/messages \
  -H "Content-Type: application/json" \
  -H "x-api-key: ak-your-api-key" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "claude-sonnet-4-20250514",
    "messages": [{"role": "user", "content": "Hello!"}],
    "max_tokens": 1024,
    "stream": true
  }'
```

## 项目结构

```
src/main/java/com/code/aigateway/
├── admin/                    # 管理后台（Controller / Service / Mapper）
├── common/                   # 通用工具（R 响应包装 / 异常定义）
├── config/                   # Spring 配置类
├── core/
│   ├── auth/                 # API Key 鉴权 WebFilter
│   ├── capability/           # 模型能力检查
│   ├── controller/           # 协议 Controller（薄层）
│   ├── error/                # 网关异常（ErrorCode / GlobalExceptionHandler）
│   ├── model/                # 统一模型（UnifiedRequest / UnifiedResponse）
│   ├── protocol/             # 协议适配器（策略模式）
│   ├── router/               # 模型路由（Persistent + ConfigBased）
│   ├── runtime/              # 运行时快照（RoutingSnapshotHolder）
│   ├── stats/                # 请求统计采集
│   └── service/              # ChatGatewayService（核心编排）
├── provider/                 # Provider 客户端
│   ├── AbstractProviderClient
│   ├── openai/               # OpenAI Chat + Responses
│   ├── anthropic/            # Anthropic Messages
│   └── gemini/               # Gemini GenerateContent
└── security/                 # JWT 鉴权 + AES 加解密

frontend-vue/                 # 管理后台前端
├── src/
│   ├── api/                  # 后端 API 调用
│   ├── components/           # 表单对话框
│   ├── layout/               # 侧边栏 + 顶栏布局
│   ├── views/                # 页面（仪表盘/通道/路由/Key/日志/运行时）
│   └── stores/               # Pinia 状态管理
```

## 数据库迁移

| 版本 | 表 | 说明 |
|------|-----|------|
| V1 | `provider_config`, `model_redirect_config` | Provider + 模型路由 |
| V2 | `request_log`, `request_stat_hourly` | 请求日志 + 聚合统计 |
| V3 | `api_key_config` | API Key 管理 |

## 监控端点

- 健康检查：`GET /actuator/health`
- Prometheus：`GET /actuator/prometheus`

## License

MIT
