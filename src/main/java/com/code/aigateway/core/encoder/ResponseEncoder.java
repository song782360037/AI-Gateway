package com.code.aigateway.core.encoder;

public interface ResponseEncoder<T, R> {
    R encode(T source);
}
