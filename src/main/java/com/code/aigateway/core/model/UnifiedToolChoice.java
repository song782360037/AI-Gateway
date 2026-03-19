package com.code.aigateway.core.model;

import lombok.Data;

/**
 * 统一的工具选择配置
 * <p>
 * 控制 AI 是否以及如何使用工具。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedToolChoice {

    /**
     * 选择类型
     * <p>
     * 支持值：
     * <ul>
     *   <li>auto: 自动决定是否调用工具</li>
     *   <li>none: 不调用工具</li>
     *   <li>required: 必须调用工具</li>
     *   <li>specific: 调用指定工具</li>
     * </ul>
     * </p>
     */
    private String type;

    /**
     * 指定的工具名称
     * <p>
     * 当 type 为 specific 时使用
     * </p>
     */
    private String toolName;
}
