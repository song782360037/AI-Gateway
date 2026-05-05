package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.req.SupportedModelAddReq;
import com.code.aigateway.admin.model.req.SupportedModelQueryReq;
import com.code.aigateway.admin.model.req.SupportedModelUpdateReq;
import com.code.aigateway.admin.model.rsp.SupportedModelRsp;
import com.code.aigateway.common.result.PageResult;

import java.util.List;

/**
 * 支持模型配置管理服务接口
 */
public interface ISupportedModelService {

    /** 新增支持模型 */
    Long add(SupportedModelAddReq req);

    /** 更新支持模型 */
    void update(SupportedModelUpdateReq req);

    /** 删除支持模型（含乐观锁校验） */
    void delete(Long id, Long versionNo);

    /** 查询支持模型详情 */
    SupportedModelRsp getById(Long id);

    /** 分页查询支持模型 */
    PageResult<SupportedModelRsp> list(SupportedModelQueryReq req);

    /** 切换启用/禁用状态 */
    void toggle(Long id, Long versionNo);

    /** 从路由别名同步导入模型，返回导入数量 */
    int syncFromRedirect();
}
