# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 开发命令

- Java 21
- 构建：`mvn clean package`
- 全量测试：`mvn test`
- 单测类：`mvn -Dtest=ClassName test`
- 单测方法：`mvn -Dtest=ClassName#methodName test`
- 本地启动：`mvn spring-boot:run`
- 运行打包产物：`java -jar target/AI-Gateway-1.0-SNAPSHOT.jar`

## 仓库现状

- 单模块 Spring Boot 3.3.5 + WebFlux 项目。
- 当前没有已提交测试：`src/test/java` 为空。
- 当前没有已提交运行配置：`src/main/resources` 为空，README 提到的 `application.yml` 需要本地自行提供。
- `pom.xml` 没有独立 lint/checkstyle/spotbugs 任务，常用校验以 Maven 构建和测试为主。
- 代码里目前只有 OpenAI provider；README 提到的其他 provider 还未落地。
- `src/main/java/com/code/aigateway/provider/openai/OpenAiProviderClient.java` 仍是 stub，不会真实调用外部 API。
- `gateway.auth` 目前只定义在 `src/main/java/com/code/aigateway/config/GatewayProperties.java`，尚未接入实际鉴权逻辑。

## 核心架构

这是一个“OpenAI 兼容入口 -> 统一内部模型 -> Provider 适配 -> OpenAI 兼容输出”的网关骨架。

核心链路：
`OpenAiChatController -> ChatGatewayService -> Parser / Router / CapabilityChecker / ProviderClient -> ResponseEncoder`

关键位置：
- `src/main/java/com/code/aigateway/core/controller/OpenAiChatController.java`
  - 暴露 `POST /v1/chat/completions`
  - 根据 `request.stream` 走普通响应或 SSE
- `src/main/java/com/code/aigateway/core/service/ChatGatewayService.java`
  - 主编排流程：`parse -> route -> capability check -> select provider -> provider call -> encode`
  - 流式响应在这里手工拼接 OpenAI 风格 SSE，并追加 `[DONE]`
- `src/main/java/com/code/aigateway/core/parser/OpenAiChatRequestParser.java`
  - 把 OpenAI chat request 转成统一 `UnifiedRequest`
- `src/main/java/com/code/aigateway/core/model/*.java`
  - `UnifiedRequest` / `UnifiedResponse` / `UnifiedMessage` / `UnifiedPart` 是协议无关核心模型
- `src/main/java/com/code/aigateway/core/router/ConfigBasedModelRouter.java`
  - 基于 `gateway.modelAliases` 和 `gateway.providers` 做模型路由
- `src/main/java/com/code/aigateway/provider/ProviderClient.java`
  - Provider 抽象接口，区分 `chat()` 与 `streamChat()`
- `src/main/java/com/code/aigateway/provider/ProviderClientFactory.java`
  - 按 `ProviderType` 选择具体 provider 实现
- `src/main/java/com/code/aigateway/core/encoder/OpenAiChatResponseEncoder.java`
  - 把统一响应编码回 OpenAI 格式
- `src/main/java/com/code/aigateway/core/error/GlobalExceptionHandler.java`
  - 把异常转换成 OpenAI 风格错误响应

## 修改时容易误判的点

- 不要把 README 里的目标能力当成已实现功能；当前落地的是网关骨架，不是完整多 provider 产品。
- 真正扩展 provider 时，通常要一起看：`provider/*`、`ProviderType`、`GatewayProperties`、路由逻辑、能力检查。
- 流式输出先看 `ChatGatewayService`，不是单纯靠 DTO 序列化。
- 新增协议或 provider 时，优先围绕 `core/model` 扩展，不要让 provider 专属 DTO 穿透到 controller/service。
