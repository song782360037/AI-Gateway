package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 模型调用排行响应
 */
@Data
public class ModelUsageRankRsp {

    /** 排名 */
    private int rank;

    /** 模型名称 */
    private String modelName;

    /** 调用次数 */
    private long callCount;

    /** Token 消耗 */
    private long tokenCount;

    /** 估算费用（USD） */
    private double cost;
}
