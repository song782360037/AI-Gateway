package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.RequestLogQueryReq;
import com.code.aigateway.admin.model.rsp.RequestLogRsp;
import com.code.aigateway.admin.service.RequestLogService;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 请求日志管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/request-log")
public class RequestLogController {

    private final RequestLogService requestLogService;

    /**
     * 分页查询请求日志
     */
    @PostMapping("/list")
    public Mono<R<PageResult<RequestLogRsp>>> list(@Valid @RequestBody RequestLogQueryReq req) {
        return Mono.fromCallable(() -> requestLogService.list(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 查询单条请求日志详情（按主键 id 查询，避免 request_id 重复导致详情错位）
     */
    @GetMapping("/by-id/{id}")
    public Mono<R<RequestLogRsp>> detailById(@PathVariable Long id) {
        return Mono.fromCallable(() -> requestLogService.getDetailById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 根据 requestId 查询请求日志详情（兼容旧接口）
     * @deprecated 推荐使用 {@link #detailById(Long)} 按主键查询，避免 request_id 重复导致详情错位
     */
    @Deprecated
    @GetMapping("/{requestId}")
    public Mono<R<RequestLogRsp>> detail(@PathVariable String requestId) {
        return Mono.fromCallable(() -> requestLogService.getDetail(requestId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }
}
