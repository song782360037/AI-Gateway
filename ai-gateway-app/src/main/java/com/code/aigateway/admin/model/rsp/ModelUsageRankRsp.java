package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 模型调用排行响应
 */
@Data
public class ModelUsageRankRsp {

    /** 排名 */
    private int rank;

    /** 模型名称（用户请求的别名） */
    private String modelName;

    /** 实际路由到的目标模型（用于费用估算） */
    private String targetModel;

    /** 调用次数 */
    private long callCount;

    /** Token 消耗 */
    private long tokenCount;

    /** 缓存命中 Token 数 */
    private long cachedTokens;

    /** 缓存节省费用（USD） */
    private double cacheSavedCost;

    /** 估算费用（USD） */
    private double cost;
}
