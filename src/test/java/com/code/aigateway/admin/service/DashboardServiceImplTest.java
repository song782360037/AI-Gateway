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
    void getOverview_whenNoData_returnsZero() {
        // 缓存未命中
        Mockito.when(dashboardCacheService.getOverview("today")).thenReturn(null);

        // 所有聚合查询返回 0
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumTotalTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumEstimatedCost(Mockito.any())).thenReturn(BigDecimal.ZERO);
        Mockito.when(requestStatHourlyMapper.sumDurationMs(Mockito.any())).thenReturn(0L);

        DashboardOverviewRsp response = dashboardService.getOverview("today");

        assertNotNull(response);
        // 当前周期和上一周期均为 0
        assertEquals(0D, response.getRequests().getCurrent());
        assertEquals(0D, response.getRequests().getPrevious());
        assertEquals(0D, response.getTokens().getCurrent());
        assertEquals(0D, response.getCost().getCurrent());
        assertEquals(0D, response.getAvgResponseMs().getCurrent());
        // tpm/rpm 已移除
    }

    @Test
    void getOverview_withData_calculatesChangePercent() {
        Mockito.when(dashboardCacheService.getOverview("today")).thenReturn(null);

        // 当前周期：100 请求，上一周期（差值法）：80 请求
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.argThat(ld -> ld != null && ld.toLocalDate().equals(java.time.LocalDate.now()))))
                .thenReturn(100L);
        Mockito.when(requestStatHourlyMapper.sumRequestCount(Mockito.argThat(ld -> ld != null && !ld.toLocalDate().equals(java.time.LocalDate.now()))))
                .thenReturn(180L); // previousStart → sum = 180, previous = 180 - 100 = 80

        Mockito.when(requestStatHourlyMapper.sumTotalTokens(Mockito.any())).thenReturn(0L);
        Mockito.when(requestStatHourlyMapper.sumEstimatedCost(Mockito.any())).thenReturn(BigDecimal.ZERO);
        Mockito.when(requestStatHourlyMapper.sumDurationMs(Mockito.any())).thenReturn(0L);

        DashboardOverviewRsp response = dashboardService.getOverview("today");

        assertNotNull(response);
        assertEquals(100D, response.getRequests().getCurrent());
        // 环比：(100 - 80) / 80 * 100 = 25.0%
        assertEquals(25.0, response.getRequests().getChangePercent(), 0.1);
    }
}
