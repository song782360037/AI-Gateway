package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ModelRedirectConfigDO;
import com.code.aigateway.admin.model.req.ModelRedirectConfigAddReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigQueryReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ModelRedirectConfigRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.core.router.GlobPatternUtil;
import com.code.aigateway.core.router.MatchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型重定向配置管理服务实现
 *
 * <p>封装模型别名路由 CRUD 操作，负责引用完整性校验，
 * 并在写入成功后触发运行时配置刷新。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRedirectConfigServiceImpl implements IModelRedirectConfigService {

    private final ModelRedirectConfigMapper modelRedirectConfigMapper;
    private final ProviderConfigMapper providerConfigMapper;
    private final RuntimeConfigRefreshService runtimeConfigRefreshService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Long add(ModelRedirectConfigAddReq req) {
        // 校验匹配类型合法性，并验证 GLOB/REGEX 语法
        String matchType = validateAndNormalizeMatchType(req.getMatchType(), req.getAliasName());

        // 校验目标提供商是否存在
        validateProviderExists(req.getProviderCode());

        // 校验同一组合是否已存在（match_type 纳入唯一性判断）
        if (modelRedirectConfigMapper.existsRedirect(req.getAliasName(), matchType, req.getProviderCode(), req.getTargetModel()) > 0) {
            throw new BizException("CONFIG_CONFLICT",
                    "该重定向规则已存在: alias=" + req.getAliasName()
                            + ", matchType=" + matchType
                            + ", provider=" + req.getProviderCode()
                            + ", model=" + req.getTargetModel());
        }

        ModelRedirectConfigDO record = buildInsertRecord(req);
        record.setMatchType(matchType);
        record.setVersionNo(0L);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = modelRedirectConfigMapper.insert(record);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "新增重定向配置失败");
            }
            log.info("[重定向配置] 新增成功，id: {}，alias: {}，matchType: {}，provider: {}，targetModel: {}",
                    record.getId(), req.getAliasName(), matchType, req.getProviderCode(), req.getTargetModel());
        });

        // 数据库写入成功后必须刷新运行时配置；刷新失败时直接抛错，避免接口返回成功但路由仍使用旧快照。
        ensureRuntimeConfigReloaded("admin-add-redirect");
        return record.getId();
    }

    @Override
    public void update(ModelRedirectConfigUpdateReq req) {
        ModelRedirectConfigDO existing = modelRedirectConfigMapper.selectById(req.getId());
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "重定向配置不存在，id: " + req.getId());
        }

        // 校验匹配类型合法性，并验证 GLOB/REGEX 语法
        String matchType = validateAndNormalizeMatchType(req.getMatchType(), req.getAliasName());

        // 校验目标提供商是否存在
        validateProviderExists(req.getProviderCode());

        // 组合唯一性校验：排除自身记录（match_type 纳入唯一性判断）
        String existingMatchType = existing.getMatchType() != null ? existing.getMatchType() : "EXACT";
        if (!existing.getAliasName().equals(req.getAliasName())
                || !existingMatchType.equals(matchType)
                || !existing.getProviderCode().equals(req.getProviderCode())
                || !existing.getTargetModel().equals(req.getTargetModel())) {
            if (modelRedirectConfigMapper.existsRedirect(req.getAliasName(), matchType, req.getProviderCode(), req.getTargetModel()) > 0) {
                throw new BizException("CONFIG_CONFLICT",
                        "该重定向规则已存在: alias=" + req.getAliasName()
                                + ", matchType=" + matchType
                                + ", provider=" + req.getProviderCode()
                                + ", model=" + req.getTargetModel());
            }
        }

        ModelRedirectConfigDO record = buildUpdateRecord(req);
        record.setMatchType(matchType);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = modelRedirectConfigMapper.updateById(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                        "数据已被其他请求修改，请刷新后重试，id: " + req.getId());
            }
            log.info("[重定向配置] 更新成功，id: {}，alias: {}，matchType: {}，provider: {}",
                    req.getId(), req.getAliasName(), matchType, req.getProviderCode());
        });

        // 更新成功后必须同步刷新运行时快照，避免新配置无法立即生效。
        ensureRuntimeConfigReloaded("admin-update-redirect");
    }

    @Override
    public void delete(Long id) {
        ModelRedirectConfigDO existing = modelRedirectConfigMapper.selectById(id);
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "重定向配置不存在，id: " + id);
        }

        transactionTemplate.executeWithoutResult(status -> {
            int rows = modelRedirectConfigMapper.softDeleteById(id);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "删除重定向配置失败，id: " + id);
            }
            log.info("[重定向配置] 删除成功，id: {}，alias: {}，provider: {}",
                    id, existing.getAliasName(), existing.getProviderCode());
        });

        // 删除成功后必须刷新运行时快照，避免已删除规则继续参与路由。
        ensureRuntimeConfigReloaded("admin-delete-redirect");
    }

    @Override
    public ModelRedirectConfigRsp getById(Long id) {
        ModelRedirectConfigDO record = modelRedirectConfigMapper.selectById(id);
        if (record == null) {
            throw new BizException("CONFIG_NOT_FOUND", "重定向配置不存在，id: " + id);
        }
        return toRsp(record);
    }

    @Override
    public PageResult<ModelRedirectConfigRsp> list(ModelRedirectConfigQueryReq req) {
        int offset = (req.getPage() - 1) * req.getPageSize();
        List<ModelRedirectConfigDO> records = modelRedirectConfigMapper.selectList(
                req.getAliasName(), req.getProviderCode(), req.getTargetModel(), req.getEnabled(), offset, req.getPageSize());
        long total = modelRedirectConfigMapper.countList(
                req.getAliasName(), req.getProviderCode(), req.getTargetModel(), req.getEnabled());

        List<ModelRedirectConfigRsp> rspList = records.stream().map(this::toRsp).toList();
        return PageResult.of(rspList, total, req.getPage(), req.getPageSize());
    }

    @Override
    public void toggle(Long id, Long versionNo) {
        // 先查询当前记录，获取当前启用状态
        ModelRedirectConfigDO existing = modelRedirectConfigMapper.selectById(id);
        if (existing == null) {
            throw new BizException("CONFIG_NOT_FOUND", "路由规则不存在，id: " + id);
        }

        // 构建仅更新 enabled 字段的记录，使用乐观锁保证并发安全
        ModelRedirectConfigDO record = new ModelRedirectConfigDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setEnabled(!existing.getEnabled());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        int rows = modelRedirectConfigMapper.updateEnabled(record);
        if (rows <= 0) {
            throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                    "数据已被其他请求修改，请刷新后重试，id: " + id);
        }
        log.info("[模型路由规则] 状态切换成功，id: {}，enabled: {} -> {}",
                id, existing.getEnabled(), !existing.getEnabled());

        // 状态变更后必须刷新运行时快照，使变更立即生效
        ensureRuntimeConfigReloaded("admin-toggle-route");
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

    /**
     * 校验目标提供商是否存在且未被删除，不存在则抛出业务异常。
     */
    private void validateProviderExists(String providerCode) {
        if (providerConfigMapper.selectByProviderCode(providerCode) == null) {
            throw new BizException("CONFIG_NOT_FOUND", "目标提供商不存在: " + providerCode);
        }
    }

    private ModelRedirectConfigDO buildInsertRecord(ModelRedirectConfigAddReq req) {
        ModelRedirectConfigDO record = new ModelRedirectConfigDO();
        record.setAliasName(req.getAliasName());
        record.setProviderCode(req.getProviderCode());
        record.setTargetModel(req.getTargetModel());
        record.setEnabled(req.getEnabled());
        record.setDeleted(false);
        record.setCreator("");
        record.setUpdater("");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private ModelRedirectConfigDO buildUpdateRecord(ModelRedirectConfigUpdateReq req) {
        ModelRedirectConfigDO record = new ModelRedirectConfigDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        record.setAliasName(req.getAliasName());
        record.setProviderCode(req.getProviderCode());
        record.setTargetModel(req.getTargetModel());
        record.setEnabled(req.getEnabled());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private ModelRedirectConfigRsp toRsp(ModelRedirectConfigDO record) {
        ModelRedirectConfigRsp rsp = new ModelRedirectConfigRsp();
        rsp.setId(record.getId());
        rsp.setAliasName(record.getAliasName());
        rsp.setMatchType(record.getMatchType() != null ? record.getMatchType() : "EXACT");
        rsp.setProviderCode(record.getProviderCode());
        rsp.setTargetModel(record.getTargetModel());
        rsp.setEnabled(record.getEnabled());
        rsp.setVersionNo(record.getVersionNo());
        rsp.setCreateTime(record.getCreateTime());
        rsp.setUpdateTime(record.getUpdateTime());
        return rsp;
    }

    /**
     * 校验并规范化匹配类型。
     *
     * <p>校验 matchType 是否为合法枚举值；
     * 对于 GLOB 和 REGEX 类型，额外验证 aliasName 的语法是否合法。</p>
     *
     * <p>注意：BizException 在内部 try 中抛出后不会被外层 IllegalArgumentException 捕获，
     * 因为 BizException 不是 IllegalArgumentException 的子类。</p>
     */
    private String validateAndNormalizeMatchType(String matchType, String aliasName) {
        String normalized = (matchType != null && !matchType.isBlank()) ? matchType.toUpperCase() : "EXACT";

        MatchType mt;
        try {
            mt = MatchType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BizException("INVALID_MATCH_TYPE", "不支持的匹配类型: " + matchType);
        }

        // 对 GLOB 和 REGEX 类型进行语法预校验
        if (mt == MatchType.GLOB) {
            String regex = GlobPatternUtil.globToRegex(aliasName);
            try {
                java.util.regex.Pattern.compile(regex);
            } catch (java.util.regex.PatternSyntaxException ex) {
                throw new BizException("INVALID_PATTERN", "通配符语法错误: " + ex.getMessage());
            }
        } else if (mt == MatchType.REGEX) {
            try {
                java.util.regex.Pattern.compile(aliasName);
            } catch (java.util.regex.PatternSyntaxException ex) {
                throw new BizException("INVALID_PATTERN", "正则表达式语法错误: " + ex.getMessage());
            }
        }

        return normalized;
    }
}
