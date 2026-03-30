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
     */
    DashboardOverviewRsp getOverview();

    /**
     * 获取模型调用排行
     */
    List<ModelUsageRankRsp> getModelUsageRank();

    /**
     * 获取最近请求记录
     */
    List<RecentRequestRsp> getRecentRequests();
}
