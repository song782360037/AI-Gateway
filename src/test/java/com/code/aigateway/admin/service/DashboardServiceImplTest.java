package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.RealtimeMetricsRsp;
import com.code.aigateway.core.stats.ActiveRequestTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceImplTest {

    private RequestLogMapper requestLogMapper;
    private RequestStatHourlyMapper requestStatHourlyMapper;
    private DashboardCacheService dashboardCacheService;
    private ProviderConfigMapper providerConfigMapper;
    private ModelRedirectConfigMapper modelRedirectConfigMapper;
    private ActiveRequestTracker activeRequestTracker;
    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        requestLogMapper = Mockito.mock(RequestLogMapper.class);
        requestStatHourlyMapper = Mockito.mock(RequestStatHourlyMapper.class);
        dashboardCacheService = Mockito.mock(DashboardCacheService.class);
        providerConfigMapper = Mockito.mock(ProviderConfigMapper.class);
        modelRedirectConfigMapper = Mockito.mock(ModelRedirectConfigMapper.class);
        activeRequestTracker = new ActiveRequestTracker();
        dashboardService = new DashboardServiceImpl(requestLogMapper, requestStatHourlyMapper, dashboardCacheService,
                providerConfigMapper, modelRedirectConfigMapper, activeRequestTracker);
    }

    @Test
    void getOverview_whenNoData_returnsZero() {
        // 缓存未命中
        Mockito.when(dashboardCacheService.getOverview("today")).thenReturn(null);

        // 所有聚合查询返回 0
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumSuccessCount(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumTotalTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumCachedInputTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumEstimatedCost(Mockito.any())).thenReturn(BigDecimal.ZERO);
        Mockito.when(requestStatHourlyMapper.sumDurationMs(Mockito.any())).thenReturn(0L);
        Mockito.when(providerConfigMapper.countList(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(0L);
        Mockito.when(modelRedirectConfigMapper.countList(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(0L);

        DashboardOverviewRsp response = dashboardService.getOverview("today");

        assertNotNull(response);
        // 当前周期和上一周期均为 0
        assertEquals(0D, response.getRequests().getCurrent());
        assertEquals(0D, response.getRequests().getPrevious());
        assertEquals(0D, response.getTokens().getCurrent());
        assertEquals(0D, response.getCost().getCurrent());
        assertEquals(0D, response.getAvgResponseMs().getCurrent());
        assertEquals(100D, response.getSuccessRate().getCurrent());
    }

    @Test
    void getOverview_withData_calculatesChangePercent() {
        Mockito.when(dashboardCacheService.getOverview("today")).thenReturn(null);

        // 当前周期：100 请求，上一周期（差值法）：80 请求
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.argThat(ld -> ld != null && ld.toLocalDate().equals(java.time.LocalDate.now()))))
                .thenReturn(100L);
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.argThat(ld -> ld != null && !ld.toLocalDate().equals(java.time.LocalDate.now()))))
                .thenReturn(180L); // previousStart → sum = 180, previous = 180 - 100 = 80

        Mockito.when(requestStatHourlyMapper.sumSuccessCount(Mockito.any())).thenReturn(95L);
        Mockito.when(requestStatHourlyMapper.sumTotalTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumCachedInputTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumEstimatedCost(Mockito.any())).thenReturn(BigDecimal.ZERO);
        Mockito.when(requestStatHourlyMapper.sumDurationMs(Mockito.any())).thenReturn(0L);
        Mockito.when(providerConfigMapper.countList(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(0L);
        Mockito.when(modelRedirectConfigMapper.countList(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(0L);

        DashboardOverviewRsp response = dashboardService.getOverview("today");

        assertNotNull(response);
        assertEquals(100D, response.getRequests().getCurrent());
        // 环比：(100 - 80) / 80 * 100 = 25.0%
        assertEquals(25.0, response.getRequests().getChangePercent(), 0.1);
    }

    @Test
    void getModelUsageRank_withCachedInputTokens_usesDiscountedCost() {
        Mockito.when(dashboardCacheService.getModelRank("today")).thenReturn(null);
        // aliasModel, callCount, tokenCount, promptSum, cachedInputSum, completionSum
        Mockito.when(requestLogMapper.aggregateByModel(Mockito.any(), Mockito.eq(10)))
                .thenReturn(List.of(new RequestLogMapper.ModelAggregation("gpt-4o", "gpt-4o", 3L, 150_000L, 100_000L, 40_000L, 50_000L)));

        List<ModelUsageRankRsp> result = dashboardService.getModelUsageRank("today");

        assertEquals(1, result.size());
        assertEquals("gpt-4o", result.getFirst().getModelName());
        assertEquals(0.66, result.getFirst().getCost(), 0.000001);
    }

    @Test
    void getRealtimeMetrics_whenNoData_returnsDefaults() {
        Mockito.when(dashboardCacheService.getRealtime()).thenReturn(null);
        Mockito.when(requestLogMapper.selectRealtime(Mockito.any())).thenReturn(null);
        Mockito.when(providerConfigMapper.selectAllEnabled()).thenReturn(Collections.emptyList());

        var result = dashboardService.getRealtimeMetrics();

        assertNotNull(result);
        assertEquals(0L, result.getRpm());
        assertEquals(0L, result.getTpm());
        assertEquals(100.0, result.getSuccessRate());
        assertEquals(0, result.getActiveProviders());
    }

    @Test
    void getRealtimeMetrics_withCachedAggregate_refreshesActiveRequests() {
        RealtimeMetricsRsp cached = new RealtimeMetricsRsp();
        cached.setRpm(12L);
        cached.setTpm(1200L);
        cached.setSuccessRate(99.5);
        cached.setActiveProviders(2);
        cached.setActiveRequestCount(0);
        Mockito.when(dashboardCacheService.getRealtime()).thenReturn(cached);

        activeRequestTracker.register("cid-1", "10.0.0.1", "gpt-4o", true);
        activeRequestTracker.updateRoute("cid-1", "openai", "gpt-4o-mini");

        var result = dashboardService.getRealtimeMetrics();

        assertEquals(12L, result.getRpm());
        assertEquals(1, result.getActiveRequestCount());
        assertEquals(1, result.getActiveClientCount());
        assertEquals(1, result.getActiveRequestGroups().size());
        assertEquals("openai", result.getActiveRequestGroups().getFirst().getProviderCode());
        assertEquals("gpt-4o-mini", result.getActiveRequestGroups().getFirst().getTargetModel());
    }

    @Test
    void getTrend_today_fillsMissingHours() {
        Mockito.when(dashboardCacheService.getTrend("today")).thenReturn(null);

        int currentHour = java.time.LocalDateTime.now().getHour();
        Mockito.when(requestStatHourlyMapper.selectTrend(Mockito.any(), Mockito.eq("%H:00")))
                .thenReturn(List.of(
                        new RequestStatHourlyMapper.TrendPoint("02:00", 10L, 100L, new BigDecimal("0.5"), 99.0, 10.0)
                ));

        var result = dashboardService.getTrend("today");

        assertNotNull(result);
        assertEquals(currentHour + 1, result.getLabels().size());
        int idx02 = result.getLabels().indexOf("02:00");
        assertTrue(idx02 >= 0);
        assertEquals(10L, result.getRequestCounts().get(idx02));
        assertEquals(100L, result.getTokenCounts().get(idx02));
        // 缺失小时默认值
        int idx00 = result.getLabels().indexOf("00:00");
        assertTrue(idx00 >= 0);
        assertEquals(0L, result.getRequestCounts().get(idx00));
        assertEquals(100.0, result.getSuccessRates().get(idx00));
        assertEquals(0.0, result.getCacheHitRates().get(idx00));
    }

    @Test
    void getTrend_7d_fillsMissingDays() {
        Mockito.when(dashboardCacheService.getTrend("7d")).thenReturn(null);

        String expectedFirstDay = java.time.LocalDate.now().minusDays(6)
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
        Mockito.when(requestStatHourlyMapper.selectTrend(Mockito.any(), Mockito.eq("%m-%d")))
                .thenReturn(List.of(
                        new RequestStatHourlyMapper.TrendPoint(expectedFirstDay, 5L, 50L, new BigDecimal("0.3"), 98.0, 5.0)
                ));

        var result = dashboardService.getTrend("7d");

        assertNotNull(result);
        assertEquals(7, result.getLabels().size());
        assertEquals(expectedFirstDay, result.getLabels().get(0));
        assertEquals(5L, result.getRequestCounts().get(0));
        assertEquals(100.0, result.getSuccessRates().get(1)); // 第二天默认值
    }

    @Test
    void getProviderDistribution_withData_calculatesPercent() {
        Mockito.when(dashboardCacheService.getProviderDistribution("today")).thenReturn(null);

        Mockito.when(requestStatHourlyMapper.selectProviderDistribution(Mockito.any()))
                .thenReturn(List.of(
                        new RequestStatHourlyMapper.ProviderAgg("openai", 75L, 1500L, new BigDecimal("1.5")),
                        new RequestStatHourlyMapper.ProviderAgg("azure", 25L, 500L, new BigDecimal("0.5"))
                ));

        var result = dashboardService.getProviderDistribution("today");

        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertEquals(75.0, result.getItems().get(0).getPercent(), 0.01);
        assertEquals(25.0, result.getItems().get(1).getPercent(), 0.01);
    }

    @Test
    void getErrorSummary_withData_groupsByCode() {
        Mockito.when(dashboardCacheService.getErrorSummary("today")).thenReturn(null);

        Mockito.when(requestLogMapper.aggregateByError(Mockito.any(), Mockito.eq(10)))
                .thenReturn(List.of(
                        new RequestLogMapper.ErrorAgg("TIMEOUT", 8L),
                        new RequestLogMapper.ErrorAgg("RATE_LIMIT", 2L)
                ));

        var result = dashboardService.getErrorSummary("today");

        assertNotNull(result);
        assertEquals(10L, result.getTotalErrors());
        assertEquals(2, result.getItems().size());
        assertEquals("TIMEOUT", result.getItems().get(0).getErrorCode());
        assertEquals(80.0, result.getItems().get(0).getPercent(), 0.01);
        assertEquals(20.0, result.getItems().get(1).getPercent(), 0.01);
    }
}
