package com.code.aigateway.core.stats;

/**
 * 统计请求信息接口
 * <p>
 * 从各协议的请求 DTO 中提取统计所需的公共字段（model、stream），
 * 解耦 RequestStatsCollector 与具体协议的请求类型。
 * </p>
 */
public interface StatsRequestInfo {

    /** 获取模型名称（别名） */
    String getModel();

    /** 是否为流式请求 */
    Boolean isStream();
}
