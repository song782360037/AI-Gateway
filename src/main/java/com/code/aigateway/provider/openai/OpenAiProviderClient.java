package com.code.aigateway.provider.openai;

import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class OpenAiProviderClient implements ProviderClient {

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }

    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText("OpenAI provider stub response for model: " + request.getModel());

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(part));

        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(10);
        usage.setOutputTokens(12);
        usage.setTotalTokens(22);

        UnifiedResponse response = new UnifiedResponse();
        response.setId("chatcmpl-" + UUID.randomUUID());
        response.setModel(request.getModel());
        response.setProvider("openai");
        response.setFinishReason("stop");
        response.setUsage(usage);
        response.setOutputs(List.of(output));

        return Mono.just(response);
    }

    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        UnifiedStreamEvent event1 = new UnifiedStreamEvent();
        event1.setType("text_delta");
        event1.setTextDelta("OpenAI ");

        UnifiedStreamEvent event2 = new UnifiedStreamEvent();
        event2.setType("text_delta");
        event2.setTextDelta("provider ");

        UnifiedStreamEvent event3 = new UnifiedStreamEvent();
        event3.setType("text_delta");
        event3.setTextDelta("stub.");

        UnifiedStreamEvent done = new UnifiedStreamEvent();
        done.setType("done");
        done.setFinishReason("stop");

        return Flux.just(event1, event2, event3, done);
    }
}
