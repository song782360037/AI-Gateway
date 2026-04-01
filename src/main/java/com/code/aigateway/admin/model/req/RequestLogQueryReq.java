package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请求日志分页查询参数
 */
@Data
public class RequestLogQueryReq {

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 提供商类型，如 OPENAI、ANTHROPIC、GEMINI */
    private String providerType;

    /** 请求状态：SUCCESS / ERROR */
    private String status;

    /** 模型别名（模糊匹配） */
    private String aliasModel;

    /** 请求唯一标识 */
    private String requestId;

    /** 页码，从 1 开始 */
    @Min(1)
    private int page = 1;

    /** 每页大小 */
    @Min(1)
    @Max(100)
    private int pageSize = 20;
}
