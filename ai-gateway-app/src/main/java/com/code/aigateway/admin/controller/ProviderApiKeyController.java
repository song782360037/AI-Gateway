package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.ProviderApiKeyAddReq;
import com.code.aigateway.admin.model.req.ProviderApiKeyListReq;
import com.code.aigateway.admin.model.req.ProviderApiKeyUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderApiKeyRsp;
import com.code.aigateway.admin.service.IProviderApiKeyService;
import com.code.aigateway.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 提供商 API Key 管理 Controller
 */
@RestController
@RequestMapping("/admin/provider-api-key")
@RequiredArgsConstructor
public class ProviderApiKeyController {

    private final IProviderApiKeyService providerApiKeyService;

    /**
     * 查询指定提供商下所有 API Key
     */
    @PostMapping("/list")
    public Mono<R<List<ProviderApiKeyRsp>>> list(@Valid @RequestBody ProviderApiKeyListReq req) {
        return Mono.fromCallable(() -> R.ok(providerApiKeyService.list(req.getProviderCode())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 新增 API Key
     */
    @PostMapping("/add")
    public Mono<R<Void>> add(@Valid @RequestBody ProviderApiKeyAddReq req) {
        return Mono.fromRunnable(() -> providerApiKeyService.add(req))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(R.ok()));
    }

    /**
     * 更新 API Key（备注/权重/排序）
     */
    @PostMapping("/update")
    public Mono<R<Void>> update(@Valid @RequestBody ProviderApiKeyUpdateReq req) {
        return Mono.fromRunnable(() -> providerApiKeyService.update(req))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(R.ok()));
    }

    /**
     * 删除 API Key
     */
    @PostMapping("/delete/{id}")
    public Mono<R<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> providerApiKeyService.delete(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(R.ok()));
    }

    /**
     * 切换启用/禁用状态
     */
    @PostMapping("/toggle")
    public Mono<R<Void>> toggle(@RequestParam Long id,
                                @RequestParam Long versionNo,
                                @RequestParam boolean enabled) {
        return Mono.fromRunnable(() -> providerApiKeyService.toggle(id, versionNo, enabled))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(R.ok()));
    }
}
