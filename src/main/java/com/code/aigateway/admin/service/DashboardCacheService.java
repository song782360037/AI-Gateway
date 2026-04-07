package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
import com.code.aigateway.core.runtime.CacheConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 仪表盘 Redis 缓存服务
 * <p>
 * 缓存键包含 period 维度，不同时间范围独立缓存。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardOverviewRsp getOverview(String period) {
        return get(buildKey(CacheConstants.KEY_DASHBOARD_OVERVIEW, period), DashboardOverviewRsp.class);
    }

    public void setOverview(String period, DashboardOverviewRsp value) {
        set(buildKey(CacheConstants.KEY_DASHBOARD_OVERVIEW, period), value);
    }

    public List<ModelUsageRankRsp> getModelRank(String period) {
        return get(buildKey(CacheConstants.KEY_DASHBOARD_MODEL_RANK, period), new TypeReference<>() {});
    }

    public void setModelRank(String period, List<ModelUsageRankRsp> value) {
        set(buildKey(CacheConstants.KEY_DASHBOARD_MODEL_RANK, period), value);
    }

    public List<RecentRequestRsp> getRecentRequests(String period) {
        return get(buildKey(CacheConstants.KEY_DASHBOARD_RECENT_REQUESTS, period), new TypeReference<>() {});
    }

    public void setRecentRequests(String period, List<RecentRequestRsp> value) {
        set(buildKey(CacheConstants.KEY_DASHBOARD_RECENT_REQUESTS, period), value);
    }

    /**
     * 构建带 period 维度的缓存键，如 gateway:dashboard:overview:today
     */
    private String buildKey(String baseKey, String period) {
        return baseKey + ":" + period;
    }

    private <T> T get(String key, Class<T> clazz) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            log.warn("[仪表盘缓存] 读取缓存失败，key: {}", key, ex);
            return null;
        }
    }

    private <T> T get(String key, TypeReference<T> typeReference) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            log.warn("[仪表盘缓存] 读取缓存失败，key: {}", key, ex);
            return null;
        }
    }

    private void set(String key, Object value) {
        try {
            long ttl = CacheConstants.TTL_DASHBOARD + ThreadLocalRandom.current().nextInt(CacheConstants.TTL_RANDOM_RANGE);
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("[仪表盘缓存] 写入缓存失败，key: {}", key, ex);
        }
    }
}
