package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
import com.code.aigateway.core.stats.ModelPriceTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘统计查询服务实现
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RequestLogMapper requestLogMapper;
    private final RequestStatHourlyMapper requestStatHourlyMapper;
    private final DashboardCacheService dashboardCacheService;

    @Override
    public DashboardOverviewRsp getOverview() {
        DashboardOverviewRsp cached = dashboardCacheService.getOverview();
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime lastMinute = now.minusMinutes(1);
        LocalDateTime lastHour = now.minusHours(1);

        DashboardOverviewRsp rsp = new DashboardOverviewRsp();
        rsp.setRequests(buildDualMetric(
                requestStatHourlyMapper.sumRequestCount(todayStart),
                requestStatHourlyMapper.sumTotalRequestCount()
        ));
        rsp.setTokens(buildDualMetric(
                requestStatHourlyMapper.sumTotalTokens(todayStart),
                requestStatHourlyMapper.sumAllTotalTokens()
        ));
        rsp.setCost(buildCostMetric(
                safeBigDecimal(requestStatHourlyMapper.sumEstimatedCost(todayStart)),
                safeBigDecimal(requestStatHourlyMapper.sumAllEstimatedCost())
        ));
        rsp.setRpm(requestLogMapper.countLastMinute(lastMinute));
        rsp.setTpm(requestLogMapper.sumTokensLastMinute(lastMinute));
        rsp.setAvgResponseMs(safeDouble(requestStatHourlyMapper.avgDurationMs(lastHour)));

        dashboardCacheService.setOverview(rsp);
        return rsp;
    }

    @Override
    public List<ModelUsageRankRsp> getModelUsageRank() {
        List<ModelUsageRankRsp> cached = dashboardCacheService.getModelRank();
        if (cached != null) {
            return cached;
        }

        List<RequestLogMapper.ModelAggregation> records = requestLogMapper.aggregateByModel(null, 10);
        List<ModelUsageRankRsp> result = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            RequestLogMapper.ModelAggregation item = records.get(i);
            ModelUsageRankRsp rsp = new ModelUsageRankRsp();
            rsp.setRank(i + 1);
            rsp.setModelName(item.getAliasModel());
            rsp.setCallCount(item.getCallCount());
            rsp.setTokenCount(item.getTokenCount());
            double cost = ModelPriceTable.estimateCost(
                    item.getAliasModel(),
                    safeToInt(item.getPromptSum()),
                    safeToInt(item.getCompletionSum())
            );
            rsp.setCost(round(cost));
            result.add(rsp);
        }

        dashboardCacheService.setModelRank(result);
        return result;
    }

    @Override
    public List<RecentRequestRsp> getRecentRequests() {
        List<RecentRequestRsp> cached = dashboardCacheService.getRecentRequests();
        if (cached != null) {
            return cached;
        }

        List<RequestLogDO> records = requestLogMapper.selectRecent(10);
        List<RecentRequestRsp> result = new ArrayList<>();
        for (RequestLogDO item : records) {
            RecentRequestRsp rsp = new RecentRequestRsp();
            rsp.setTime(item.getCreateTime() == null ? "--:--:--" : item.getCreateTime().format(TIME_FORMATTER));
            rsp.setModel(item.getAliasModel());
            rsp.setProvider(item.getProviderCode() == null ? "unknown" : item.getProviderCode());
            rsp.setTokens(item.getTotalTokens() == null ? 0 : item.getTotalTokens().intValue());
            rsp.setDuration(item.getDurationMs() == null ? 0 : item.getDurationMs());
            rsp.setStatus("SUCCESS".equals(item.getStatus()) ? "success" : "error");
            result.add(rsp);
        }

        dashboardCacheService.setRecentRequests(result);
        return result;
    }

    private DashboardOverviewRsp.DualMetric buildDualMetric(long today, long total) {
        DashboardOverviewRsp.DualMetric metric = new DashboardOverviewRsp.DualMetric();
        metric.setToday(today);
        metric.setTotal(total);
        return metric;
    }

    private DashboardOverviewRsp.DualMetric buildCostMetric(BigDecimal today, BigDecimal total) {
        DashboardOverviewRsp.DualMetric metric = new DashboardOverviewRsp.DualMetric();
        metric.setToday(today.setScale(2, RoundingMode.HALF_UP).doubleValue());
        metric.setTotal(total.setScale(2, RoundingMode.HALF_UP).doubleValue());
        return metric;
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0D;
    }

    private int safeToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
