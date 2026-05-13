package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.ModelRedirectConfigAddReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigQueryReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigToggleReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ModelRedirectConfigRsp;
import com.code.aigateway.admin.service.IModelRedirectConfigService;
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
 * 模型重定向配置管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/model-redirect-config")
public class ModelRedirectConfigController {

    private final IModelRedirectConfigService modelRedirectConfigService;

    /**
     * 新增模型重定向配置
     *
     * @param req 新增请求参数
     * @return 新增记录的主键 ID
     */
    @PostMapping("/add")
    public Mono<R<Long>> add(@Valid @RequestBody ModelRedirectConfigAddReq req) {
        // JDBC/MyBatis 为阻塞调用，这里切到 boundedElastic，避免阻塞 WebFlux 事件线程。
        return Mono.fromCallable(() -> modelRedirectConfigService.add(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 更新模型重定向配置
     *
     * @param req 更新请求参数，包含 id 和 versionNo
     */
    @PostMapping("/update")
    public Mono<R<Void>> update(@Valid @RequestBody ModelRedirectConfigUpdateReq req) {
        // 阻塞型写操作统一放到弹性线程池执行，避免影响响应式主链路。
        return Mono.fromRunnable(() -> modelRedirectConfigService.update(req))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 切换路由规则启用/禁用状态
     *
     * @param req 切换请求参数，包含 id 和 versionNo
     */
    @PostMapping("/toggle")
    public Mono<R<Void>> toggle(@Valid @RequestBody ModelRedirectConfigToggleReq req) {
        // 阻塞型写操作统一切到弹性线程池执行。
        return Mono.fromRunnable(() -> modelRedirectConfigService.toggle(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 逻辑删除模型重定向配置
     *
     * @param id 重定向配置主键
     */
    @PostMapping("/delete/{id}")
    public Mono<R<Void>> delete(@PathVariable Long id) {
        // 阻塞型删除操作统一切线程执行。
        return Mono.fromRunnable(() -> modelRedirectConfigService.delete(id))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 查询模型重定向配置详情
     *
     * @param id 重定向配置主键
     * @return 重定向配置详情
     */
    @GetMapping("/{id}")
    public Mono<R<ModelRedirectConfigRsp>> getById(@PathVariable Long id) {
        // 阻塞型查询同样切到 boundedElastic，保持 WebFlux 线程模型一致。
        return Mono.fromCallable(() -> modelRedirectConfigService.getById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 分页查询模型重定向配置
     *
     * @param req 查询条件，包含分页参数
     * @return 分页结果
     */
    @PostMapping("/list")
    public Mono<R<PageResult<ModelRedirectConfigRsp>>> list(@Valid @RequestBody ModelRedirectConfigQueryReq req) {
        // 分页查询底层仍是阻塞数据库访问，需要切线程池执行。
        return Mono.fromCallable(() -> modelRedirectConfigService.list(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }
}
