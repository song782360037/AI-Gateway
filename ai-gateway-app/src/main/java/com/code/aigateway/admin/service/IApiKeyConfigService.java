package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.req.ApiKeyConfigAddReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigQueryReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigCreateRsp;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigRsp;
import com.code.aigateway.common.result.PageResult;

/**
 * API Key 配置管理服务接口
 */
public interface IApiKeyConfigService {

    /**
     * 新增 API Key 配置，返回含完整明文 key 的响应（仅此一次）
     */
    ApiKeyConfigCreateRsp add(ApiKeyConfigAddReq req);

    /**
     * 更新 API Key 配置
     */
    void update(ApiKeyConfigUpdateReq req);

    /**
     * 删除 API Key 配置（逻辑删除）
     */
    void delete(Long id);

    /**
     * 查询 API Key 配置详情
     */
    ApiKeyConfigRsp getById(Long id);

    /**
     * 分页查询 API Key 配置
     */
    PageResult<ApiKeyConfigRsp> list(ApiKeyConfigQueryReq req);
}
