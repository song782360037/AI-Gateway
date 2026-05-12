package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.DashboardTrendRsp;
import com.code.aigateway.admin.model.rsp.ErrorSummaryRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.ProviderDistributionRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
import com.code.aigateway.admin.model.rsp.RealtimeMetricsRsp;

import java.util.List;

/**
 * 仪表盘统计查询服务接口
 */
public interface IDashboardService {

    /**
     * 获取仪表盘概览统计
     *
     * @param period 时间范围：today / 7d / 30d
     */
    DashboardOverviewRsp getOverview(String period);

    /**
     * 获取模型调用排行
     *
     * @param period 时间范围：today / 7d / 30d
     */
    List<ModelUsageRankRsp> getModelUsageRank(String period);

    /**
     * 获取最近请求记录
     *
     * @param period 时间范围：today / 7d / 30d
     */
    List<RecentRequestRsp> getRecentRequests(String period);

    /**
     * 获取趋势数据（请求量、Token、费用、成功率、缓存命中率）
     *
     * @param period 时间范围：today / 7d / 30d
     */
    DashboardTrendRsp getTrend(String period);

    /**
     * 获取提供商调用分布
     *
     * @param period 时间范围：today / 7d / 30d
     */
    ProviderDistributionRsp getProviderDistribution(String period);

    /**
     * 获取错误摘要
     *
     * @param period 时间范围：today / 7d / 30d
     */
    ErrorSummaryRsp getErrorSummary(String period);

    /**
     * 获取实时指标（最近 1 分钟）
     */
    RealtimeMetricsRsp getRealtimeMetrics();
}
