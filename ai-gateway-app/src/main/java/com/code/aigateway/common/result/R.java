package com.code.aigateway.common.result;

import lombok.Data;

/**
 * 统一响应包装
 */
@Data
public class R<T> {

    /**
     * 标识本次请求是否成功
     */
    private boolean success;

    /**
     * 业务响应码
     */
    private String code;

    /**
     * 响应提示信息
     */
    private String message;

    /**
     * 响应数据载荷
     */
    private T data;

    private R(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构建带数据的成功响应
     */
    public static <T> R<T> ok(T data) {
        return new R<>(true, "SUCCESS", "操作成功", data);
    }

    /**
     * 构建不带数据的成功响应
     */
    public static <T> R<T> ok() {
        return new R<>(true, "SUCCESS", "操作成功", null);
    }

    /**
     * 构建失败响应，避免业务层重复拼装返回结构
     */
    public static <T> R<T> fail(String code, String message) {
        return new R<>(false, code, message, null);
    }
}
