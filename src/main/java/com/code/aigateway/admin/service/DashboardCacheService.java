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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardOverviewRsp getOverview() {
        return get(CacheConstants.KEY_DASHBOARD_OVERVIEW, DashboardOverviewRsp.class);
    }

    public void setOverview(DashboardOverviewRsp value) {
        set(CacheConstants.KEY_DASHBOARD_OVERVIEW, value);
    }

    public List<ModelUsageRankRsp> getModelRank() {
        return get(CacheConstants.KEY_DASHBOARD_MODEL_RANK, new TypeReference<>() {});
    }

    public void setModelRank(List<ModelUsageRankRsp> value) {
        set(CacheConstants.KEY_DASHBOARD_MODEL_RANK, value);
    }

    public List<RecentRequestRsp> getRecentRequests() {
        return get(CacheConstants.KEY_DASHBOARD_RECENT_REQUESTS, new TypeReference<>() {});
    }

    public void setRecentRequests(List<RecentRequestRsp> value) {
        set(CacheConstants.KEY_DASHBOARD_RECENT_REQUESTS, value);
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
