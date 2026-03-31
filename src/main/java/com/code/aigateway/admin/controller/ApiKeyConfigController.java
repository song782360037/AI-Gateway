package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.ApiKeyConfigAddReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigQueryReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigCreateRsp;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigRsp;
import com.code.aigateway.admin.service.IApiKeyConfigService;
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
 * API Key 配置管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/api-key-config")
public class ApiKeyConfigController {

    private final IApiKeyConfigService apiKeyConfigService;

    /**
     * 新增 API Key 配置
     *
     * @return 含完整明文 key 的响应（仅创建时返回一次）
     */
    @PostMapping("/add")
    public Mono<R<ApiKeyConfigCreateRsp>> add(@Valid @RequestBody ApiKeyConfigAddReq req) {
        return Mono.fromCallable(() -> apiKeyConfigService.add(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 更新 API Key 配置
     */
    @PostMapping("/update")
    public Mono<R<Void>> update(@Valid @RequestBody ApiKeyConfigUpdateReq req) {
        return Mono.fromRunnable(() -> apiKeyConfigService.update(req))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 删除 API Key 配置（逻辑删除）
     */
    @PostMapping("/delete/{id}")
    public Mono<R<Void>> delete(@PathVariable Long id) {
        return Mono.fromRunnable(() -> apiKeyConfigService.delete(id))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 查询 API Key 配置详情
     */
    @GetMapping("/{id}")
    public Mono<R<ApiKeyConfigRsp>> getById(@PathVariable Long id) {
        return Mono.fromCallable(() -> apiKeyConfigService.getById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 分页查询 API Key 配置
     */
    @PostMapping("/list")
    public Mono<R<PageResult<ApiKeyConfigRsp>>> list(@Valid @RequestBody ApiKeyConfigQueryReq req) {
        return Mono.fromCallable(() -> apiKeyConfigService.list(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }
}
