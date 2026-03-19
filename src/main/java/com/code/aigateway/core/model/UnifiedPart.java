package com.code.aigateway.core.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的内容部分
 * <p>
 * 表示消息中的一个内容片段，支持多种类型：
 * <ul>
 *   <li>text: 文本内容</li>
 *   <li>image: 图片内容（URL 或 Base64）</li>
 * </ul>
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedPart {

    /**
     * 内容类型
     * <p>
     * 支持的类型：text、image
     * </p>
     */
    private String type;

    /**
     * 文本内容（当 type 为 text 时使用）
     */
    private String text;

    /**
     * MIME 类型（可选）
     */
    private String mimeType;

    /**
     * 资源 URL（当 type 为 image 时使用）
     */
    private String url;

    /**
     * Base64 编码的数据（可选）
     */
    private String base64Data;

    /**
     * 扩展属性（用于存储额外信息）
     */
    private Map<String, Object> attributes;
}
