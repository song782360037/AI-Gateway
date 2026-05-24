package com.code.aigateway.admin.service.impl;

import com.code.aigateway.admin.mapper.ProviderApiKeyMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ProviderApiKeyDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.ProviderApiKeyAddReq;
import com.code.aigateway.admin.model.req.ProviderApiKeyUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderApiKeyRsp;
import com.code.aigateway.admin.service.IProviderApiKeyService;
import com.code.aigateway.admin.service.RuntimeConfigRefreshService;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供商 API Key 管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderApiKeyServiceImpl implements IProviderApiKeyService {

    private final ProviderApiKeyMapper providerApiKeyMapper;
    private final ProviderConfigMapper providerConfigMapper;
    private final ApiKeyEncryptor apiKeyEncryptor;
    private final RuntimeConfigRefreshService runtimeConfigRefreshService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<ProviderApiKeyRsp> list(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new BizException("BAD_REQUEST", "提供商编码不能为空");
        }
        ProviderConfigDO config = providerConfigMapper.selectByProviderCode(providerCode);
        if (config == null) {
            throw new BizException("BAD_REQUEST", "提供商不存在: " + providerCode);
        }
        List<ProviderApiKeyDO> keys = providerApiKeyMapper.selectByProviderCode(providerCode);
        return keys.stream().map(this::toRsp).toList();
    }

    @Override
    public void add(ProviderApiKeyAddReq req) {
        ProviderConfigDO config = providerConfigMapper.selectByProviderCode(req.getProviderCode());
        if (config == null) {
            throw new BizException("BAD_REQUEST", "提供商不存在: " + req.getProviderCode());
        }

        // 加密 API Key
        ApiKeyEncryptor.EncryptResult encryptResult = apiKeyEncryptor.encrypt(req.getApiKey());
        String apiKeyPrefix = apiKeyEncryptor.mask(req.getApiKey());

        // 通过脱敏前缀检测重复 Key（AES-GCM 随机 IV 导致相同明文密文不同，故无法按密文查重）
        int duplicateCount = providerApiKeyMapper.countByProviderCodeAndPrefix(req.getProviderCode(), apiKeyPrefix);
        if (duplicateCount > 0) {
            throw new BizException("BAD_REQUEST", "该 Provider 下已存在相同前缀的 API Key（" + apiKeyPrefix + "），请确认是否重复添加");
        }

        ProviderApiKeyDO record = new ProviderApiKeyDO();
        record.setProviderCode(req.getProviderCode());
        record.setApiKeyCiphertext(encryptResult.ciphertext());
        record.setApiKeyIv(encryptResult.iv());
        record.setApiKeyPrefix(apiKeyPrefix);
        record.setRemark(req.getRemark());
        record.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        record.setWeight(req.getWeight() != null ? req.getWeight() : 100);
        record.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        record.setVersionNo(0L);
        record.setCreator("system");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdater("system");
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);

        transactionTemplate.executeWithoutResult(status -> {
            providerApiKeyMapper.insert(record);
        });

        runtimeConfigRefreshService.reloadFromDb("admin-apikey-add");
        log.info("[API Key管理] 新增成功, providerCode: {}, keyPrefix: {}", req.getProviderCode(), apiKeyPrefix);
    }

    @Override
    public void update(ProviderApiKeyUpdateReq req) {
        ProviderApiKeyDO existing = providerApiKeyMapper.selectById(req.getId());
        if (existing == null) {
            throw new BizException("BAD_REQUEST", "API Key 不存在");
        }

        ProviderApiKeyDO record = new ProviderApiKeyDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        record.setRemark(req.getRemark() != null ? req.getRemark() : existing.getRemark());
        record.setEnabled(req.getEnabled() != null ? req.getEnabled() : existing.getEnabled());
        record.setWeight(req.getWeight() != null ? req.getWeight() : existing.getWeight());
        record.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : existing.getSortOrder());
        record.setUpdater("system");
        record.setUpdateTime(LocalDateTime.now());

        int rows = transactionTemplate.execute(status -> providerApiKeyMapper.updateById(record));
        if (rows == 0) {
            throw new BizException("BAD_REQUEST", "更新失败，数据可能已被其他操作修改，请刷新后重试");
        }

        runtimeConfigRefreshService.reloadFromDb("admin-apikey-update");
        log.info("[API Key管理] 更新成功, id: {}", req.getId());
    }

    @Override
    public void delete(Long id) {
        ProviderApiKeyDO existing = providerApiKeyMapper.selectById(id);
        if (existing == null) {
            throw new BizException("BAD_REQUEST", "API Key 不存在");
        }

        // 在事务中加锁并检查，保证原子性
        // 注意：必须基于锁内的实时状态判断，不能依赖事务外的 existing.getEnabled()（可能过期）
        transactionTemplate.executeWithoutResult(status -> {
            providerApiKeyMapper.lockIdsForUpdate(existing.getProviderCode());
            int enabledCount = providerApiKeyMapper.countEnabledByProviderCode(existing.getProviderCode());
            if (enabledCount <= 1) {
                throw new BizException("BAD_REQUEST", "不能删除最后一个启用的 API Key，至少保留一个可用 Key");
            }
            providerApiKeyMapper.softDeleteById(id);
        });

        runtimeConfigRefreshService.reloadFromDb("admin-apikey-delete");
        log.info("[API Key管理] 删除成功, id: {}, providerCode: {}", id, existing.getProviderCode());
    }

    @Override
    public void toggle(Long id, Long versionNo, boolean enabled) {
        ProviderApiKeyDO existing = providerApiKeyMapper.selectById(id);
        if (existing == null) {
            throw new BizException("BAD_REQUEST", "API Key 不存在");
        }

        ProviderApiKeyDO record = new ProviderApiKeyDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setEnabled(enabled);
        record.setUpdater("system");
        record.setUpdateTime(LocalDateTime.now());

        // 禁用时：锁检查和更新必须在同一事务中，避免 TOCTOU 竞态导致所有 Key 被禁用
        if (!enabled) {
            transactionTemplate.executeWithoutResult(status -> {
                providerApiKeyMapper.lockIdsForUpdate(existing.getProviderCode());
                int enabledCount = providerApiKeyMapper.countEnabledByProviderCode(existing.getProviderCode());
                if (enabledCount <= 1) {
                    throw new BizException("BAD_REQUEST", "不能禁用最后一个启用的 API Key，至少保留一个可用 Key");
                }
                int rows = providerApiKeyMapper.updateEnabled(record);
                if (rows == 0) {
                    throw new BizException("BAD_REQUEST", "操作失败，数据可能已被其他操作修改，请刷新后重试");
                }
            });
        } else {
            int rows = transactionTemplate.execute(status -> providerApiKeyMapper.updateEnabled(record));
            if (rows == 0) {
                throw new BizException("BAD_REQUEST", "操作失败，数据可能已被其他操作修改，请刷新后重试");
            }
        }

        runtimeConfigRefreshService.reloadFromDb("admin-apikey-toggle");
        log.info("[API Key管理] 切换状态成功, id: {}, enabled: {}", id, enabled);
    }

    private ProviderApiKeyRsp toRsp(ProviderApiKeyDO entity) {
        ProviderApiKeyRsp rsp = new ProviderApiKeyRsp();
        rsp.setId(entity.getId());
        rsp.setProviderCode(entity.getProviderCode());
        rsp.setApiKeyMasked(entity.getApiKeyPrefix());
        rsp.setRemark(entity.getRemark());
        rsp.setEnabled(entity.getEnabled());
        rsp.setWeight(entity.getWeight());
        rsp.setSortOrder(entity.getSortOrder());
        rsp.setVersionNo(entity.getVersionNo());
        rsp.setCreateTime(entity.getCreateTime());
        rsp.setUpdateTime(entity.getUpdateTime());
        return rsp;
    }
}
