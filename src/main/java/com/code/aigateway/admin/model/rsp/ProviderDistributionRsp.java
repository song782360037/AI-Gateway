package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.util.List;

/**
 * 提供商调用分布响应
 */
@Data
public class ProviderDistributionRsp {

    /** 分布数据列表 */
    private List<Item> items;

    /** 单条分布数据 */
    @Data
    public static class Item {

        /** 提供商编码 */
        private String providerCode;

        /** 调用次数 */
        private long requestCount;

        /** Token 消耗 */
        private long tokenCount;

        /** 估算费用（USD） */
        private double cost;

        /** 占比（百分比，如 35.5） */
        private double percent;
    }
}
