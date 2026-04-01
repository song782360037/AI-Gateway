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
 * <p>验证概览统计、模型排行、最近请求三个只读端点的响应格式和数据结构。</p>
 */
class DashboardControllerTest {

    private IDashboardService dashboardService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        dashboardService = Mockito.mock(IDashboardService.class);
        DashboardController controller = new DashboardController(dashboardService);
        // Dashboard 接口无 @Valid 参数，不需要注册 Validator
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new AdminExceptionHandler())
                .build();
    }

    // ==================== overview ====================

    @Test
    void overview_success() {
        // 构造概览统计数据，包含双指标（今日/累计）和实时指标
        DashboardOverviewRsp overviewRsp = new DashboardOverviewRsp();

        DashboardOverviewRsp.DualMetric requests = new DashboardOverviewRsp.DualMetric();
        requests.setToday(128);
        requests.setTotal(25600);
        overviewRsp.setRequests(requests);

        DashboardOverviewRsp.DualMetric cost = new DashboardOverviewRsp.DualMetric();
        cost.setToday(3.45);
        cost.setTotal(678.90);
        overviewRsp.setCost(cost);

        DashboardOverviewRsp.DualMetric tokens = new DashboardOverviewRsp.DualMetric();
        tokens.setToday(50000);
        tokens.setTotal(1000000);
        overviewRsp.setTokens(tokens);

        overviewRsp.setTpm(1200);
        overviewRsp.setRpm(30);
        overviewRsp.setAvgResponseMs(850.5);

        Mockito.when(dashboardService.getOverview()).thenReturn(overviewRsp);

        webTestClient.get()
                .uri("/admin/dashboard/overview")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                // 验证双指标结构：今日 / 累计请求数
                .jsonPath("$.data.requests.today").isEqualTo(128)
                .jsonPath("$.data.requests.total").isEqualTo(25600)
                // 验证费用指标
                .jsonPath("$.data.cost.today").isEqualTo(3.45)
                .jsonPath("$.data.cost.total").isEqualTo(678.90)
                // 验证实时指标
                .jsonPath("$.data.tpm").isEqualTo(1200)
                .jsonPath("$.data.rpm").isEqualTo(30)
                .jsonPath("$.data.avgResponseMs").isEqualTo(850.5);
    }

    // ==================== modelRank ====================

    @Test
    void modelRank_success() {
        // 构造模型调用排行数据
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

        Mockito.when(dashboardService.getModelUsageRank())
                .thenReturn(List.of(rank1, rank2));

        webTestClient.get()
                .uri("/admin/dashboard/model-rank")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                // 验证排行列表长度和排序
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].rank").isEqualTo(1)
                .jsonPath("$.data[0].modelName").isEqualTo("gpt-4o")
                .jsonPath("$.data[0].callCount").isEqualTo(5000)
                .jsonPath("$.data[1].modelName").isEqualTo("claude-3.5-sonnet");
    }

    // ==================== recentRequests ====================

    @Test
    void recentRequests_success() {
        // 构造最近请求记录
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

        Mockito.when(dashboardService.getRecentRequests())
                .thenReturn(List.of(req1, req2));

        webTestClient.get()
                .uri("/admin/dashboard/recent-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                // 验证第一条记录的字段
                .jsonPath("$.data[0].time").isEqualTo("14:30:25")
                .jsonPath("$.data[0].model").isEqualTo("gpt-4o")
                .jsonPath("$.data[0].status").isEqualTo("success")
                // 验证第二条记录状态
                .jsonPath("$.data[1].status").isEqualTo("error");
    }
}
