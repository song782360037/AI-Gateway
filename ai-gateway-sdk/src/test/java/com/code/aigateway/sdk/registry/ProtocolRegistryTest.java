package com.code.aigateway.sdk.registry;

import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.protocol.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 协议注册表测试
 */
class ProtocolRegistryTest {

    private ObjectMapper objectMapper;
    private ProtocolAdapter openAiChatAdapter;
    private ProtocolAdapter anthropicAdapter;
    private ProtocolAdapter geminiAdapter;
    private ProtocolAdapter openAiResponsesAdapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        openAiChatAdapter = new OpenAiChatProtocolAdapter(objectMapper);
        anthropicAdapter = new AnthropicProtocolAdapter(objectMapper);
        geminiAdapter = new GeminiProtocolAdapter(objectMapper);
        openAiResponsesAdapter = new OpenAiResponsesProtocolAdapter(objectMapper);
    }

    // ===================== Builder 构建 =====================

    @Nested
    @DisplayName("Builder 构建测试")
    class BuilderTests {

        @Test
        @DisplayName("注册单个适配器")
        void builder_registerSingle() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .register(openAiChatAdapter)
                    .build();

            assertThat(registry.hasAdapter(ProtocolType.OPENAI_CHAT)).isTrue();
            assertThat(registry.getAdapter(ProtocolType.OPENAI_CHAT)).isSameAs(openAiChatAdapter);
        }

        @Test
        @DisplayName("注册所有四个适配器")
        void builder_registerAll() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .register(openAiChatAdapter)
                    .register(anthropicAdapter)
                    .register(geminiAdapter)
                    .register(openAiResponsesAdapter)
                    .build();

            assertThat(registry.getRegisteredProtocols())
                    .containsExactlyInAnyOrder(
                            ProtocolType.OPENAI_CHAT,
                            ProtocolType.ANTHROPIC,
                            ProtocolType.GEMINI,
                            ProtocolType.OPENAI_RESPONSES
                    );
        }

        @Test
        @DisplayName("registerAll 批量注册")
        void builder_registerAllBatch() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .registerAll(openAiChatAdapter, anthropicAdapter, geminiAdapter, openAiResponsesAdapter)
                    .build();

            assertThat(registry.getAllAdapters()).hasSize(4);
        }

        @Test
        @DisplayName("重复注册同一协议类型抛出 IllegalStateException")
        void builder_duplicateRegistration_throwsException() {
            assertThatThrownBy(() -> ProtocolRegistry.builder()
                            .register(openAiChatAdapter)
                            .register(openAiChatAdapter))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("duplicate adapter");
        }

        @Test
        @DisplayName("注册 null 适配器抛出 NullPointerException")
        void builder_nullAdapter_throwsNPE() {
            assertThatThrownBy(() -> ProtocolRegistry.builder().register(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("空 Builder 构建的注册表无适配器")
        void builder_empty() {
            ProtocolRegistry registry = ProtocolRegistry.builder().build();

            assertThat(registry.getRegisteredProtocols()).isEmpty();
            assertThat(registry.getAllAdapters()).isEmpty();
        }
    }

    // ===================== getAdapter 查询 =====================

    @Nested
    @DisplayName("getAdapter 查询测试")
    class GetAdapterTests {

        private ProtocolRegistry registry;

        @BeforeEach
        void initRegistry() {
            registry = ProtocolRegistry.builder()
                    .registerAll(openAiChatAdapter, anthropicAdapter, geminiAdapter, openAiResponsesAdapter)
                    .build();
        }

        @Test
        @DisplayName("按类型查找 OpenAI Chat 适配器")
        void getAdapter_openAiChat() {
            ProtocolAdapter adapter = registry.getAdapter(ProtocolType.OPENAI_CHAT);
            assertThat(adapter).isInstanceOf(OpenAiChatProtocolAdapter.class);
            assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.OPENAI_CHAT);
        }

        @Test
        @DisplayName("按类型查找 Anthropic 适配器")
        void getAdapter_anthropic() {
            ProtocolAdapter adapter = registry.getAdapter(ProtocolType.ANTHROPIC);
            assertThat(adapter).isInstanceOf(AnthropicProtocolAdapter.class);
        }

        @Test
        @DisplayName("按类型查找 Gemini 适配器")
        void getAdapter_gemini() {
            ProtocolAdapter adapter = registry.getAdapter(ProtocolType.GEMINI);
            assertThat(adapter).isInstanceOf(GeminiProtocolAdapter.class);
        }

        @Test
        @DisplayName("按类型查找 OpenAI Responses 适配器")
        void getAdapter_openAiResponses() {
            ProtocolAdapter adapter = registry.getAdapter(ProtocolType.OPENAI_RESPONSES);
            assertThat(adapter).isInstanceOf(OpenAiResponsesProtocolAdapter.class);
        }

        @Test
        @DisplayName("未注册的协议类型抛出 NoSuchElementException")
        void getAdapter_unregistered_throwsException() {
            // 只有 OpenAI Chat 的注册表
            ProtocolRegistry partial = ProtocolRegistry.builder()
                    .register(openAiChatAdapter)
                    .build();

            assertThatThrownBy(() -> partial.getAdapter(ProtocolType.ANTHROPIC))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("no adapter registered");
        }

        @Test
        @DisplayName("null protocolType 抛出 NullPointerException")
        void getAdapter_null_throwsNPE() {
            assertThatThrownBy(() -> registry.getAdapter(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ===================== 集合查询 =====================

    @Nested
    @DisplayName("集合查询测试")
    class CollectionTests {

        @Test
        @DisplayName("hasAdapter 正确判断协议是否注册")
        void hasAdapter_correctlyChecks() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .register(openAiChatAdapter)
                    .build();

            assertThat(registry.hasAdapter(ProtocolType.OPENAI_CHAT)).isTrue();
            assertThat(registry.hasAdapter(ProtocolType.ANTHROPIC)).isFalse();
        }

        @Test
        @DisplayName("getRegisteredProtocols 返回正确的协议集合")
        void getRegisteredProtocols_returnsCorrectSet() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .register(openAiChatAdapter)
                    .register(anthropicAdapter)
                    .build();

            Set<ProtocolType> protocols = registry.getRegisteredProtocols();
            assertThat(protocols).containsExactlyInAnyOrder(ProtocolType.OPENAI_CHAT, ProtocolType.ANTHROPIC);
        }

        @Test
        @DisplayName("getAllAdapters 返回正确的适配器集合")
        void getAllAdapters_returnsCorrectCollection() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .registerAll(openAiChatAdapter, anthropicAdapter)
                    .build();

            Collection<ProtocolAdapter> adapters = registry.getAllAdapters();
            assertThat(adapters).hasSize(2);
            assertThat(adapters).containsExactlyInAnyOrder(openAiChatAdapter, anthropicAdapter);
        }
    }

    // ===================== 不可变性 =====================

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("注册后 getRegisteredProtocols 不可修改")
        void immutability_protocolsUnmodifiable() {
            ProtocolRegistry registry = ProtocolRegistry.builder()
                    .register(openAiChatAdapter)
                    .build();

            Set<ProtocolType> protocols = registry.getRegisteredProtocols();

            assertThatThrownBy(() -> protocols.add(ProtocolType.ANTHROPIC))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
