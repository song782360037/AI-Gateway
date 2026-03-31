package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ApiKeyConfigMapper;
import com.code.aigateway.admin.model.dataobject.ApiKeyConfigDO;
import com.code.aigateway.admin.model.req.ApiKeyConfigAddReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigQueryReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigCreateRsp;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * API Key 配置管理服务实现
 *
 * <p>核心安全设计：
 * <ul>
 *   <li>原始 key（ak-xxx）永不落库，仅存储 SHA-256 哈希</li>
 *   <li>key 前缀（前 7 字符）单独存储，供管理列表展示</li>
 *   <li>完整明文 key 仅在创建时返回一次</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyConfigServiceImpl implements IApiKeyConfigService {

    /** API Key 前缀标识 */
    private static final String KEY_PREFIX = "ak-";
    /** 随机部分长度（32 个 hex 字符 = 16 字节） */
    private static final int RANDOM_BYTES = 16;
    /** 前缀展示长度（含 ak- 前缀共 7 字符，如 ak-a1b2c） */
    private static final int PREFIX_DISPLAY_LEN = 7;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyConfigMapper apiKeyConfigMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ApiKeyConfigCreateRsp add(ApiKeyConfigAddReq req) {
        // 生成随机 key：ak- + 32 hex 字符
        String rawKey = generateRawKey();
        String keyHash = sha256Hex(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(PREFIX_DISPLAY_LEN, rawKey.length()));

        ApiKeyConfigDO record = new ApiKeyConfigDO();
        record.setKeyHash(keyHash);
        record.setKeyPrefix(keyPrefix);
        record.setName(req.getName());
        record.setStatus(defaultIfBlank(req.getStatus(), "ACTIVE"));
        record.setDailyLimit(req.getDailyLimit());
        record.setTotalLimit(req.getTotalLimit());
        record.setUsedCount(0L);
        record.setExpireTime(req.getExpireTime());
        record.setVersionNo(0L);
        record.setCreator("");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = apiKeyConfigMapper.insert(record);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "新增 API Key 配置失败");
            }
            log.info("[API Key配置] 新增成功，id: {}，name: {}", record.getId(), req.getName());
        });

        // 构建响应，包含完整明文 key（仅此一次）
        ApiKeyConfigCreateRsp rsp = new ApiKeyConfigCreateRsp();
        fillRsp(record, rsp);
        rsp.setApiKey(rawKey);
        return rsp;
    }

    @Override
    public void update(ApiKeyConfigUpdateReq req) {
        ApiKeyConfigDO existing = apiKeyConfigMapper.selectById(req.getId());
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "API Key 配置不存在，id: " + req.getId());
        }

        ApiKeyConfigDO record = new ApiKeyConfigDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        // 仅更新非 null 字段，保留原值
        record.setName(req.getName() != null ? req.getName() : existing.getName());
        record.setStatus(req.getStatus() != null ? req.getStatus() : existing.getStatus());
        record.setDailyLimit(req.getDailyLimit());
        record.setTotalLimit(req.getTotalLimit());
        record.setExpireTime(req.getExpireTime());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            int rows = apiKeyConfigMapper.updateById(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                        "数据已被其他请求修改，请刷新后重试，id: " + req.getId());
            }
            log.info("[API Key配置] 更新成功，id: {}", req.getId());
        });
    }

    @Override
    public void delete(Long id) {
        ApiKeyConfigDO existing = apiKeyConfigMapper.selectById(id);
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "API Key 配置不存在，id: " + id);
        }

        transactionTemplate.executeWithoutResult(status -> {
            int rows = apiKeyConfigMapper.softDeleteById(id);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "删除 API Key 配置失败，id: " + id);
            }
            log.info("[API Key配置] 删除成功，id: {}，name: {}", id, existing.getName());
        });
    }

    @Override
    public ApiKeyConfigRsp getById(Long id) {
        ApiKeyConfigDO record = apiKeyConfigMapper.selectById(id);
        if (record == null) {
            throw new BizException("CONFIG_NOT_FOUND", "API Key 配置不存在，id: " + id);
        }
        return toRsp(record);
    }

    @Override
    public PageResult<ApiKeyConfigRsp> list(ApiKeyConfigQueryReq req) {
        int offset = (req.getPage() - 1) * req.getPageSize();
        List<ApiKeyConfigDO> records = apiKeyConfigMapper.selectList(
                req.getName(), req.getStatus(), offset, req.getPageSize());
        long total = apiKeyConfigMapper.countList(req.getName(), req.getStatus());

        List<ApiKeyConfigRsp> rspList = records.stream().map(this::toRsp).toList();
        return PageResult.of(rspList, total, req.getPage(), req.getPageSize());
    }

    // ==================== 内部方法 ====================

    /**
     * 生成随机 API Key：ak- + 32 个 hex 字符
     */
    private String generateRawKey() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return KEY_PREFIX + HexFormat.of().formatHex(randomBytes);
    }

    /**
     * SHA-256 哈希并转为 hex 字符串
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new BizException("HASH_ERROR", "SHA-256 哈希计算失败");
        }
    }

    /**
     * 将 DO 转换为响应对象（不含完整 key）
     */
    private ApiKeyConfigRsp toRsp(ApiKeyConfigDO record) {
        ApiKeyConfigRsp rsp = new ApiKeyConfigRsp();
        fillRsp(record, rsp);
        return rsp;
    }

    /**
     * 填充公共响应字段
     */
    private void fillRsp(ApiKeyConfigDO record, ApiKeyConfigRsp rsp) {
        rsp.setId(record.getId());
        rsp.setKeyPrefix(record.getKeyPrefix());
        rsp.setName(record.getName());
        rsp.setStatus(record.getStatus());
        rsp.setDailyLimit(record.getDailyLimit());
        rsp.setTotalLimit(record.getTotalLimit());
        rsp.setUsedCount(record.getUsedCount());
        rsp.setExpireTime(record.getExpireTime());
        rsp.setVersionNo(record.getVersionNo());
        rsp.setCreateTime(record.getCreateTime());
        rsp.setUpdateTime(record.getUpdateTime());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
