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
import java.util.Set;

/**
 * 仪表盘统计查询服务实现
 * <p>
 * 支持按时间范围（today/7d/30d）查询，并自动计算环比变化。
 * 环比计算：当前周期 vs 上一周期，通过 RequestStatHourlyMapper 的累积查询差值法实现。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> VALID_PERIODS = Set.of("today", "7d", "30d");

    private final RequestLogMapper requestLogMapper;
    private final RequestStatHourlyMapper requestStatHourlyMapper;
    private final DashboardCacheService dashboardCacheService;

    @Override
    public DashboardOverviewRsp getOverview(String period) {
        String p = normalizePeriod(period);

        DashboardOverviewRsp cached = dashboardCacheService.getOverview(p);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = resolveStart(now, p);
        LocalDateTime previousStart = resolvePreviousStart(now, p);

        DashboardOverviewRsp rsp = new DashboardOverviewRsp();

        // 请求数：当前周期 = sum(currentStart)，上一周期 = sum(previousStart) - sum(currentStart)
        long currentRequests = requestStatHourlyMapper.sumRequestCount(currentStart);
        long previousRequests = requestStatHourlyMapper.sumRequestCount(previousStart) - currentRequests;
        rsp.setRequests(new DashboardOverviewRsp.DualMetric(currentRequests, previousRequests));

        // Token 消耗
        long currentTokens = requestStatHourlyMapper.sumTotalTokens(currentStart);
        long previousTokens = requestStatHourlyMapper.sumTotalTokens(previousStart) - currentTokens;
        rsp.setTokens(new DashboardOverviewRsp.DualMetric(currentTokens, previousTokens));

        // 消费金额
        BigDecimal currentCost = safeBigDecimal(requestStatHourlyMapper.sumEstimatedCost(currentStart));
        BigDecimal prevCost = safeBigDecimal(requestStatHourlyMapper.sumEstimatedCost(previousStart)).subtract(currentCost);
        rsp.setCost(new DashboardOverviewRsp.DualMetric(
                currentCost.setScale(2, RoundingMode.HALF_UP).doubleValue(),
                prevCost.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).doubleValue()
        ));

        // 平均响应耗时（加权平均：总耗时/总请求数，使用差值法避免两个加权平均相减）
        long currentDuration = requestStatHourlyMapper.sumDurationMs(currentStart);
        double currentAvg = currentRequests > 0 ? (double) currentDuration / currentRequests : 0;
        long prevDuration = requestStatHourlyMapper.sumDurationMs(previousStart) - currentDuration;
        double previousAvg = previousRequests > 0 ? (double) prevDuration / previousRequests : 0;
        rsp.setAvgResponseMs(new DashboardOverviewRsp.DualMetric(currentAvg, previousAvg));

        dashboardCacheService.setOverview(p, rsp);
        return rsp;
    }

    @Override
    public List<ModelUsageRankRsp> getModelUsageRank(String period) {
        String p = normalizePeriod(period);

        List<ModelUsageRankRsp> cached = dashboardCacheService.getModelRank(p);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = resolveStart(now, p);

        List<RequestLogMapper.ModelAggregation> records = requestLogMapper.aggregateByModel(startTime, 10);
        List<ModelUsageRankRsp> result = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            RequestLogMapper.ModelAggregation item = records.get(i);
            ModelUsageRankRsp rsp = new ModelUsageRankRsp();
            rsp.setRank(i + 1);
            rsp.setModelName(item.aliasModel());
            rsp.setCallCount(item.callCount());
            rsp.setTokenCount(item.tokenCount());
            double cost = ModelPriceTable.estimateCost(
                    item.aliasModel(),
                    safeToInt(item.promptSum()),
                    safeToInt(item.completionSum())
            );
            rsp.setCost(round(cost));
            result.add(rsp);
        }

        dashboardCacheService.setModelRank(p, result);
        return result;
    }

    @Override
    public List<RecentRequestRsp> getRecentRequests(String period) {
        String p = normalizePeriod(period);

        List<RecentRequestRsp> cached = dashboardCacheService.getRecentRequests(p);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = resolveStart(now, p);

        List<RequestLogDO> records = requestLogMapper.selectRecent(startTime, 10);
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

        dashboardCacheService.setRecentRequests(p, result);
        return result;
    }

    // ==================== 时间范围计算 ====================

    /**
     * 根据时间范围类型计算当前周期的起始时间
     */
    private LocalDateTime resolveStart(LocalDateTime now, String period) {
        return switch (period) {
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> now.toLocalDate().atStartOfDay();
        };
    }

    /**
     * 根据时间范围类型计算上一周期的起始时间
     * <p>
     * 差值法：上一周期值 = sum(previousStart) - sum(currentStart)
     * </p>
     */
    private LocalDateTime resolvePreviousStart(LocalDateTime now, String period) {
        return switch (period) {
            case "7d" -> now.minusDays(14);
            case "30d" -> now.minusDays(60);
            default -> now.minusDays(1).toLocalDate().atStartOfDay();
        };
    }

    /**
     * 规范化 period 参数，不合法时回退为 today
     */
    private String normalizePeriod(String period) {
        return (period != null && VALID_PERIODS.contains(period)) ? period : "today";
    }

    // ==================== 工具方法 ====================

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
