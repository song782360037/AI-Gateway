package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.SupportedModelAddReq;
import com.code.aigateway.admin.model.req.SupportedModelDeleteReq;
import com.code.aigateway.admin.model.req.SupportedModelQueryReq;
import com.code.aigateway.admin.model.req.SupportedModelToggleReq;
import com.code.aigateway.admin.model.req.SupportedModelUpdateReq;
import com.code.aigateway.admin.model.rsp.SupportedModelRsp;
import com.code.aigateway.admin.service.ISupportedModelService;
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
 * 支持模型配置管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/supported-model")
public class SupportedModelController {

    private final ISupportedModelService supportedModelService;

    @PostMapping("/add")
    public Mono<R<Long>> add(@Valid @RequestBody SupportedModelAddReq req) {
        return Mono.fromCallable(() -> supportedModelService.add(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    @PostMapping("/update")
    public Mono<R<Void>> update(@Valid @RequestBody SupportedModelUpdateReq req) {
        return Mono.fromRunnable(() -> supportedModelService.update(req))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    @PostMapping("/toggle")
    public Mono<R<Void>> toggle(@Valid @RequestBody SupportedModelToggleReq req) {
        return Mono.fromRunnable(() -> supportedModelService.toggle(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    @PostMapping("/delete")
    public Mono<R<Void>> delete(@Valid @RequestBody SupportedModelDeleteReq req) {
        return Mono.fromRunnable(() -> supportedModelService.delete(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    @GetMapping("/{id}")
    public Mono<R<SupportedModelRsp>> getById(@PathVariable Long id) {
        return Mono.fromCallable(() -> supportedModelService.getById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    @PostMapping("/list")
    public Mono<R<PageResult<SupportedModelRsp>>> list(@Valid @RequestBody SupportedModelQueryReq req) {
        return Mono.fromCallable(() -> supportedModelService.list(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 从路由别名同步导入模型
     *
     * @return 导入的模型数量
     */
    @PostMapping("/sync-from-redirect")
    public Mono<R<Integer>> syncFromRedirect() {
        return Mono.fromCallable(() -> supportedModelService.syncFromRedirect())
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }
}
