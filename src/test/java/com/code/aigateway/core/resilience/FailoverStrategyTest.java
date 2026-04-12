package com.code.aigateway.core.resilience;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.RequestStatsContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FailoverStrategy shouldSkipFailover 逻辑测试
 */
class FailoverStrategyTest {

    private FailoverStrategy failoverStrategy;
    private CircuitBreakerManager circuitBreakerManager;

    @BeforeEach
    void setUp() {
        circuitBreakerManager = mock(CircuitBreakerManager.class);
        when(circuitBreakerManager.isCircuitOpen(anyString(), anyString())).thenReturn(false);
        failoverStrategy = new FailoverStrategy(circuitBreakerManager);
    }

    @Test
    void executeWithFailover_authError_skipsRemaining() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException authError = new GatewayException(ErrorCode.PROVIDER_AUTH_ERROR, "auth failed");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> Mono.error(authError),
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    assertEquals(ErrorCode.PROVIDER_AUTH_ERROR, ((GatewayException) error).getErrorCode());
                })
                .verify(Duration.ofSeconds(5));
        assertEquals(1, context.getAttemptCount());
        assertEquals(0, context.getFailoverCount());
        assertEquals("UPSTREAM", context.getTerminalStage());
    }

    @Test
    void executeWithFailover_badRequest_skipsRemaining() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException badRequest = new GatewayException(ErrorCode.PROVIDER_BAD_REQUEST, "bad request");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> Mono.error(badRequest),
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    assertEquals(ErrorCode.PROVIDER_BAD_REQUEST, ((GatewayException) error).getErrorCode());
                })
                .verify(Duration.ofSeconds(5));
        assertEquals(1, context.getAttemptCount());
        assertEquals(0, context.getFailoverCount());
        assertEquals("UPSTREAM", context.getTerminalStage());
    }

    @Test
    void executeWithFailover_resourceNotFound_skipsRemaining() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException notFound = new GatewayException(ErrorCode.PROVIDER_RESOURCE_NOT_FOUND, "not found");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> Mono.error(notFound),
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    assertEquals(ErrorCode.PROVIDER_RESOURCE_NOT_FOUND, ((GatewayException) error).getErrorCode());
                })
                .verify(Duration.ofSeconds(5));
        assertEquals(1, context.getAttemptCount());
        assertEquals(0, context.getFailoverCount());
        assertEquals("UPSTREAM", context.getTerminalStage());
    }

    @Test
    void executeWithFailover_serverError_triggersFailover() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException serverError = new GatewayException(ErrorCode.PROVIDER_SERVER_ERROR, "server error");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> {
                    if ("provider-a".equals(candidate.getProviderName())) {
                        return Mono.error(serverError);
                    }
                    return Mono.just("success-from-b");
                },
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectNext("success-from-b")
                .verifyComplete();
        assertEquals(2, context.getAttemptCount());
        assertEquals(1, context.getFailoverCount());
        assertEquals("FAILOVER", context.getTerminalStage());
    }

    @Test
    void executeWithFailover_rateLimit_triggersFailover() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException rateLimit = new GatewayException(ErrorCode.PROVIDER_RATE_LIMIT, "rate limited");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> {
                    if ("provider-a".equals(candidate.getProviderName())) {
                        return Mono.error(rateLimit);
                    }
                    return Mono.just("success-from-b");
                },
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectNext("success-from-b")
                .verifyComplete();
        assertEquals(2, context.getAttemptCount());
        assertEquals(1, context.getFailoverCount());
        assertEquals("FAILOVER", context.getTerminalStage());
    }

    @Test
    void executeWithFailover_nonGatewayException_triggersFailover() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        RuntimeException runtimeEx = new RuntimeException("connection reset");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> {
                    if ("provider-a".equals(candidate.getProviderName())) {
                        return Mono.error(runtimeEx);
                    }
                    return Mono.just("success-from-b");
                },
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectNext("success-from-b")
                .verifyComplete();
        assertEquals(2, context.getAttemptCount());
        assertEquals(1, context.getFailoverCount());
        assertEquals("FAILOVER", context.getTerminalStage());
    }

    @Test
    void executeStreamWithFailover_authError_skipsRemaining() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException authError = new GatewayException(ErrorCode.PROVIDER_AUTH_ERROR, "auth failed");

        Flux<String> result = failoverStrategy.executeStreamWithFailover(
                candidates,
                candidate -> Flux.error(authError),
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    assertEquals(ErrorCode.PROVIDER_AUTH_ERROR, ((GatewayException) error).getErrorCode());
                })
                .verify(Duration.ofSeconds(5));
        assertEquals(1, context.getAttemptCount());
        assertEquals(0, context.getFailoverCount());
        assertEquals("UPSTREAM", context.getTerminalStage());
    }

    @Test
    void executeStreamWithFailover_serverError_triggersFailover() {
        List<RouteResult> candidates = List.of(
                routeResult("provider-a", "model-x"),
                routeResult("provider-b", "model-y")
        );
        RequestStatsContext context = new RequestStatsContext();
        GatewayException serverError = new GatewayException(ErrorCode.PROVIDER_SERVER_ERROR, "server error");

        Flux<String> result = failoverStrategy.executeStreamWithFailover(
                candidates,
                candidate -> {
                    if ("provider-a".equals(candidate.getProviderName())) {
                        return Flux.error(serverError);
                    }
                    return Flux.just("token-1", "token-2");
                },
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectNext("token-1", "token-2")
                .verifyComplete();
        assertEquals(2, context.getAttemptCount());
        assertEquals(1, context.getFailoverCount());
        assertEquals("FAILOVER", context.getTerminalStage());
    }

    @Test
    void executeWithFailover_emptyCandidates() {
        RequestStatsContext context = new RequestStatsContext();
        Mono<String> result = failoverStrategy.executeWithFailover(
                List.of(),
                candidate -> Mono.just("never"),
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ((GatewayException) error).getErrorCode());
                })
                .verify(Duration.ofSeconds(5));
        assertEquals(0, context.getAttemptCount());
        assertEquals(0, context.getFailoverCount());
    }

    @Test
    void executeWithFailover_singleCandidate_authError() {
        List<RouteResult> candidates = List.of(routeResult("provider-a", "model-x"));
        RequestStatsContext context = new RequestStatsContext();
        GatewayException authError = new GatewayException(ErrorCode.PROVIDER_AUTH_ERROR, "auth failed");

        Mono<String> result = failoverStrategy.executeWithFailover(
                candidates,
                candidate -> Mono.error(authError),
                "test-correlation-id",
                context
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    assertEquals(ErrorCode.PROVIDER_AUTH_ERROR, ((GatewayException) error).getErrorCode());
                })
                .verify(Duration.ofSeconds(5));
        assertEquals(1, context.getAttemptCount());
        assertEquals(0, context.getFailoverCount());
        assertTrue(context.getTerminalStage() == null || "UPSTREAM".equals(context.getTerminalStage()));
    }

    private RouteResult routeResult(String provider, String model) {
        RouteResult rr = mock(RouteResult.class);
        when(rr.getProviderName()).thenReturn(provider);
        when(rr.getTargetModel()).thenReturn(model);
        return rr;
    }
}
