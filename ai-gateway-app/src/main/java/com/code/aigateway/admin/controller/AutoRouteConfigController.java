package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.model.req.AutoRouteCandidateAddReq;
import com.code.aigateway.admin.model.req.AutoRouteCandidateUpdateReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigAddReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigQueryReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigUpdateReq;
import com.code.aigateway.admin.model.req.AutoRouteEvaluateReq;
import com.code.aigateway.admin.model.req.AutoRouteIdVersionReq;
import com.code.aigateway.admin.model.rsp.AutoRouteConfigRsp;
import com.code.aigateway.admin.model.rsp.AutoRouteEvaluateRsp;
import com.code.aigateway.admin.service.IAutoRouteConfigService;
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
 * Auto 智能路由配置管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/auto-route-config")
public class AutoRouteConfigController {

    private final IAutoRouteConfigService autoRouteConfigService;

    /**
     * 新增 Auto 智能路由配置
     *
     * @param req 新增请求参数
     * @return 新增记录的主键 ID
     */
    @PostMapping("/add")
    public Mono<R<Long>> add(@Valid @RequestBody AutoRouteConfigAddReq req) {
        return Mono.fromCallable(() -> autoRouteConfigService.add(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 更新 Auto 智能路由配置
     *
     * @param req 更新请求参数，包含 id 和 versionNo
     */
    @PostMapping("/update")
    public Mono<R<Void>> update(@Valid @RequestBody AutoRouteConfigUpdateReq req) {
        return Mono.fromRunnable(() -> autoRouteConfigService.update(req))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 切换 Auto 智能路由配置启用状态
     *
     * @param req 切换请求参数，包含 id 和 versionNo
     */
    @PostMapping("/toggle")
    public Mono<R<Void>> toggle(@Valid @RequestBody AutoRouteIdVersionReq req) {
        return Mono.fromRunnable(() -> autoRouteConfigService.toggle(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 删除 Auto 智能路由配置
     *
     * @param req 删除请求参数，包含 id 和 versionNo
     */
    @PostMapping("/delete")
    public Mono<R<Void>> delete(@Valid @RequestBody AutoRouteIdVersionReq req) {
        return Mono.fromRunnable(() -> autoRouteConfigService.delete(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 查询 Auto 智能路由配置详情
     *
     * @param id 配置主键
     * @return 配置详情
     */
    @GetMapping("/{id}")
    public Mono<R<AutoRouteConfigRsp>> getById(@PathVariable Long id) {
        return Mono.fromCallable(() -> autoRouteConfigService.getById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 分页查询 Auto 智能路由配置
     *
     * @param req 查询条件，包含分页参数
     * @return 分页结果
     */
    @PostMapping("/list")
    public Mono<R<PageResult<AutoRouteConfigRsp>>> list(@Valid @RequestBody AutoRouteConfigQueryReq req) {
        return Mono.fromCallable(() -> autoRouteConfigService.list(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 评估示例请求的 Auto 智能路由选择结果
     *
     * @param req 评估请求参数
     * @return 请求画像和候选评分明细
     */
    @PostMapping("/evaluate")
    public Mono<R<AutoRouteEvaluateRsp>> evaluate(@Valid @RequestBody AutoRouteEvaluateReq req) {
        return Mono.fromCallable(() -> autoRouteConfigService.evaluate(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 新增 Auto 智能路由候选模型
     *
     * @param req 新增请求参数
     * @return 新增记录的主键 ID
     */
    @PostMapping("/candidate/add")
    public Mono<R<Long>> addCandidate(@Valid @RequestBody AutoRouteCandidateAddReq req) {
        return Mono.fromCallable(() -> autoRouteConfigService.addCandidate(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 更新 Auto 智能路由候选模型
     *
     * @param req 更新请求参数，包含 id 和 versionNo
     */
    @PostMapping("/candidate/update")
    public Mono<R<Void>> updateCandidate(@Valid @RequestBody AutoRouteCandidateUpdateReq req) {
        return Mono.fromRunnable(() -> autoRouteConfigService.updateCandidate(req))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 删除 Auto 智能路由候选模型
     *
     * @param req 删除请求参数，包含 id 和 versionNo
     */
    @PostMapping("/candidate/delete")
    public Mono<R<Void>> deleteCandidate(@Valid @RequestBody AutoRouteIdVersionReq req) {
        return Mono.fromRunnable(() -> autoRouteConfigService.deleteCandidate(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }

    /**
     * 切换 Auto 智能路由候选模型启用状态
     *
     * @param req 切换请求参数，包含 id 和 versionNo
     */
    @PostMapping("/candidate/toggle")
    public Mono<R<Void>> toggleCandidate(@Valid @RequestBody AutoRouteIdVersionReq req) {
        return Mono.fromRunnable(() -> autoRouteConfigService.toggleCandidate(req.getId(), req.getVersionNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(R.ok());
    }
}
