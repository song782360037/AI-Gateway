package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
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
        rank1.setCallCount(5000);
        rank1.setTokenCount(2000000);
        rank1.setCost(120.50);

        ModelUsageRankRsp rank2 = new ModelUsageRankRsp();
        rank2.setRank(2);
        rank2.setModelName("claude-3.5-sonnet");
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
                .jsonPath("$.data[0].callCount").isEqualTo(5000)
                .jsonPath("$.data[1].modelName").isEqualTo("claude-3.5-sonnet");
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
