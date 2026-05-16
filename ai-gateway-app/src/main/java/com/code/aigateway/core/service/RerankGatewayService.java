package com.code.aigateway.core.service;

import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.protocol.ProtocolAdapter;
import com.code.aigateway.core.resilience.FailoverStrategy;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.stats.ActiveRequestTracker;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Rerank 网关服务，委托 AbstractGatewayService.executeNonStreaming 编排。
 */
@Service
public class RerankGatewayService extends AbstractGatewayService {

    public RerankGatewayService(ModelRouter modelRouter, CapabilityChecker capabilityChecker,
                                 ProviderClientFactory providerClientFactory,
                                 RequestStatsCollector requestStatsCollector,
                                 FailoverStrategy failoverStrategy,
                                 ActiveRequestTracker activeRequestTracker) {
        super(modelRouter, capabilityChecker, providerClientFactory,
              requestStatsCollector, failoverStrategy, activeRequestTracker);
    }

    public Mono<?> rerankWithStats(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        return executeNonStreaming(rawRequest, adapter, context, ProviderClient::rerank);
    }
}
