package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.DashboardTrendRsp;
import com.code.aigateway.admin.model.rsp.ErrorSummaryRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.ProviderDistributionRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
import com.code.aigateway.admin.model.rsp.RealtimeMetricsRsp;
import com.code.aigateway.core.stats.ModelPriceTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> VALID_PERIODS = Set.of("today", "7d", "30d");

    private final RequestLogMapper requestLogMapper;
    private final RequestStatHourlyMapper requestStatHourlyMapper;
    private final DashboardCacheService dashboardCacheService;
    private final ProviderConfigMapper providerConfigMapper;
    private final ModelRedirectConfigMapper modelRedirectConfigMapper;

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

        // 缓存命中 Token
        long currentCacheTokens = requestStatHourlyMapper.sumCachedInputTokens(currentStart);
        long previousCacheTokens = requestStatHourlyMapper.sumCachedInputTokens(previousStart) - currentCacheTokens;
        rsp.setCacheTokens(new DashboardOverviewRsp.DualMetric(currentCacheTokens, previousCacheTokens));

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

        // 成功率（当前周期 / 上一周期）
        long currentSuccess = requestStatHourlyMapper.sumSuccessCount(currentStart);
        long prevSuccessTotal = requestStatHourlyMapper.sumSuccessCount(previousStart);
        long previousSuccess = Math.max(0, prevSuccessTotal - currentSuccess);
        double currentRate = currentRequests > 0 ? (double) currentSuccess / currentRequests * 100.0 : 100.0;
        double previousRate = previousRequests > 0 ? (double) previousSuccess / previousRequests * 100.0 : 100.0;
        rsp.setSuccessRate(new DashboardOverviewRsp.DualMetric(currentRate, previousRate));

        rsp.setProviderCount((int) providerConfigMapper.countList(null, null, null));
        rsp.setRedirectCount((int) modelRedirectConfigMapper.countList(null, null, null, null));

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
            // 展示用户友好的别名
            rsp.setModelName(item.aliasModel());
            // 记录真实目标模型
            rsp.setTargetModel(item.targetModel());
            rsp.setCallCount(item.callCount());
            rsp.setTokenCount(item.tokenCount());
            // 缓存 Token 统计
            rsp.setCachedTokens(item.cachedInputSum());
            // 费用估算基于真实目标模型
            double cost = ModelPriceTable.estimateCost(
                    item.targetModel(),
                    safeToInt(item.promptSum()),
                    safeToInt(item.cachedInputSum()),
                    safeToInt(item.completionSum())
            );
            rsp.setCost(round(cost));
            // 缓存节省费用
            double saved = ModelPriceTable.cacheSavedCost(
                    item.targetModel(),
                    safeToInt(item.cachedInputSum())
            );
            rsp.setCacheSavedCost(round(saved));
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

    @Override
    public DashboardTrendRsp getTrend(String period) {
        String p = normalizePeriod(period);

        DashboardTrendRsp cached = dashboardCacheService.getTrend(p);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = resolveStart(now, p);
        String pattern = "today".equals(p) ? "%H:00" : "%m-%d";

        List<RequestStatHourlyMapper.TrendPoint> raw = requestStatHourlyMapper.selectTrend(startTime, pattern);
        List<String> labels = new ArrayList<>();
        List<Long> requestCounts = new ArrayList<>();
        List<Long> tokenCounts = new ArrayList<>();
        List<Double> costs = new ArrayList<>();
        List<Double> successRates = new ArrayList<>();
        List<Double> cacheHitRates = new ArrayList<>();

        for (RequestStatHourlyMapper.TrendPoint point : raw) {
            labels.add(point.timeLabel());
            requestCounts.add(point.requestCount());
            tokenCounts.add(point.tokenCount());
            costs.add(point.cost() == null ? 0.0 : point.cost().setScale(2, RoundingMode.HALF_UP).doubleValue());
            successRates.add(round(point.successRate()));
            cacheHitRates.add(round(point.cacheHitRate()));
        }

        // 补齐缺失的时间点（today 按小时、7d/30d 按天）
        DashboardTrendRsp rsp = fillMissingPoints(p, now, labels, requestCounts, tokenCounts, costs, successRates, cacheHitRates);
        dashboardCacheService.setTrend(p, rsp);
        return rsp;
    }

    @Override
    public ProviderDistributionRsp getProviderDistribution(String period) {
        String p = normalizePeriod(period);

        ProviderDistributionRsp cached = dashboardCacheService.getProviderDistribution(p);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = resolveStart(now, p);

        List<RequestStatHourlyMapper.ProviderAgg> raw = requestStatHourlyMapper.selectProviderDistribution(startTime);
        long totalRequests = raw.stream().mapToLong(RequestStatHourlyMapper.ProviderAgg::requestCount).sum();

        ProviderDistributionRsp rsp = new ProviderDistributionRsp();
        List<ProviderDistributionRsp.Item> items = new ArrayList<>();
        for (RequestStatHourlyMapper.ProviderAgg agg : raw) {
            ProviderDistributionRsp.Item item = new ProviderDistributionRsp.Item();
            item.setProviderCode(agg.providerCode());
            item.setRequestCount(agg.requestCount());
            item.setTokenCount(agg.tokenCount());
            item.setCost(agg.cost() == null ? 0.0 : agg.cost().setScale(2, RoundingMode.HALF_UP).doubleValue());
            item.setPercent(totalRequests > 0 ? round((double) agg.requestCount() / totalRequests * 100.0) : 0.0);
            items.add(item);
        }
        rsp.setItems(items);
        dashboardCacheService.setProviderDistribution(p, rsp);
        return rsp;
    }

    @Override
    public ErrorSummaryRsp getErrorSummary(String period) {
        String p = normalizePeriod(period);

        ErrorSummaryRsp cached = dashboardCacheService.getErrorSummary(p);
        if (cached != null) {
            return cached;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = resolveStart(now, p);

        List<RequestLogMapper.ErrorAgg> raw = requestLogMapper.aggregateByError(startTime, 10);
        long totalErrors = raw.stream().mapToLong(RequestLogMapper.ErrorAgg::errorCount).sum();

        ErrorSummaryRsp rsp = new ErrorSummaryRsp();
        rsp.setTotalErrors(totalErrors);
        List<ErrorSummaryRsp.ErrorItem> items = new ArrayList<>();
        for (RequestLogMapper.ErrorAgg agg : raw) {
            ErrorSummaryRsp.ErrorItem item = new ErrorSummaryRsp.ErrorItem();
            item.setErrorCode(agg.errorCode() == null ? "UNKNOWN" : agg.errorCode());
            item.setErrorCount(agg.errorCount());
            item.setPercent(totalErrors > 0 ? round((double) agg.errorCount() / totalErrors * 100.0) : 0.0);
            items.add(item);
        }
        rsp.setItems(items);
        dashboardCacheService.setErrorSummary(p, rsp);
        return rsp;
    }

    @Override
    public RealtimeMetricsRsp getRealtimeMetrics() {
        RealtimeMetricsRsp cached = dashboardCacheService.getRealtime();
        if (cached != null) {
            return cached;
        }

        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        RequestLogMapper.RealtimeAgg agg = requestLogMapper.selectRealtime(oneMinuteAgo);

        RealtimeMetricsRsp rsp = new RealtimeMetricsRsp();
        rsp.setRpm(agg == null ? 0 : agg.totalCount());
        rsp.setTpm(agg == null ? 0 : agg.tokenSum());
        rsp.setSuccessRate(agg == null ? 100.0 : round(agg.successRate()));
        rsp.setActiveProviders(providerConfigMapper.selectAllEnabled().size());

        dashboardCacheService.setRealtime(rsp);
        return rsp;
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

    // ==================== 趋势数据补齐 ====================

    private DashboardTrendRsp fillMissingPoints(String period, LocalDateTime now,
                                                  List<String> labels, List<Long> requestCounts,
                                                  List<Long> tokenCounts, List<Double> costs,
                                                  List<Double> successRates, List<Double> cacheHitRates) {
        DashboardTrendRsp rsp = new DashboardTrendRsp();

        int count;
        LocalDate startDate = null;
        if ("today".equals(period)) {
            count = now.getHour() + 1;
        } else {
            count = "7d".equals(period) ? 7 : 30;
            startDate = now.toLocalDate().minusDays(count - 1);
        }

        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            indexMap.put(labels.get(i), i);
        }

        List<String> fullLabels = new ArrayList<>(count);
        List<Long> fullRequests = new ArrayList<>(count);
        List<Long> fullTokens = new ArrayList<>(count);
        List<Double> fullCosts = new ArrayList<>(count);
        List<Double> fullSuccess = new ArrayList<>(count);
        List<Double> fullCache = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String key = "today".equals(period)
                    ? String.format("%02d:00", i)
                    : startDate.plusDays(i).format(MONTH_DAY_FORMATTER);
            fullLabels.add(key);
            Integer idx = indexMap.get(key);
            if (idx != null) {
                fullRequests.add(requestCounts.get(idx));
                fullTokens.add(tokenCounts.get(idx));
                fullCosts.add(costs.get(idx));
                fullSuccess.add(successRates.get(idx));
                fullCache.add(cacheHitRates.get(idx));
            } else {
                fullRequests.add(0L);
                fullTokens.add(0L);
                fullCosts.add(0.0);
                fullSuccess.add(100.0);
                fullCache.add(0.0);
            }
        }

        rsp.setLabels(fullLabels);
        rsp.setRequestCounts(fullRequests);
        rsp.setTokenCounts(fullTokens);
        rsp.setCosts(fullCosts);
        rsp.setSuccessRates(fullSuccess);
        rsp.setCacheHitRates(fullCache);
        return rsp;
    }

    // ==================== 工具方法 ====================

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int safeToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
