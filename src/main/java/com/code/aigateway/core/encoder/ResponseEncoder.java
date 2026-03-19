package com.code.aigateway.core.encoder;

/**
 * 响应编码器接口
 * <p>
 * 负责将统一的响应模型转换为特定格式的响应。
 * 支持泛型，可以输出多种响应格式。
 * </p>
 *
 * @param <T> 输入的统一响应类型
 * @param <R> 输出的目标响应类型
 * @author sst
 */
public interface ResponseEncoder<T, R> {

    /**
     * 编码响应
     *
     * @param source 统一格式的响应
     * @return 目标格式的响应
     */
    R encode(T source);
}
