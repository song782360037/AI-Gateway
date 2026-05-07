package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 实时指标响应（最近 1 分钟）
 */
@Data
public class RealtimeMetricsRsp {

    /** 最近 1 分钟请求数（RPM） */
    private long rpm;

    /** 最近 1 分钟 Token 数（TPM） */
    private long tpm;

    /** 最近 1 分钟成功率（百分比） */
    private double successRate;

    /** 活跃通道数（启用状态的提供商） */
    private int activeProviders;
}
