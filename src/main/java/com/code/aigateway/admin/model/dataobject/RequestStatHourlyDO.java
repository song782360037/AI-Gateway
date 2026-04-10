package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请求小时统计数据对象
 */
@Data
public class RequestStatHourlyDO {

    /** 主键 */
    private Long id;

    /** 统计小时（如 2026-03-28 14:00:00） */
    private LocalDateTime statTime;

    /** 模型别名 */
    private String aliasModel;

    /** 提供商编码 */
    private String providerCode;

    /** 请求总数 */
    private Integer requestCount;

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer errorCount;

    /** 输入 Token 总数 */
    private Long promptTokens;

    /** 输入命中缓存的 Token 总数 */
    private Long cachedInputTokens;

    /** 输出 Token 总数 */
    private Long completionTokens;

    /** 总 Token 数 */
    private Long totalTokens;

    /** 总耗时（ms） */
    private Long totalDurationMs;

    /** 估算费用（USD） */
    private BigDecimal estimatedCost;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
