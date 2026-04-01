package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.req.RequestLogQueryReq;
import com.code.aigateway.admin.model.rsp.RequestLogRsp;
import com.code.aigateway.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 请求日志查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final RequestLogMapper requestLogMapper;

    /**
     * 分页查询请求日志
     */
    public PageResult<RequestLogRsp> list(RequestLogQueryReq req) {
        int offset = (req.getPage() - 1) * req.getPageSize();
        List<RequestLogDO> records = requestLogMapper.selectPage(req, offset, req.getPageSize());
        long total = requestLogMapper.countPage(req);

        List<RequestLogRsp> rspList = records.stream().map(this::toRsp).toList();
        return PageResult.of(rspList, total, req.getPage(), req.getPageSize());
    }

    /**
     * DO 转 Rsp
     */
    private RequestLogRsp toRsp(RequestLogDO record) {
        RequestLogRsp rsp = new RequestLogRsp();
        rsp.setId(record.getId());
        rsp.setRequestId(record.getRequestId());
        rsp.setAliasModel(record.getAliasModel());
        rsp.setTargetModel(record.getTargetModel());
        rsp.setProviderCode(record.getProviderCode());
        rsp.setProviderType(record.getProviderType());
        rsp.setIsStream(record.getIsStream());
        rsp.setPromptTokens(record.getPromptTokens());
        rsp.setCompletionTokens(record.getCompletionTokens());
        rsp.setTotalTokens(record.getTotalTokens());
        rsp.setDurationMs(record.getDurationMs());
        rsp.setStatus(record.getStatus());
        rsp.setErrorCode(record.getErrorCode());
        rsp.setSourceIp(record.getSourceIp());
        rsp.setCreateTime(record.getCreateTime());
        return rsp;
    }
}
