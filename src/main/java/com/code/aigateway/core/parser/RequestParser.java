package com.code.aigateway.core.parser;

public interface RequestParser<T, R> {
    R parse(T request);
}
