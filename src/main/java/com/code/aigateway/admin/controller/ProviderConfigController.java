package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.admin.model.req.ProviderConfigQueryReq;
import com.code.aigateway.admin.model.req.ProviderConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderConfigRsp;
import com.code.aigateway.admin.service.IProviderConfigService;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.common.result.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
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
 * 提供商配置管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/provider-config")
public class ProviderConfigController {

    private final IProviderConfigService providerConfigService;

    /**
     * 新增提供商配置
     *
     * @param req 新增请求参数
     * @return 新增记录的主键 ID
     */
    @PostMapping("/add")
    public Mono<R<Long>> add(@Valid @RequestBody ProviderConfigAddReq req) {
        // JDBC/MyBatis 为阻塞调用，这里切到 boundedElastic，避免阻塞 WebFlux 事件线程。
        return Mono.fromCallable(() -> providerConfigService.add(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 更新提供商配置
     *
     * @param req 更新请求参数，包含 id 和 versionNo
     */
    @PostMapping("/update")
    public Mono<R<Void>> update(@Valid @RequestBody ProviderConfigUpdateReq req) {
        // 阻塞型写操作统一放到弹性线程池执行，避免影响响应式主链路。
        return Mono.fromRunnable(() -> providerConfigService.update(req))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 逻辑删除提供商配置
     *
     * @param id 提供商配置主键
     */
    @PostMapping("/delete/{id}")
    public Mono<R<Void>> delete(@PathVariable Long id) {
        // 阻塞型删除操作统一切线程执行。
        return Mono.fromRunnable(() -> providerConfigService.delete(id))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 查询提供商配置详情
     *
     * @param id 提供商配置主键
     * @return 提供商配置详情（含掩码后的 API Key）
     */
    @GetMapping("/{id}")
    public Mono<R<ProviderConfigRsp>> getById(@PathVariable Long id) {
        // 阻塞型查询同样切到 boundedElastic，保持 WebFlux 线程模型一致。
        return Mono.fromCallable(() -> providerConfigService.getById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 分页查询提供商配置
     *
     * @param req 查询条件，包含分页参数
     * @return 分页结果
     */
    @PostMapping("/list")
    public Mono<R<PageResult<ProviderConfigRsp>>> list(@Valid @RequestBody ProviderConfigQueryReq req) {
        // 分页查询底层仍是阻塞数据库访问，需要切线程池执行。
        return Mono.fromCallable(() -> providerConfigService.list(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 切换提供商配置启用/禁用状态
     *
     * @param req 包含 id 和 versionNo 的切换请求
     */
    @PostMapping("/toggle")
    public Mono<R<Void>> toggle(@Valid @RequestBody ProviderConfigToggleReq req) {
        // 阻塞型写操作统一放到弹性线程池执行，避免影响响应式主链路。
        return Mono.fromRunnable(() -> providerConfigService.toggle(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 提供商配置状态切换请求参数
     */
    @Data
    static class ProviderConfigToggleReq {
        @NotNull(message = "ID 不能为空")
        private Long id;

        @NotNull(message = "版本号不能为空")
        private Long versionNo;
    }
}
