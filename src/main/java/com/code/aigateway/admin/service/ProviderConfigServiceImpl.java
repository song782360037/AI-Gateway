package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.admin.model.req.ProviderConfigQueryReq;
import com.code.aigateway.admin.model.req.ProviderConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderConfigRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供商配置管理服务实现
 *
 * <p>封装提供商 CRUD 操作，负责 API Key 加密存储与脱敏展示，
 * 并在写入成功后触发运行时配置刷新。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConfigServiceImpl implements IProviderConfigService {

    private final ProviderConfigMapper providerConfigMapper;
    private final ApiKeyEncryptor apiKeyEncryptor;
    private final RuntimeConfigRefreshService runtimeConfigRefreshService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Long add(ProviderConfigAddReq req) {
        // 校验业务编码唯一性
        if (providerConfigMapper.existsByProviderCode(req.getProviderCode()) > 0) {
            throw new BizException("CONFIG_CONFLICT", "提供商编码已存在: " + req.getProviderCode());
        }

        // 加密 API Key，明文不入库
        ApiKeyEncryptor.EncryptResult encryptResult = apiKeyEncryptor.encrypt(req.getApiKey());
        ProviderConfigDO record = buildInsertRecord(req, encryptResult);
        record.setVersionNo(0L);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = providerConfigMapper.insert(record);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "新增提供商配置失败");
            }
            log.info("[提供商配置] 新增成功，id: {}，providerCode: {}", record.getId(), req.getProviderCode());
        });

        // 数据库写入成功后必须刷新运行时配置；刷新失败时直接抛错，避免接口返回成功但路由仍使用旧快照。
        ensureRuntimeConfigReloaded("admin-add-provider");
        return record.getId();
    }

    @Override
    public void update(ProviderConfigUpdateReq req) {
        // 先查出当前记录，用于获取已有密文（更新时可选择不修改 API Key）
        ProviderConfigDO existing = providerConfigMapper.selectById(req.getId());
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "提供商配置不存在，id: " + req.getId());
        }

        ProviderConfigDO record = buildUpdateRecord(req);

        // 如果传入了新的 API Key，则重新加密；否则沿用已有的密文和 IV
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            ApiKeyEncryptor.EncryptResult encryptResult = apiKeyEncryptor.encrypt(req.getApiKey());
            record.setApiKeyCiphertext(encryptResult.ciphertext());
            record.setApiKeyIv(encryptResult.iv());
        } else {
            record.setApiKeyCiphertext(existing.getApiKeyCiphertext());
            record.setApiKeyIv(existing.getApiKeyIv());
        }

        // 编码变更时检查目标编码是否已被占用
        if (!existing.getProviderCode().equals(req.getProviderCode())
                && providerConfigMapper.existsByProviderCode(req.getProviderCode()) > 0) {
            throw new BizException("CONFIG_CONFLICT", "提供商编码已存在: " + req.getProviderCode());
        }

        transactionTemplate.executeWithoutResult(status -> {
            int rows = providerConfigMapper.updateById(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                        "数据已被其他请求修改，请刷新后重试，id: " + req.getId());
            }
            log.info("[提供商配置] 更新成功，id: {}，providerCode: {}", req.getId(), req.getProviderCode());
        });

        // 更新成功后必须同步刷新运行时快照，避免新配置无法立即生效。
        ensureRuntimeConfigReloaded("admin-update-provider");
    }

    @Override
    public void delete(Long id) {
        ProviderConfigDO existing = providerConfigMapper.selectById(id);
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "提供商配置不存在，id: " + id);
        }

        // 删除前校验是否存在引用此 provider 的启用中重定向规则
        int redirectCount = providerConfigMapper.existsEnabledRedirectByProviderCode(existing.getProviderCode());
        if (redirectCount > 0) {
            throw new BizException("CONFIG_CONFLICT",
                    "当前提供商仍被 " + redirectCount + " 条启用中的重定向规则引用，请先停用或删除关联规则");
        }

        transactionTemplate.executeWithoutResult(status -> {
            int rows = providerConfigMapper.softDeleteById(id);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "删除提供商配置失败，id: " + id);
            }
            log.info("[提供商配置] 删除成功，id: {}，providerCode: {}", id, existing.getProviderCode());
        });

        // 删除成功后必须刷新运行时快照，避免已删除配置继续参与路由。
        ensureRuntimeConfigReloaded("admin-delete-provider");
    }

    @Override
    public ProviderConfigRsp getById(Long id) {
        ProviderConfigDO record = providerConfigMapper.selectById(id);
        if (record == null) {
            throw new BizException("CONFIG_NOT_FOUND", "提供商配置不存在，id: " + id);
        }
        return toRsp(record);
    }

    @Override
    public PageResult<ProviderConfigRsp> list(ProviderConfigQueryReq req) {
        int offset = (req.getPage() - 1) * req.getPageSize();
        List<ProviderConfigDO> records = providerConfigMapper.selectList(
                req.getProviderCode(), req.getProviderType(), req.getEnabled(), offset, req.getPageSize());
        long total = providerConfigMapper.countList(
                req.getProviderCode(), req.getProviderType(), req.getEnabled());

        List<ProviderConfigRsp> rspList = records.stream().map(this::toRsp).toList();
        return PageResult.of(rspList, total, req.getPage(), req.getPageSize());
    }

    // ==================== 内部方法 ====================

    /**
     * 校验运行时配置是否刷新成功。
     *
     * <p>数据库写入成功但运行时快照刷新失败时，
     * 需要显式返回错误，提醒调用方当前配置尚未真正生效。</p>
     */
    private void ensureRuntimeConfigReloaded(String source) {
        if (runtimeConfigRefreshService.reloadFromDb(source)) {
            return;
        }
        throw new BizException("CONFIG_REFRESH_FAILED", "运行时配置刷新失败，请稍后重试");
    }

    private ProviderConfigDO buildInsertRecord(ProviderConfigAddReq req, ApiKeyEncryptor.EncryptResult encryptResult) {
        ProviderConfigDO record = new ProviderConfigDO();
        record.setProviderCode(req.getProviderCode());
        record.setProviderType(req.getProviderType());
        record.setDisplayName(req.getDisplayName());
        record.setEnabled(req.getEnabled());
        record.setBaseUrl(req.getBaseUrl());
        record.setApiKeyCiphertext(encryptResult.ciphertext());
        record.setApiKeyIv(encryptResult.iv());
        record.setApiVersion(req.getApiVersion());
        record.setTimeoutSeconds(req.getTimeoutSeconds());
        record.setPriority(req.getPriority());
        record.setExtConfigJson(req.getExtConfigJson());
        record.setDeleted(false);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private ProviderConfigDO buildUpdateRecord(ProviderConfigUpdateReq req) {
        ProviderConfigDO record = new ProviderConfigDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        record.setProviderCode(req.getProviderCode());
        record.setProviderType(req.getProviderType());
        record.setDisplayName(req.getDisplayName());
        record.setEnabled(req.getEnabled());
        record.setBaseUrl(req.getBaseUrl());
        record.setApiVersion(req.getApiVersion());
        record.setTimeoutSeconds(req.getTimeoutSeconds());
        record.setPriority(req.getPriority());
        record.setExtConfigJson(req.getExtConfigJson());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    /**
     * 将数据库记录转换为脱敏后的响应对象。
     *
     * <p>API Key 通过解密后做掩码处理，避免敏感信息明文回显。</p>
     */
    private ProviderConfigRsp toRsp(ProviderConfigDO record) {
        ProviderConfigRsp rsp = new ProviderConfigRsp();
        rsp.setId(record.getId());
        rsp.setProviderCode(record.getProviderCode());
        rsp.setProviderType(record.getProviderType());
        rsp.setDisplayName(record.getDisplayName());
        rsp.setEnabled(record.getEnabled());
        rsp.setBaseUrl(record.getBaseUrl());
        rsp.setApiVersion(record.getApiVersion());
        rsp.setTimeoutSeconds(record.getTimeoutSeconds());
        rsp.setPriority(record.getPriority());
        rsp.setExtConfigJson(record.getExtConfigJson());
        rsp.setVersionNo(record.getVersionNo());
        rsp.setCreateTime(record.getCreateTime());
        rsp.setUpdateTime(record.getUpdateTime());

        // 尝试解密 API Key 做掩码展示；若解密失败则返回统一掩码值
        try {
            String plainKey = apiKeyEncryptor.decrypt(record.getApiKeyIv(), record.getApiKeyCiphertext());
            rsp.setApiKeyMasked(apiKeyEncryptor.mask(plainKey));
        } catch (Exception ex) {
            log.warn("[提供商配置] API Key 解密失败，id: {}，返回掩码值", record.getId());
            rsp.setApiKeyMasked("****");
        }
        return rsp;
    }
}
