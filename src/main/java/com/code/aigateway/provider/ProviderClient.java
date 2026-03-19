package com.code.aigateway.provider;

import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provider client interface.
 */
public interface ProviderClient {

    ProviderType getProviderType();

    Mono<UnifiedResponse> chat(UnifiedRequest request);

    Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request);
}
