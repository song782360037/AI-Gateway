package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.util.List;

/**
 * 仪表盘趋势数据响应
 */
@Data
public class DashboardTrendRsp {

    /** 时间标签列表，如 ["00:00", "01:00", ...] 或 ["05-01", "05-02", ...] */
    private List<String> labels;

    /** 请求数序列 */
    private List<Long> requestCounts;

    /** Token 消耗序列 */
    private List<Long> tokenCounts;

    /** 费用序列（USD） */
    private List<Double> costs;

    /** 成功率序列（百分比，如 99.5） */
    private List<Double> successRates;

    /** 缓存命中率序列（百分比，如 45.2） */
    private List<Double> cacheHitRates;
}
