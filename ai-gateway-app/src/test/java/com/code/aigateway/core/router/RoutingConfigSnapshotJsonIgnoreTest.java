package com.code.aigateway.core.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 @JsonIgnore 注解在 Redis 序列化时正确排除敏感字段
 */
@DisplayName("RoutingConfigSnapshot @JsonIgnore 安全验证")
class RoutingConfigSnapshotJsonIgnoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("RouteCandidate 序列化安全")
    class RouteCandidateSerializationTest {

        @Test
        @DisplayName("providerApiKey 不参与 JSON 序列化")
        void shouldExcludeProviderApiKeyFromSerialization() throws Exception {
            // 构建含明文 API Key 的候选规则
            RouteCandidate candidate = RouteCandidate.builder()
                    .providerType("OPENAI")
                    .providerCode("openai-main")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://api.openai.com/v1")
                    .providerApiKey("sk-secret-key-should-not-appear")
                    .providerTimeoutSeconds(60)
                    .providerPriority(10)
                    .supportedProtocols(List.of("openai-chat"))
                    .build();

            String json = objectMapper.writeValueAsString(candidate);

            // API Key 不应出现在序列化结果中
            assertFalse(json.contains("sk-secret-key-should-not-appear"),
                    "providerApiKey 不应参与 JSON 序列化");
            assertFalse(json.contains("providerApiKey"),
                    "providerApiKey 字段名也不应出现在 JSON 中");
        }

        @Test
        @DisplayName("其他必要字段正常序列化")
        void shouldSerializeOtherFieldsNormally() throws Exception {
            RouteCandidate candidate = RouteCandidate.builder()
                    .providerType("OPENAI")
                    .providerCode("openai-main")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://api.openai.com/v1")
                    .providerApiKey("sk-secret")
                    .providerTimeoutSeconds(60)
                    .providerPriority(10)
                    .supportedProtocols(List.of("openai-chat"))
                    .build();

            String json = objectMapper.writeValueAsString(candidate);

            // 路由所需字段应正常序列化
            assertTrue(json.contains("openai-main"), "providerCode 应正常序列化");
            assertTrue(json.contains("gpt-4o"), "targetModel 应正常序列化");
            assertTrue(json.contains("OPENAI"), "providerType 应正常序列化");
            assertTrue(json.contains("https://api.openai.com/v1"), "providerBaseUrl 应正常序列化");
        }
    }

    @Nested
    @DisplayName("ProviderEntry 序列化安全")
    class ProviderEntrySerializationTest {

        @Test
        @DisplayName("apiKeys 不参与 JSON 序列化")
        void shouldExcludeApiKeyFromSerialization() throws Exception {
            RoutingConfigSnapshot.ProviderEntry entry = new RoutingConfigSnapshot.ProviderEntry(
                    "OPENAI", "openai-main", true,
                    "https://api.openai.com/v1",
                    List.of(new ProviderKeyEntry(1L, "sk-secret-key-should-not-appear", "sk-secr***pear", 100, 0)),
                    KeySelectionStrategy.ROUND_ROBIN,
                    60, 10, List.of("openai-chat"), java.util.Map.of(), "full"
            );

            String json = objectMapper.writeValueAsString(entry);

            assertFalse(json.contains("sk-secret-key-should-not-appear"),
                    "apiKey 不应参与 JSON 序列化");
            assertFalse(json.contains("apiKey"),
                    "apiKey 字段名也不应出现在 JSON 中");
        }

        @Test
        @DisplayName("其他字段正常序列化")
        void shouldSerializeOtherFieldsNormally() throws Exception {
            RoutingConfigSnapshot.ProviderEntry entry = new RoutingConfigSnapshot.ProviderEntry(
                    "OPENAI", "openai-main", true,
                    "https://api.openai.com/v1",
                    List.of(new ProviderKeyEntry(1L, "sk-secret", "sk-secr***ret", 100, 0)),
                    KeySelectionStrategy.ROUND_ROBIN,
                    60, 10, List.of("openai-chat"), java.util.Map.of(), "full"
            );

            String json = objectMapper.writeValueAsString(entry);

            assertTrue(json.contains("openai-main"), "providerCode 应正常序列化");
            assertTrue(json.contains("OPENAI"), "providerType 应正常序列化");
            assertTrue(json.contains("https://api.openai.com/v1"), "baseUrl 应正常序列化");
            assertTrue(json.contains("enabled"), "enabled 字段应存在");
        }
    }

    @Nested
    @DisplayName("完整快照序列化安全")
    class FullSnapshotSerializationTest {

        @Test
        @DisplayName("完整快照序列化不包含任何 API Key")
        void shouldExcludeAllApiKeysFromSnapshotSerialization() throws Exception {
            // 构建含明文 Key 的候选规则
            RouteCandidate candidate = RouteCandidate.builder()
                    .providerType("OPENAI")
                    .providerCode("openai-main")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://api.openai.com/v1")
                    .providerApiKey("sk-candidate-secret-key")
                    .providerTimeoutSeconds(60)
                    .providerPriority(10)
                    .supportedProtocols(List.of("openai-chat"))
                    .build();

            // 构建含明文 Key 的 ProviderEntry
            RoutingConfigSnapshot.ProviderEntry providerEntry = new RoutingConfigSnapshot.ProviderEntry(
                    "ANTHROPIC", "anthropic-main", true,
                    "https://api.anthropic.com",
                    List.of(new ProviderKeyEntry(1L, "sk-ant-provider-secret-key", "sk-ant-p***key", 100, 0)),
                    KeySelectionStrategy.ROUND_ROBIN,
                    60, 5, List.of("anthropic-chat"), java.util.Map.of(), "full"
            );

            RoutingConfigSnapshot snapshot = new RoutingConfigSnapshot(
                    java.util.Map.of("gpt-4o", List.of(candidate)),
                    List.of(),
                    java.util.Map.of("anthropic-main", providerEntry),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    List.of(),
                    1L,
                    "test"
            );

            String json = objectMapper.writeValueAsString(snapshot);

            // 两个来源的 API Key 都不应出现
            assertFalse(json.contains("sk-candidate-secret-key"),
                    "RouteCandidate 中的 providerApiKey 不应序列化");
            assertFalse(json.contains("sk-ant-provider-secret-key"),
                    "ProviderEntry 中的 apiKeys 不应序列化");
            assertFalse(json.contains("providerApiKey"),
                    "providerApiKey 字段名不应出现在快照 JSON 中");
            assertFalse(json.contains("apiKey"),
                    "apiKey 字段名不应出现在快照 JSON 中");
        }
    }
}
