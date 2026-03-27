package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.service.RuntimeConfigRefreshService;
import com.code.aigateway.common.result.R;
import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.runtime.RedisRoutingCacheService;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时配置管理接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/runtime-config")
public class RuntimeConfigController {

    private final RuntimeConfigRefreshService runtimeConfigRefreshService;
    private final RoutingSnapshotHolder routingSnapshotHolder;
    private final RedisRoutingCacheService redisRoutingCacheService;

    /**
     * 手动全量重载运行时配置
     */
    @PostMapping("/reload")
    public R<Boolean> reload() {
        return R.ok(runtimeConfigRefreshService.reloadFromDb("admin-manual"));
    }

    /**
     * 查看当前运行时状态
     */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasSnapshot", snapshot != null);
        result.put("dirty", routingSnapshotHolder.isDirty() || redisRoutingCacheService.isDirty());

        if (snapshot != null) {
            result.put("version", snapshot.getVersion());
            result.put("source", snapshot.getSource());
            result.put("createdAt", Instant.ofEpochMilli(snapshot.getCreatedAt()).toString());
            result.put("aliasCount", snapshot.getCandidatesMapSize());
            result.put("providerCount", snapshot.getProviderMap().size());
        }

        return R.ok(result);
    }
}
