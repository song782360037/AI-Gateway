package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.rsp.DashboardOverviewRsp;
import com.code.aigateway.admin.model.rsp.ModelUsageRankRsp;
import com.code.aigateway.admin.model.rsp.RecentRequestRsp;
import com.code.aigateway.admin.service.IDashboardService;
import com.code.aigateway.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计管理接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
public class DashboardController {

    private final IDashboardService dashboardService;

    /**
     * 获取仪表盘概览统计
     *
     * @param period 时间范围：today / 7d / 30d，默认 today
     */
    @GetMapping("/overview")
    public Mono<R<DashboardOverviewRsp>> overview(
            @RequestParam(defaultValue = "today") String period) {
        return Mono.fromCallable(() -> dashboardService.getOverview(period))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 获取模型调用排行
     */
    @GetMapping("/model-rank")
    public Mono<R<List<ModelUsageRankRsp>>> modelRank(
            @RequestParam(defaultValue = "today") String period) {
        return Mono.fromCallable(() -> dashboardService.getModelUsageRank(period))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 获取最近请求记录
     */
    @GetMapping("/recent-requests")
    public Mono<R<List<RecentRequestRsp>>> recentRequests(
            @RequestParam(defaultValue = "today") String period) {
        return Mono.fromCallable(() -> dashboardService.getRecentRequests(period))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 系统健康检测（简单心跳）
     */
    @GetMapping("/health")
    public Mono<R<Map<String, String>>> health() {
        return Mono.just(R.ok(Map.of("status", "UP")));
    }
}
