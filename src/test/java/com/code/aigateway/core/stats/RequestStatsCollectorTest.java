package com.code.aigateway.core.stats;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.dataobject.RequestStatHourlyDO;
import com.code.aigateway.core.model.UnifiedUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestStatsCollectorTest {

    private RequestLogMapper requestLogMapper;
    private RequestStatHourlyMapper requestStatHourlyMapper;
    private RequestStatsCollector collector;

    @BeforeEach
    void setUp() {
        requestLogMapper = Mockito.mock(RequestLogMapper.class);
        requestStatHourlyMapper = Mockito.mock(RequestStatHourlyMapper.class);
        collector = new RequestStatsCollector(requestLogMapper, requestStatHourlyMapper);
    }

    @Test
    void applyUsage_withCachedInputTokens_recordsCachedTokens() throws Exception {
        RequestLogDO log = new RequestLogDO();
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(100);
        usage.setCachedInputTokens(40);
        usage.setOutputTokens(20);
        usage.setTotalTokens(120);

        invokeApplyUsage(log, usage);

        assertEquals(100, log.getPromptTokens());
        assertEquals(40, log.getCachedInputTokens());
        assertEquals(20, log.getCompletionTokens());
        assertEquals(120, log.getTotalTokens());
    }

    @Test
    void upsertHourlyStat_withCachedInputTokens_usesDiscountedCost() throws Exception {
        RequestLogDO log = new RequestLogDO();
        log.setAliasModel("gpt-4o");
        log.setTargetModel("gpt-4o");
        log.setProviderCode("openai");
        log.setStatus("SUCCESS");
        log.setPromptTokens(100_000);
        log.setCachedInputTokens(40_000);
        log.setCompletionTokens(50_000);
        log.setTotalTokens(150_000);
        log.setDurationMs(321);
        log.setCreateTime(java.time.LocalDateTime.now());
        log.setRequestId("req-1");

        invokeUpsertHourlyStat(log);

        ArgumentCaptor<RequestStatHourlyDO> statCaptor = ArgumentCaptor.forClass(RequestStatHourlyDO.class);
        Mockito.verify(requestStatHourlyMapper).upsert(statCaptor.capture());
        RequestStatHourlyDO stat = statCaptor.getValue();
        assertNotNull(stat);
        assertEquals(40_000L, stat.getCachedInputTokens());
        assertEquals(0.66, stat.getEstimatedCost().doubleValue(), 0.000001);
    }

    private void invokeApplyUsage(RequestLogDO log, UnifiedUsage usage) throws Exception {
        Method method = RequestStatsCollector.class.getDeclaredMethod("applyUsage", RequestLogDO.class, UnifiedUsage.class);
        method.setAccessible(true);
        method.invoke(collector, log, usage);
    }

    private void invokeUpsertHourlyStat(RequestLogDO log) throws Exception {
        Method method = RequestStatsCollector.class.getDeclaredMethod("upsertHourlyStat", RequestLogDO.class);
        method.setAccessible(true);
        method.invoke(collector, log);
    }
}
