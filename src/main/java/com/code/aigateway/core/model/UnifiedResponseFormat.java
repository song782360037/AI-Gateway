package com.code.aigateway.core.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的响应格式配置
 * <p>
 * 控制输出格式，如 JSON 模式。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedResponseFormat {

    /**
     * 格式类型
     * <p>
     * 支持值：
     * <ul>
     *   <li>text: 普通文本</li>
     *   <li>json_object: JSON 对象</li>
     *   <li>json_schema: 符合指定 Schema 的 JSON</li>
     * </ul>
     * </p>
     */
    private String type;

    /**
     * JSON Schema（当 type 为 json_schema 时使用）
     */
    private Map<String, Object> schema;
}
