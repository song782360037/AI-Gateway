package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 仪表盘概览统计响应
 */
@Data
public class DashboardOverviewRsp {

    /** 请求数（当前周期 / 上一周期 + 环比变化） */
    private DualMetric requests;

    /** 消费金额 USD */
    private DualMetric cost;

    /** Token 消耗 */
    private DualMetric tokens;

    /** 平均响应时间（ms） */
    private DualMetric avgResponseMs;

    /**
     * 双维度指标：当前周期值、上一周期值、环比变化百分比
     */
    @Data
    public static class DualMetric {

        /** 当前周期值 */
        private double current;

        /** 上一周期值（用于计算环比） */
        private double previous;

        /** 环比变化百分比，如 +12.5 表示增长 12.5%，-3.1 表示下降 3.1% */
        private double changePercent;

        public DualMetric() {
        }

        public DualMetric(double current, double previous) {
            this.current = current;
            this.previous = previous;
            this.changePercent = calcChange(current, previous);
        }

        /**
         * 计算环比变化百分比
         */
        private static double calcChange(double current, double previous) {
            if (previous == 0) {
                return current > 0 ? 100.0 : 0.0;
            }
            return ((current - previous) / previous) * 100.0;
        }
    }
}
