package com.code.aigateway.core.parser;

/**
 * 请求解析器接口
 * <p>
 * 负责将不同格式的请求转换为统一的请求模型。
 * 支持泛型，可以处理多种输入格式。
 * </p>
 *
 * @param <T> 输入请求类型
 * @param <R> 输出的统一请求类型
 * @author sst
 */
public interface RequestParser<T, R> {

    /**
     * 解析请求
     *
     * @param request 原始请求
     * @return 统一格式的请求
     */
    R parse(T request);
}
