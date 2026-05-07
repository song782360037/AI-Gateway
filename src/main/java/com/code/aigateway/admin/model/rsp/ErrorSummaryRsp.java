package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.util.List;

/**
 * 错误摘要响应
 */
@Data
public class ErrorSummaryRsp {

    /** 总错误数 */
    private long totalErrors;

    /** 错误类型列表 */
    private List<ErrorItem> items;

    /** 单条错误数据 */
    @Data
    public static class ErrorItem {

        /** 错误码 */
        private String errorCode;

        /** 错误次数 */
        private long errorCount;

        /** 占比（百分比） */
        private double percent;
    }
}
