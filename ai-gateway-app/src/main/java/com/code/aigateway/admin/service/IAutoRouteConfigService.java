package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.req.AutoRouteCandidateAddReq;
import com.code.aigateway.admin.model.req.AutoRouteCandidateUpdateReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigAddReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigQueryReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigUpdateReq;
import com.code.aigateway.admin.model.req.AutoRouteEvaluateReq;
import com.code.aigateway.admin.model.rsp.AutoRouteConfigRsp;
import com.code.aigateway.admin.model.rsp.AutoRouteEvaluateRsp;
import com.code.aigateway.common.result.PageResult;

/**
 * Auto 智能路由配置管理服务接口
 */
public interface IAutoRouteConfigService {

    Long add(AutoRouteConfigAddReq req);

    void update(AutoRouteConfigUpdateReq req);

    void delete(Long id, Long versionNo);

    AutoRouteConfigRsp getById(Long id);

    PageResult<AutoRouteConfigRsp> list(AutoRouteConfigQueryReq req);

    AutoRouteEvaluateRsp evaluate(AutoRouteEvaluateReq req);

    void toggle(Long id, Long versionNo);

    Long addCandidate(AutoRouteCandidateAddReq req);

    void updateCandidate(AutoRouteCandidateUpdateReq req);

    void deleteCandidate(Long id, Long versionNo);

    void toggleCandidate(Long id, Long versionNo);
}
