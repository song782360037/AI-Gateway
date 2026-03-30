package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 仪表盘概览统计响应
 */
@Data
public class DashboardOverviewRsp {

    /** 今日 / 累计请求数 */
    private DualMetric requests;

    /** 今日 / 累计消费（USD） */
    private DualMetric cost;

    /** 今日 / 累计 Token 消耗 */
    private DualMetric tokens;

    /** 每分钟 Token 数（TPM） */
    private long tpm;

    /** 每分钟请求数（RPM） */
    private long rpm;

    /** 平均响应时间（ms） */
    private double avgResponseMs;

    @Data
    public static class DualMetric {
        private double today;
        private double total;
    }
}
