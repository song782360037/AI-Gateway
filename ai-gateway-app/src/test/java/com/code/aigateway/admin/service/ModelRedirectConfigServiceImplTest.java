package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ModelRedirectConfigDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.ModelRedirectConfigUpdateReq;
import com.code.aigateway.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelRedirectConfigServiceImpl 单元测试
 *
 * <p>覆盖更新路由规则时的唯一性校验逻辑，确保 excludeId 正确排除自身记录。</p>
 */
class ModelRedirectConfigServiceImplTest {

    private ModelRedirectConfigMapper modelRedirectConfigMapper;
    private ProviderConfigMapper providerConfigMapper;
    private RuntimeConfigRefreshService runtimeConfigRefreshService;
    private ModelRedirectConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        modelRedirectConfigMapper = Mockito.mock(ModelRedirectConfigMapper.class);
        providerConfigMapper = Mockito.mock(ProviderConfigMapper.class);
        runtimeConfigRefreshService = Mockito.mock(RuntimeConfigRefreshService.class);

        // 构造真实 TransactionTemplate，callback 直接执行（不走真实事务）
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
        TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);

        service = new ModelRedirectConfigServiceImpl(
                modelRedirectConfigMapper, providerConfigMapper,
                runtimeConfigRefreshService, transactionTemplate);
    }

    // ==================== update 唯一性校验 ====================

    @Test
    @DisplayName("更新时仅修改 enabled，不改关键字段 → 不应报冲突")
    void update_onlyEnabledChanged_shouldNotConflict() {
        // 已有记录：id=1, alias=gpt-4o, provider=openai, model=gpt-4o-2024-08-06
        ModelRedirectConfigDO existing = buildExistingDO(1L, "gpt-4o", "EXACT", "openai", "gpt-4o-2024-08-06", true);
        when(modelRedirectConfigMapper.selectById(1L)).thenReturn(existing);
        mockProviderExists("openai");
        // existsRedirect 不应被调用（关键字段未变更，但新逻辑会调用，返回 0 表示无冲突）
        when(modelRedirectConfigMapper.existsRedirect(
                eq("gpt-4o"), eq("EXACT"), eq("openai"), eq("gpt-4o-2024-08-06"), eq(1L)))
                .thenReturn(0);
        when(modelRedirectConfigMapper.updateById(any())).thenReturn(1);
        when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

        // 请求仅将 enabled 改为 false，关键字段不变
        ModelRedirectConfigUpdateReq req = buildUpdateReq(1L, 1L, "gpt-4o", "EXACT", "openai", "gpt-4o-2024-08-06", false);

        assertDoesNotThrow(() -> service.update(req));
        verify(modelRedirectConfigMapper).updateById(any());
    }

    @Test
    @DisplayName("更新时修改 aliasName 但不存在其他相同组合 → 不应报冲突")
    void update_aliasChanged_noDuplicate_shouldNotConflict() {
        // 已有记录：id=1
        ModelRedirectConfigDO existing = buildExistingDO(1L, "gpt-4o", "EXACT", "openai", "gpt-4o-2024-08-06", true);
        when(modelRedirectConfigMapper.selectById(1L)).thenReturn(existing);
        mockProviderExists("openai");
        // 新的 alias=gpt-4o-mini，查询返回 0 表示不存在重复
        when(modelRedirectConfigMapper.existsRedirect(
                eq("gpt-4o-mini"), eq("EXACT"), eq("openai"), eq("gpt-4o-2024-08-06"), eq(1L)))
                .thenReturn(0);
        when(modelRedirectConfigMapper.updateById(any())).thenReturn(1);
        when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

        // aliasName 从 gpt-4o 改为 gpt-4o-mini
        ModelRedirectConfigUpdateReq req = buildUpdateReq(1L, 1L, "gpt-4o-mini", "EXACT", "openai", "gpt-4o-2024-08-06", true);

        assertDoesNotThrow(() -> service.update(req));
        verify(modelRedirectConfigMapper).updateById(any());
    }

    @Test
    @DisplayName("更新时修改 providerCode 与另一条已有记录冲突 → 应报冲突")
    void update_providerChanged_duplicateExists_shouldThrowConflict() {
        // 已有记录：id=1
        ModelRedirectConfigDO existing = buildExistingDO(1L, "gpt-4o", "EXACT", "openai", "gpt-4o-2024-08-06", true);
        when(modelRedirectConfigMapper.selectById(1L)).thenReturn(existing);
        mockProviderExists("anthropic");
        // provider 改为 anthropic 后，existsRedirect 返回 1（排除自身 id=1 后仍有冲突）
        when(modelRedirectConfigMapper.existsRedirect(
                eq("gpt-4o"), eq("EXACT"), eq("anthropic"), eq("gpt-4o-2024-08-06"), eq(1L)))
                .thenReturn(1);

        // providerCode 从 openai 改为 anthropic（该组合已被 id=2 占用）
        ModelRedirectConfigUpdateReq req = buildUpdateReq(1L, 1L, "gpt-4o", "EXACT", "anthropic", "gpt-4o-2024-08-06", true);

        BizException ex = assertThrows(BizException.class, () -> service.update(req));
        assertEquals("CONFIG_CONFLICT", ex.getCode());
        // 不应调用 updateById
        verify(modelRedirectConfigMapper, never()).updateById(any());
    }

    // ==================== 辅助方法 ====================

    private ModelRedirectConfigDO buildExistingDO(Long id, String aliasName, String matchType,
                                                   String providerCode, String targetModel, Boolean enabled) {
        ModelRedirectConfigDO doObj = new ModelRedirectConfigDO();
        doObj.setId(id);
        doObj.setAliasName(aliasName);
        doObj.setMatchType(matchType);
        doObj.setProviderCode(providerCode);
        doObj.setTargetModel(targetModel);
        doObj.setEnabled(enabled);
        doObj.setVersionNo(1L);
        return doObj;
    }

    private ModelRedirectConfigUpdateReq buildUpdateReq(Long id, Long versionNo, String aliasName,
                                                         String matchType, String providerCode,
                                                         String targetModel, Boolean enabled) {
        ModelRedirectConfigUpdateReq req = new ModelRedirectConfigUpdateReq();
        req.setId(id);
        req.setVersionNo(versionNo);
        req.setAliasName(aliasName);
        req.setMatchType(matchType);
        req.setProviderCode(providerCode);
        req.setTargetModel(targetModel);
        req.setEnabled(enabled);
        return req;
    }

    private void mockProviderExists(String providerCode) {
        ProviderConfigDO provider = new ProviderConfigDO();
        provider.setProviderCode(providerCode);
        when(providerConfigMapper.selectByProviderCode(providerCode)).thenReturn(provider);
    }
}
