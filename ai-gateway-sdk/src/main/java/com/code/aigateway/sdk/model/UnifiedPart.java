package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的内容部分
 * <p>
 * 表示消息中的一个内容片段，支持多种类型：
 * text / image / thinking
 * </p>
 */
@Data
public class UnifiedPart {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_THINKING = "thinking";

    /** 内容类型：text / image / thinking */
    private String type;

    /** 文本内容（type 为 text 或 thinking 时使用） */
    private String text;

    /** MIME 类型（可选） */
    private String mimeType;

    /** 资源 URL（type 为 image 时使用） */
    private String url;

    /** Base64 编码数据（可选） */
    private String base64Data;

    /** 扩展属性（存储额外信息，如图片 detail、thinking signature 等） */
    private Map<String, Object> attributes;
}
