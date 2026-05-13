package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.rsp.JvmInfoRsp;
import com.code.aigateway.admin.model.rsp.SystemOverviewRsp;
import com.code.aigateway.admin.model.rsp.SystemRealtimeRsp;
import com.code.aigateway.admin.service.SystemMonitorService;
import com.code.aigateway.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 系统监控管理接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/system-monitor")
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    /**
     * 获取系统概览信息
     */
    @GetMapping("/overview")
    public Mono<R<SystemOverviewRsp>> overview() {
        return Mono.fromCallable(() -> systemMonitorService.getOverview())
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 获取 JVM 详细信息
     */
    @GetMapping("/jvm")
    public Mono<R<JvmInfoRsp>> jvm() {
        return Mono.fromCallable(() -> systemMonitorService.getJvmInfo())
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 获取系统实时指标（用于前端定时刷新）
     */
    @GetMapping("/realtime")
    public Mono<R<SystemRealtimeRsp>> realtime() {
        return Mono.fromCallable(() -> systemMonitorService.getRealtime())
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }
}
