package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardServiceImplTest {

    private RequestLogMapper requestLogMapper;
    private RequestStatHourlyMapper requestStatHourlyMapper;
    private DashboardCacheService dashboardCacheService;
    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        requestLogMapper = Mockito.mock(RequestLogMapper.class);
        requestStatHourlyMapper = Mockito.mock(RequestStatHourlyMapper.class);
        dashboardCacheService = Mockito.mock(DashboardCacheService.class);
        dashboardService = new DashboardServiceImpl(requestLogMapper, requestStatHourlyMapper, dashboardCacheService);
    }

    @Test
    void getOverview_whenAvgDurationIsNull_returnsZero() {
        Mockito.when(dashboardCacheService.getOverview()).thenReturn(null);
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumTotalRequestCount()).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumTotalTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumAllTotalTokens()).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumEstimatedCost(Mockito.any())).thenReturn(BigDecimal.ZERO);
        Mockito.when(requestStatHourlyMapper.sumAllEstimatedCost()).thenReturn(BigDecimal.ZERO);
        // 模拟聚合表没有任何数据时，Mapper 返回 null。
        Mockito.when(requestStatHourlyMapper.avgDurationMs(Mockito.any())).thenReturn(null);
        Mockito.when(requestLogMapper.countLastMinute(Mockito.any())).thenReturn(0L);
        Mockito.when(requestLogMapper.sumTokensLastMinute(Mockito.any())).thenReturn(0L);

        DashboardOverviewRsp response = dashboardService.getOverview();

        assertNotNull(response);
        assertEquals(0D, response.getAvgResponseMs());
        assertEquals(0D, response.getRequests().getToday());
        assertEquals(0D, response.getRequests().getTotal());
        assertEquals(0D, response.getTokens().getToday());
        assertEquals(0D, response.getTokens().getTotal());
        assertEquals(0D, response.getCost().getToday());
        assertEquals(0D, response.getCost().getTotal());
        assertEquals(0L, response.getRpm());
        assertEquals(0L, response.getTpm());
    }
}
