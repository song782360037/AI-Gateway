package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;

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
}
