package com.code.aigateway.provider;

import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.code.aigateway.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ProviderClientFactory 单元测试
 */
class ProviderClientFactoryTest {

    @Test
    void getClient_singleProvider_returnsCorrectClient() {
        ProviderClient client = mockProviderClient(ProviderType.OPENAI);
        ProviderClientFactory factory = new ProviderClientFactory(List.of(client));
        factory.init();

        assertSame(client, factory.getClient(ProviderType.OPENAI));
    }

    @Test
    void getClient_multipleProviders_returnsCorrectClient() {
        ProviderClient openai = mockProviderClient(ProviderType.OPENAI);
        ProviderClient anthropic = mockProviderClient(ProviderType.ANTHROPIC);
        ProviderClient gemini = mockProviderClient(ProviderType.GEMINI);

        ProviderClientFactory factory = new ProviderClientFactory(List.of(openai, anthropic, gemini));
        factory.init();

        assertSame(openai, factory.getClient(ProviderType.OPENAI));
        assertSame(anthropic, factory.getClient(ProviderType.ANTHROPIC));
        assertSame(gemini, factory.getClient(ProviderType.GEMINI));
    }

    @Test
    void getClient_notFound_throwsException() {
        ProviderClientFactory factory = new ProviderClientFactory(List.of());
        factory.init();

        GatewayException ex = assertThrows(GatewayException.class,
                () -> factory.getClient(ProviderType.OPENAI));
        assertTrue(ex.getMessage().contains("provider client not found"));
    }

    @Test
    void constructor_duplicateProviderType_throwsException() {
        ProviderClient client1 = mockProviderClient(ProviderType.OPENAI);
        ProviderClient client2 = mockProviderClient(ProviderType.OPENAI);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ProviderClientFactory(List.of(client1, client2)));
        assertTrue(ex.getMessage().contains("Duplicate ProviderType"));
    }

    private ProviderClient mockProviderClient(ProviderType type) {
        ProviderClient client = mock(ProviderClient.class);
        when(client.getProviderType()).thenReturn(type);
        return client;
    }
}
