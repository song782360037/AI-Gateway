package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.SupportedModelMapper;
import com.code.aigateway.admin.model.dataobject.ModelRedirectConfigDO;
import com.code.aigateway.admin.model.dataobject.SupportedModelDO;
import com.code.aigateway.admin.model.req.SupportedModelAddReq;
import com.code.aigateway.admin.model.req.SupportedModelQueryReq;
import com.code.aigateway.admin.model.req.SupportedModelUpdateReq;
import com.code.aigateway.admin.model.rsp.SupportedModelRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支持模型配置管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportedModelServiceImpl implements ISupportedModelService {

    private final SupportedModelMapper supportedModelMapper;
    private final ModelRedirectConfigMapper modelRedirectConfigMapper;
    private final RuntimeConfigRefreshService runtimeConfigRefreshService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Long add(SupportedModelAddReq req) {
        // 校验 modelId 唯一性
        if (supportedModelMapper.existsByModelId(req.getModelId()) > 0) {
            throw new BizException("CONFIG_CONFLICT", "模型标识已存在: " + req.getModelId());
        }

        SupportedModelDO record = buildInsertRecord(req);
        record.setVersionNo(0L);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = supportedModelMapper.insert(record);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "新增支持模型失败");
            }
            log.info("[支持模型] 新增成功，id: {}，modelId: {}", record.getId(), req.getModelId());
        });

        ensureRuntimeConfigReloaded("admin-add-supported-model");
        return record.getId();
    }

    @Override
    public void update(SupportedModelUpdateReq req) {
        SupportedModelDO existing = supportedModelMapper.selectById(req.getId());
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "支持模型不存在，id: " + req.getId());
        }

        // modelId 唯一性校验（排除自身）
        if (!existing.getModelId().equals(req.getModelId())) {
            if (supportedModelMapper.existsByModelId(req.getModelId()) > 0) {
                throw new BizException("CONFIG_CONFLICT", "模型标识已存在: " + req.getModelId());
            }
        }

        SupportedModelDO record = buildUpdateRecord(req);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = supportedModelMapper.updateById(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                        "数据已被其他请求修改，请刷新后重试，id: " + req.getId());
            }
            log.info("[支持模型] 更新成功，id: {}，modelId: {}", req.getId(), req.getModelId());
        });

        ensureRuntimeConfigReloaded("admin-update-supported-model");
    }

    @Override
    public void delete(Long id, Long versionNo) {
        SupportedModelDO existing = supportedModelMapper.selectById(id);
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "支持模型不存在，id: " + id);
        }

        SupportedModelDO record = new SupportedModelDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            int rows = supportedModelMapper.softDeleteById(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                        "数据已被其他请求修改，请刷新后重试，id: " + id);
            }
            log.info("[支持模型] 删除成功，id: {}，modelId: {}", id, existing.getModelId());
        });

        ensureRuntimeConfigReloaded("admin-delete-supported-model");
    }

    @Override
    public SupportedModelRsp getById(Long id) {
        SupportedModelDO record = supportedModelMapper.selectById(id);
        if (record == null) {
            throw new BizException("CONFIG_NOT_FOUND", "支持模型不存在，id: " + id);
        }
        return toRsp(record);
    }

    @Override
    public PageResult<SupportedModelRsp> list(SupportedModelQueryReq req) {
        int offset = (req.getPage() - 1) * req.getPageSize();
        List<SupportedModelDO> records = supportedModelMapper.selectList(
                req.getModelId(), req.getDisplayName(), req.getOwnedBy(), req.getEnabled(), offset, req.getPageSize());
        long total = supportedModelMapper.countList(
                req.getModelId(), req.getDisplayName(), req.getOwnedBy(), req.getEnabled());

        List<SupportedModelRsp> rspList = records.stream().map(this::toRsp).toList();
        return PageResult.of(rspList, total, req.getPage(), req.getPageSize());
    }

    @Override
    public void toggle(Long id, Long versionNo) {
        SupportedModelDO existing = supportedModelMapper.selectById(id);
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "支持模型不存在，id: " + id);
        }

        SupportedModelDO record = new SupportedModelDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setEnabled(!existing.getEnabled());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            int rows = supportedModelMapper.updateEnabled(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                        "数据已被其他请求修改，请刷新后重试，id: " + id);
            }
            log.info("[支持模型] 状态切换成功，id: {}，enabled: {} -> {}",
                    id, existing.getEnabled(), !existing.getEnabled());
        });

        ensureRuntimeConfigReloaded("admin-toggle-supported-model");
    }

    @Override
    public int syncFromRedirect() {
        // 获取所有启用的精确匹配路由别名
        List<ModelRedirectConfigDO> allRedirects = modelRedirectConfigMapper.selectAllEnabled();

        // 过滤出 EXACT 类型的别名，提取去重的 aliasName 作为 modelId
        List<String> aliasNames = allRedirects.stream()
                .filter(r -> "EXACT".equals(r.getMatchType() != null ? r.getMatchType() : "EXACT"))
                .map(ModelRedirectConfigDO::getAliasName)
                .distinct()
                .toList();

        if (aliasNames.isEmpty()) {
            return 0;
        }

        // 批量查询已存在的 modelId，避免 N+1
        List<String> existingModelIds = supportedModelMapper.selectExistingModelIds(aliasNames);
        java.util.Set<String> existingSet = new java.util.HashSet<>(existingModelIds);

        List<SupportedModelDO> toInsert = new java.util.ArrayList<>();
        for (String aliasName : aliasNames) {
            if (existingSet.contains(aliasName)) {
                continue;
            }
            SupportedModelDO record = new SupportedModelDO();
            record.setModelId(aliasName);
            record.setDisplayName(aliasName);
            record.setOwnedBy("");
            record.setEnabled(true);
            record.setSortOrder(0);
            record.setVersionNo(0L);
            record.setCreator("");
            record.setUpdater("");
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            record.setDeleted(false);
            toInsert.add(record);
        }

        if (toInsert.isEmpty()) {
            return 0;
        }

        // 单事务批量插入
        final int[] imported = {0};
        transactionTemplate.executeWithoutResult(status -> {
            for (SupportedModelDO record : toInsert) {
                int rows = supportedModelMapper.insert(record);
                if (rows <= 0) {
                    throw new BizException("DB_ERROR", "同步导入模型失败: " + record.getModelId());
                }
                imported[0]++;
                log.info("[支持模型] 同步导入成功，modelId: {}", record.getModelId());
            }
        });

        if (imported[0] > 0) {
            ensureRuntimeConfigReloaded("admin-sync-supported-model");
        }

        log.info("[支持模型] 同步导入完成，共导入 {} 条", imported[0]);
        return imported[0];
    }

    // ==================== 内部方法 ====================

    private void ensureRuntimeConfigReloaded(String source) {
        if (runtimeConfigRefreshService.reloadFromDb(source)) {
            return;
        }
        throw new BizException("CONFIG_REFRESH_FAILED", "运行时配置刷新失败，请稍后重试");
    }

    private SupportedModelDO buildInsertRecord(SupportedModelAddReq req) {
        SupportedModelDO record = new SupportedModelDO();
        record.setModelId(req.getModelId());
        record.setDisplayName(req.getDisplayName());
        record.setOwnedBy(req.getOwnedBy());
        record.setEnabled(req.getEnabled());
        record.setSortOrder(req.getSortOrder());
        record.setDeleted(false);
        record.setCreator("");
        record.setUpdater("");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private SupportedModelDO buildUpdateRecord(SupportedModelUpdateReq req) {
        SupportedModelDO record = new SupportedModelDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        record.setModelId(req.getModelId());
        record.setDisplayName(req.getDisplayName());
        record.setOwnedBy(req.getOwnedBy());
        record.setEnabled(req.getEnabled());
        record.setSortOrder(req.getSortOrder());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private SupportedModelRsp toRsp(SupportedModelDO record) {
        SupportedModelRsp rsp = new SupportedModelRsp();
        rsp.setId(record.getId());
        rsp.setModelId(record.getModelId());
        rsp.setDisplayName(record.getDisplayName());
        rsp.setOwnedBy(record.getOwnedBy());
        rsp.setEnabled(record.getEnabled());
        rsp.setSortOrder(record.getSortOrder());
        rsp.setVersionNo(record.getVersionNo());
        rsp.setCreateTime(record.getCreateTime());
        rsp.setUpdateTime(record.getUpdateTime());
        return rsp;
    }
}
