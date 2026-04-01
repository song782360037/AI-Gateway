package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.req.ModelRedirectConfigAddReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigQueryReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ModelRedirectConfigRsp;
import com.code.aigateway.common.result.PageResult;

/**
 * 模型重定向配置管理服务接口
 */
public interface IModelRedirectConfigService {

    /**
     * 新增模型重定向配置
     */
    Long add(ModelRedirectConfigAddReq req);

    /**
     * 更新模型重定向配置
     */
    void update(ModelRedirectConfigUpdateReq req);

    /**
     * 删除模型重定向配置
     */
    void delete(Long id);

    /**
     * 查询模型重定向配置详情
     */
    ModelRedirectConfigRsp getById(Long id);

    /**
     * 分页查询模型重定向配置
     */
    PageResult<ModelRedirectConfigRsp> list(ModelRedirectConfigQueryReq req);

    /**
     * 切换路由规则启用/禁用状态
     */
    void toggle(Long id, Long versionNo);
}
