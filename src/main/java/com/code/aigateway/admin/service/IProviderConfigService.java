package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.admin.model.req.ProviderConfigQueryReq;
import com.code.aigateway.admin.model.req.ProviderConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderConfigRsp;
import com.code.aigateway.common.result.PageResult;

import java.util.List;

/**
 * 提供商配置管理服务接口
 */
public interface IProviderConfigService {

    /**
     * 新增提供商配置
     */
    Long add(ProviderConfigAddReq req);

    /**
     * 更新提供商配置
     */
    void update(ProviderConfigUpdateReq req);

    /**
     * 删除提供商配置
     */
    void delete(Long id);

    /**
     * 切换提供商配置启用/禁用状态
     */
    void toggle(Long id, Long versionNo);

    /**
     * 查询提供商配置详情
     */
    ProviderConfigRsp getById(Long id);

    /**
     * 分页查询提供商配置
     */
    PageResult<ProviderConfigRsp> list(ProviderConfigQueryReq req);

    /**
     * 批量更新提供商优先级
     *
     * @param items 包含 id、versionNo、priority 的列表
     */
    void batchUpdatePriority(List<ProviderPriorityItem> items);

    /** 批量更新优先级的单项参数 */
    record ProviderPriorityItem(Long id, Long versionNo, Integer priority) {}
}
