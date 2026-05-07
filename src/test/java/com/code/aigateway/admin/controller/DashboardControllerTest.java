package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.DashboardTrendRsp;
import com.code.aigateway.admin.model.rsp.ErrorSummaryRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.ProviderDistributionRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
import com.code.aigateway.admin.model.rsp.RealtimeMetricsRsp;
import com.code.aigateway.admin.service.IDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

/**
 * 仪表盘统计管理接口 WebFlux 切片测试
 *
 * <p>验证概览统计、模型排行、最近请求、健康检测四个端点的响应格式和数据结构。</p>
 */
class DashboardControllerTest {

    private IDashboardService dashboardService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        dashboardService = Mockito.mock(IDashboardService.class);
        DashboardController controller = new DashboardController(dashboardService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new AdminExceptionHandler())
                .build();
    }

    // ==================== overview ====================

    @Test
    void overview_success() {
        DashboardOverviewRsp overviewRsp = new DashboardOverviewRsp();
        overviewRsp.setRequests(new DashboardOverviewRsp.DualMetric(128, 100));
        overviewRsp.setCost(new DashboardOverviewRsp.DualMetric(3.45, 2.80));
        overviewRsp.setTokens(new DashboardOverviewRsp.DualMetric(50000, 40000));
        overviewRsp.setAvgResponseMs(new DashboardOverviewRsp.DualMetric(850.5, 780.2));

        Mockito.when(dashboardService.getOverview("today")).thenReturn(overviewRsp);

        webTestClient.get()
                .uri("/admin/dashboard/overview?period=today")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                // 验证双指标结构：当前周期 / 上一周期
                .jsonPath("$.data.requests.current").isEqualTo(128)
                .jsonPath("$.data.requests.previous").isEqualTo(100)
                .jsonPath("$.data.cost.current").isEqualTo(3.45)
                .jsonPath("$.data.cost.previous").isEqualTo(2.80)
                .jsonPath("$.data.tokens.current").isEqualTo(50000)
                .jsonPath("$.data.avgResponseMs.current").isEqualTo(850.5)
                .jsonPath("$.data.avgResponseMs.previous").isEqualTo(780.2);
    }

    @Test
    void overview_defaultPeriod() {
        DashboardOverviewRsp overviewRsp = new DashboardOverviewRsp();
        overviewRsp.setRequests(new DashboardOverviewRsp.DualMetric(0, 0));

        Mockito.when(dashboardService.getOverview("today")).thenReturn(overviewRsp);

        // 不传 period 参数，默认 today
        webTestClient.get()
                .uri("/admin/dashboard/overview")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    // ==================== modelRank ====================

    @Test
    void modelRank_success() {
        ModelUsageRankRsp rank1 = new ModelUsageRankRsp();
        rank1.setRank(1);
        rank1.setModelName("gpt-4o");
        rank1.setTargetModel("gpt-4o-2024-11-20");
        rank1.setCallCount(5000);
        rank1.setTokenCount(2000000);
        rank1.setCost(120.50);

        ModelUsageRankRsp rank2 = new ModelUsageRankRsp();
        rank2.setRank(2);
        rank2.setModelName("claude-3.5-sonnet");
        rank2.setTargetModel("claude-3-5-sonnet-20241022");
        rank2.setCallCount(3000);
        rank2.setTokenCount(1500000);
        rank2.setCost(95.00);

        Mockito.when(dashboardService.getModelUsageRank("7d"))
                .thenReturn(List.of(rank1, rank2));

        webTestClient.get()
                .uri("/admin/dashboard/model-rank?period=7d")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].rank").isEqualTo(1)
                .jsonPath("$.data[0].modelName").isEqualTo("gpt-4o")
                .jsonPath("$.data[0].targetModel").isEqualTo("gpt-4o-2024-11-20")
                .jsonPath("$.data[0].callCount").isEqualTo(5000)
                .jsonPath("$.data[1].modelName").isEqualTo("claude-3.5-sonnet")
                .jsonPath("$.data[1].targetModel").isEqualTo("claude-3-5-sonnet-20241022");
    }

    // ==================== recentRequests ====================

    @Test
    void recentRequests_success() {
        RecentRequestRsp req1 = new RecentRequestRsp();
        req1.setTime("14:30:25");
        req1.setModel("gpt-4o");
        req1.setProvider("openai-main");
        req1.setTokens(1500);
        req1.setDuration(2300);
        req1.setStatus("success");

        RecentRequestRsp req2 = new RecentRequestRsp();
        req2.setTime("14:29:10");
        req2.setModel("claude-3.5-sonnet");
        req2.setProvider("anthropic-main");
        req2.setTokens(800);
        req2.setDuration(500);
        req2.setStatus("error");

        Mockito.when(dashboardService.getRecentRequests("today"))
                .thenReturn(List.of(req1, req2));

        webTestClient.get()
                .uri("/admin/dashboard/recent-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].time").isEqualTo("14:30:25")
                .jsonPath("$.data[0].model").isEqualTo("gpt-4o")
                .jsonPath("$.data[0].status").isEqualTo("success")
                .jsonPath("$.data[1].status").isEqualTo("error");
    }

    // ==================== trend ====================

    @Test
    void trend_success() {
        DashboardTrendRsp trendRsp = new DashboardTrendRsp();
        trendRsp.setLabels(List.of("00:00", "01:00"));
        trendRsp.setRequestCounts(List.of(10L, 20L));
        trendRsp.setTokenCounts(List.of(100L, 200L));
        trendRsp.setCosts(List.of(0.5, 1.0));
        trendRsp.setSuccessRates(List.of(99.0, 98.5));
        trendRsp.setCacheHitRates(List.of(10.0, 15.0));

        Mockito.when(dashboardService.getTrend("today")).thenReturn(trendRsp);

        webTestClient.get()
                .uri("/admin/dashboard/trend?period=today")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.labels.length()").isEqualTo(2)
                .jsonPath("$.data.requestCounts[0]").isEqualTo(10)
                .jsonPath("$.data.successRates[1]").isEqualTo(98.5);
    }

    // ==================== providerDistribution ====================

    @Test
    void providerDistribution_success() {
        ProviderDistributionRsp rsp = new ProviderDistributionRsp();
        ProviderDistributionRsp.Item item = new ProviderDistributionRsp.Item();
        item.setProviderCode("openai-main");
        item.setRequestCount(500);
        item.setTokenCount(20000);
        item.setCost(12.5);
        item.setPercent(75.0);
        rsp.setItems(List.of(item));

        Mockito.when(dashboardService.getProviderDistribution("7d")).thenReturn(rsp);

        webTestClient.get()
                .uri("/admin/dashboard/provider-distribution?period=7d")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.items[0].providerCode").isEqualTo("openai-main")
                .jsonPath("$.data.items[0].percent").isEqualTo(75.0);
    }

    // ==================== errorSummary ====================

    @Test
    void errorSummary_success() {
        ErrorSummaryRsp rsp = new ErrorSummaryRsp();
        rsp.setTotalErrors(15);
        ErrorSummaryRsp.ErrorItem item = new ErrorSummaryRsp.ErrorItem();
        item.setErrorCode("TIMEOUT");
        item.setErrorCount(10);
        item.setPercent(66.7);
        rsp.setItems(List.of(item));

        Mockito.when(dashboardService.getErrorSummary("30d")).thenReturn(rsp);

        webTestClient.get()
                .uri("/admin/dashboard/error-summary?period=30d")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalErrors").isEqualTo(15)
                .jsonPath("$.data.items[0].errorCode").isEqualTo("TIMEOUT");
    }

    // ==================== realtime ====================

    @Test
    void realtime_success() {
        RealtimeMetricsRsp rsp = new RealtimeMetricsRsp();
        rsp.setRpm(42);
        rsp.setTpm(3500);
        rsp.setSuccessRate(98.5);
        rsp.setActiveProviders(3);

        Mockito.when(dashboardService.getRealtimeMetrics()).thenReturn(rsp);

        webTestClient.get()
                .uri("/admin/dashboard/realtime")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.rpm").isEqualTo(42)
                .jsonPath("$.data.tpm").isEqualTo(3500)
                .jsonPath("$.data.successRate").isEqualTo(98.5)
                .jsonPath("$.data.activeProviders").isEqualTo(3);
    }

    // ==================== health ====================

    @Test
    void health_returnsUp() {
        webTestClient.get()
                .uri("/admin/dashboard/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.status").isEqualTo("UP");
    }
}
