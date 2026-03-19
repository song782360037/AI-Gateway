package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的响应模型
 * <p>
 * 作为不同 AI 提供商响应格式的中间表示，解耦 API 层与提供商层。
 * 包含聊天响应的所有核心要素。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedResponse {

    /**
     * 响应唯一标识
     */
    private String id;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 提供商名称
     */
    private String provider;

    /**
     * 完成原因（如 stop、length、tool_calls）
     */
    private String finishReason;

    /**
     * Token 使用统计
     */
    private UnifiedUsage usage;

    /**
     * 输出列表（通常只有一个输出）
     */
    private List<UnifiedOutput> outputs;
}
