package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.admin.model.req.ProviderConfigQueryReq;
import com.code.aigateway.admin.model.req.ProviderConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ConnectionTestResult;
import com.code.aigateway.admin.model.rsp.ProviderConfigRsp;
import com.code.aigateway.admin.service.IProviderConfigService;
import com.code.aigateway.admin.service.ProviderConnectionTestService;
import com.code.aigateway.admin.service.IProviderConfigService.ProviderPriorityItem;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.common.result.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
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

import java.util.List;

/**
 * 提供商配置管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/provider-config")
public class ProviderConfigController {

    private final IProviderConfigService providerConfigService;
    private final ProviderConnectionTestService connectionTestService;

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
     * 批量更新提供商优先级（拖拽排序）
     *
     * @param req 包含 items 列表的请求，每项含 id、versionNo、priority
     */
    @PostMapping("/batch-update-priority")
    public Mono<R<Void>> batchUpdatePriority(@Valid @RequestBody BatchPriorityReq req) {
        List<ProviderPriorityItem> items = req.getItems().stream()
                .map(item -> new ProviderPriorityItem(item.getId(), item.getVersionNo(), item.getPriority()))
                .toList();
        return Mono.fromRunnable(() -> providerConfigService.batchUpdatePriority(items))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 测试提供商连接
     * <p>
     * 根据提供商类型构造轻量级验证请求（GET /models 或最小化 chat），
     * 确认服务可达性和凭证有效性。阻塞的数据库读取切到弹性线程池，
     * 后续 HTTP 调用天然响应式，直接在事件线程执行。
     * </p>
     *
     * @param id 提供商配置主键
     * @return 连接测试结果
     */
    @PostMapping("/test-connection/{id}")
    public Mono<R<ConnectionTestResult>> testConnection(@PathVariable Long id) {
        // 阻塞的 DB 读取 + API Key 解密切到弹性线程池，避免阻塞 Netty 事件线程
        return Mono.fromCallable(() -> connectionTestService.loadTestContext(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ctx -> {
                    if (ctx == null) {
                        return Mono.just(R.ok(ConnectionTestResult.builder()
                                .success(false)
                                .errorType("UNKNOWN")
                                .errorMessage("提供商配置不存在，id=" + id)
                                .build()));
                    }
                    // 响应式 HTTP 测试直接在事件线程执行
                    return connectionTestService.executeTest(ctx).map(R::ok);
                });
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

    /**
     * 批量更新优先级请求参数
     */
    @Data
    static class BatchPriorityReq {
        @NotEmpty(message = "优先级列表不能为空")
        @Valid
        private List<PriorityItem> items;
    }

    @Data
    static class PriorityItem {
        @NotNull(message = "ID 不能为空")
        private Long id;

        @NotNull(message = "版本号不能为空")
        private Long versionNo;

        @NotNull(message = "优先级不能为空")
        @Min(value = 0, message = "优先级不能为负数")
        @Max(value = 9999, message = "优先级超出允许范围")
        private Integer priority;
    }
}
