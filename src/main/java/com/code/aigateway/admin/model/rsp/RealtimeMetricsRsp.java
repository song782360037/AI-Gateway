package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.util.List;

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

    /** 当前正在请求的总数 */
    private int activeRequestCount;

    /** 当前正在请求的唯一客户端（来源IP）数量 */
    private int activeClientCount;

    /** 当前活跃请求按提供商+模型分组的详情 */
    private List<ActiveRequestGroup> activeRequestGroups;

    /**
     * 活跃请求分组信息（按提供商+模型维度）
     */
    @Data
    public static class ActiveRequestGroup {
        /** 提供商编码 */
        private String providerCode;
        /** 目标模型 */
        private String targetModel;
        /** 该分组的请求数量 */
        private int count;
    }
}
