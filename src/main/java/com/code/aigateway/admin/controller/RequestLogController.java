package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.RequestLogQueryReq;
import com.code.aigateway.admin.model.rsp.RequestLogRsp;
import com.code.aigateway.admin.service.RequestLogService;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
}
