package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ProviderApiKeyMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ProviderApiKeyDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.ProviderApiKeyAddReq;
import com.code.aigateway.admin.model.req.ProviderApiKeyUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderApiKeyRsp;
import com.code.aigateway.admin.service.impl.ProviderApiKeyServiceImpl;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProviderApiKeyServiceImpl 单元测试
 *
 * <p>覆盖：新增、重复检测、Provider 不存在、乐观锁更新、最后 Key 保护、列表查询。</p>
 */
class ProviderApiKeyServiceImplTest {

    private ProviderApiKeyMapper providerApiKeyMapper;
    private ProviderConfigMapper providerConfigMapper;
    private ApiKeyEncryptor apiKeyEncryptor;
    private RuntimeConfigRefreshService runtimeConfigRefreshService;
    private TransactionTemplate transactionTemplate;
    private ProviderApiKeyServiceImpl service;

    @BeforeEach
    void setUp() {
        providerApiKeyMapper = Mockito.mock(ProviderApiKeyMapper.class);
        providerConfigMapper = Mockito.mock(ProviderConfigMapper.class);
        apiKeyEncryptor = Mockito.mock(ApiKeyEncryptor.class);
        runtimeConfigRefreshService = Mockito.mock(RuntimeConfigRefreshService.class);

        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
        transactionTemplate = new TransactionTemplate(txManager);

        service = new ProviderApiKeyServiceImpl(
                providerApiKeyMapper, providerConfigMapper, apiKeyEncryptor,
                runtimeConfigRefreshService, transactionTemplate);
    }

    // ==================== list ====================

    @Nested
    @DisplayName("list - 查询 API Key 列表")
    class ListTests {

        @Test
        @DisplayName("providerCode 为空时抛出 BizException")
        void list_blankProviderCode_shouldThrow() {
            assertThrows(BizException.class, () -> service.list(""));
        }

        @Test
        @DisplayName("Provider 不存在时抛出 BizException")
        void list_providerNotFound_shouldThrow() {
            when(providerConfigMapper.selectByProviderCode("not-exist")).thenReturn(null);

            assertThrows(BizException.class, () -> service.list("not-exist"));
        }

        @Test
        @DisplayName("正常查询返回 Key 列表（脱敏）")
        void list_success_shouldReturnMaskedKeys() {
            when(providerConfigMapper.selectByProviderCode("openai")).thenReturn(new ProviderConfigDO());
            when(providerApiKeyMapper.selectByProviderCode("openai")).thenReturn(List.of(buildKeyDO(1L)));

            List<ProviderApiKeyRsp> result = service.list("openai");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("sk-test-****5678", result.get(0).getApiKeyMasked());
        }
    }

    // ==================== add ====================

    @Nested
    @DisplayName("add - 新增 API Key")
    class AddTests {

        @Test
        @DisplayName("Provider 不存在时抛出 BizException")
        void add_providerNotFound_shouldThrow() {
            when(providerConfigMapper.selectByProviderCode("not-exist")).thenReturn(null);

            ProviderApiKeyAddReq req = new ProviderApiKeyAddReq();
            req.setProviderCode("not-exist");
            req.setApiKey("sk-test-key-1234567890");

            assertThrows(BizException.class, () -> service.add(req));
            verify(providerApiKeyMapper, never()).insert(any());
        }

        @Test
        @DisplayName("重复 Key 时抛出 BizException")
        void add_duplicateKey_shouldThrow() {
            when(providerConfigMapper.selectByProviderCode("openai")).thenReturn(new ProviderConfigDO());
            when(apiKeyEncryptor.encrypt(anyString())).thenReturn(new ApiKeyEncryptor.EncryptResult("iv", "ct"));
            when(apiKeyEncryptor.mask(anyString())).thenReturn("sk-test-****5678");
            when(providerApiKeyMapper.countByProviderCodeAndPrefix("openai", "sk-test-****5678")).thenReturn(1);

            ProviderApiKeyAddReq req = new ProviderApiKeyAddReq();
            req.setProviderCode("openai");
            req.setApiKey("sk-test-key-12345678");

            assertThrows(BizException.class, () -> service.add(req));
            verify(providerApiKeyMapper, never()).insert(any());
        }

        @Test
        @DisplayName("新增成功：加密、脱敏、写入数据库、刷新配置")
        void add_success_shouldEncryptAndPersist() {
            when(providerConfigMapper.selectByProviderCode("openai")).thenReturn(new ProviderConfigDO());
            when(apiKeyEncryptor.encrypt("sk-test-key-12345678")).thenReturn(
                    new ApiKeyEncryptor.EncryptResult("encrypted_iv", "encrypted_ct"));
            when(apiKeyEncryptor.mask("sk-test-key-12345678")).thenReturn("sk-test-****5678");
            when(providerApiKeyMapper.countByProviderCodeAndPrefix("openai", "sk-test-****5678")).thenReturn(0);
            when(providerApiKeyMapper.insert(any())).thenReturn(1);

            ProviderApiKeyAddReq req = new ProviderApiKeyAddReq();
            req.setProviderCode("openai");
            req.setApiKey("sk-test-key-12345678");

            // 调用成功不应抛出异常
            service.add(req);

            // 验证写入的记录包含加密后的字段
            ArgumentCaptor<ProviderApiKeyDO> captor = ArgumentCaptor.forClass(ProviderApiKeyDO.class);
            verify(providerApiKeyMapper).insert(captor.capture());
            ProviderApiKeyDO inserted = captor.getValue();
            assertEquals("openai", inserted.getProviderCode());
            assertEquals("encrypted_ct", inserted.getApiKeyCiphertext());
            assertEquals("encrypted_iv", inserted.getApiKeyIv());
            assertEquals("sk-test-****5678", inserted.getApiKeyPrefix());
            assertEquals("system", inserted.getCreator());
            assertEquals("system", inserted.getUpdater());
            assertTrue(inserted.getEnabled());
            assertEquals(100, inserted.getWeight());
            assertEquals(0, inserted.getSortOrder());

            verify(runtimeConfigRefreshService).reloadFromDb("admin-apikey-add");
        }

        @Test
        @DisplayName("新增时自定义权重和排序")
        void add_success_withCustomWeightAndSort() {
            when(providerConfigMapper.selectByProviderCode("openai")).thenReturn(new ProviderConfigDO());
            when(apiKeyEncryptor.encrypt(anyString())).thenReturn(new ApiKeyEncryptor.EncryptResult("iv", "ct"));
            when(apiKeyEncryptor.mask(anyString())).thenReturn("sk-test-****5678");
            when(providerApiKeyMapper.countByProviderCodeAndPrefix(anyString(), anyString())).thenReturn(0);
            when(providerApiKeyMapper.insert(any())).thenReturn(1);

            ProviderApiKeyAddReq req = new ProviderApiKeyAddReq();
            req.setProviderCode("openai");
            req.setApiKey("sk-test-key-12345678");
            req.setWeight(200);
            req.setSortOrder(5);
            req.setEnabled(false);

            service.add(req);

            ArgumentCaptor<ProviderApiKeyDO> captor = ArgumentCaptor.forClass(ProviderApiKeyDO.class);
            verify(providerApiKeyMapper).insert(captor.capture());
            ProviderApiKeyDO inserted = captor.getValue();
            assertEquals(200, inserted.getWeight());
            assertEquals(5, inserted.getSortOrder());
            assertFalse(inserted.getEnabled());
        }
    }

    // ==================== update ====================

    @Nested
    @DisplayName("update - 更新 API Key")
    class UpdateTests {

        @Test
        @DisplayName("Key 不存在时抛出 BizException")
        void update_notFound_shouldThrow() {
            when(providerApiKeyMapper.selectById(999L)).thenReturn(null);

            ProviderApiKeyUpdateReq req = new ProviderApiKeyUpdateReq();
            req.setId(999L);
            req.setVersionNo(0L);

            assertThrows(BizException.class, () -> service.update(req));
        }

        @Test
        @DisplayName("乐观锁冲突时抛出 BizException")
        void update_optimisticLockFail_shouldThrow() {
            when(providerApiKeyMapper.selectById(1L)).thenReturn(buildKeyDO(1L));
            when(providerApiKeyMapper.updateById(any())).thenReturn(0);

            ProviderApiKeyUpdateReq req = new ProviderApiKeyUpdateReq();
            req.setId(1L);
            req.setVersionNo(0L);

            BizException ex = assertThrows(BizException.class, () -> service.update(req));
            assertTrue(ex.getMessage().contains("已被其他操作修改"));
        }

        @Test
        @DisplayName("更新成功：仅修改传入字段，保留原值")
        void update_success_shouldMergeFields() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setRemark("原备注");
            existing.setWeight(100);
            existing.setSortOrder(0);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            when(providerApiKeyMapper.updateById(any())).thenReturn(1);

            ProviderApiKeyUpdateReq req = new ProviderApiKeyUpdateReq();
            req.setId(1L);
            req.setVersionNo(0L);
            req.setWeight(200);

            // 调用成功不应抛出异常
            service.update(req);

            ArgumentCaptor<ProviderApiKeyDO> captor = ArgumentCaptor.forClass(ProviderApiKeyDO.class);
            verify(providerApiKeyMapper).updateById(captor.capture());
            ProviderApiKeyDO updated = captor.getValue();
            assertEquals(200, updated.getWeight());
            assertEquals("原备注", updated.getRemark()); // 未传的字段保留原值
            assertEquals("system", updated.getUpdater());

            verify(runtimeConfigRefreshService).reloadFromDb("admin-apikey-update");
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("delete - 删除 API Key")
    class DeleteTests {

        @Test
        @DisplayName("Key 不存在时抛出 BizException")
        void delete_notFound_shouldThrow() {
            when(providerApiKeyMapper.selectById(999L)).thenReturn(null);

            assertThrows(BizException.class, () -> service.delete(999L));
        }

        @Test
        @DisplayName("删除最后一个启用的 Key 时抛出 BizException")
        void delete_lastEnabledKey_shouldThrow() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setEnabled(true);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            // 事务内检查：加锁后查询到只有 1 个启用的 Key
            when(providerApiKeyMapper.countEnabledByProviderCode("openai")).thenReturn(1);

            BizException ex = assertThrows(BizException.class, () -> service.delete(1L));
            assertTrue(ex.getMessage().contains("最后一个启用的 API Key"));
        }

        @Test
        @DisplayName("删除成功：还有其他启用的 Key")
        void delete_success_whenOtherKeysExist() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setEnabled(true);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            // 事务内检查：加锁后查询到有 2 个启用的 Key
            when(providerApiKeyMapper.countEnabledByProviderCode("openai")).thenReturn(2);

            service.delete(1L);

            verify(providerApiKeyMapper).softDeleteById(1L);
            verify(runtimeConfigRefreshService).reloadFromDb("admin-apikey-delete");
        }
    }

    // ==================== toggle ====================

    @Nested
    @DisplayName("toggle - 切换启用/禁用状态")
    class ToggleTests {

        @Test
        @DisplayName("Key 不存在时抛出 BizException")
        void toggle_notFound_shouldThrow() {
            when(providerApiKeyMapper.selectById(999L)).thenReturn(null);

            assertThrows(BizException.class, () -> service.toggle(999L, 0L, false));
        }

        @Test
        @DisplayName("禁用最后一个启用的 Key 时抛出 BizException")
        void toggle_disableLastKey_shouldThrow() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setEnabled(true);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            // 事务内检查：加锁后查询到只有 1 个启用的 Key
            when(providerApiKeyMapper.countEnabledByProviderCode("openai")).thenReturn(1);

            BizException ex = assertThrows(BizException.class, () -> service.toggle(1L, 0L, false));
            assertTrue(ex.getMessage().contains("最后一个启用的 API Key"));
        }

        @Test
        @DisplayName("启用操作不受最后 Key 保护限制")
        void toggle_enable_success() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setEnabled(false);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            when(providerApiKeyMapper.updateEnabled(any())).thenReturn(1);

            service.toggle(1L, 0L, true);

            verify(providerApiKeyMapper).updateEnabled(any());
            verify(runtimeConfigRefreshService).reloadFromDb("admin-apikey-toggle");
        }

        @Test
        @DisplayName("禁用成功：还有其他启用的 Key")
        void toggle_disable_success_whenOtherKeysExist() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setEnabled(true);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            // 事务内检查：加锁后查询到有 2 个启用的 Key
            when(providerApiKeyMapper.countEnabledByProviderCode("openai")).thenReturn(2);
            when(providerApiKeyMapper.updateEnabled(any())).thenReturn(1);

            service.toggle(1L, 0L, false);

            ArgumentCaptor<ProviderApiKeyDO> captor = ArgumentCaptor.forClass(ProviderApiKeyDO.class);
            verify(providerApiKeyMapper).updateEnabled(captor.capture());
            assertFalse(captor.getValue().getEnabled());
        }

        @Test
        @DisplayName("乐观锁冲突时抛出 BizException")
        void toggle_optimisticLockFail_shouldThrow() {
            ProviderApiKeyDO existing = buildKeyDO(1L);
            existing.setEnabled(false);
            when(providerApiKeyMapper.selectById(1L)).thenReturn(existing);
            when(providerApiKeyMapper.updateEnabled(any())).thenReturn(0);

            BizException ex = assertThrows(BizException.class, () -> service.toggle(1L, 0L, true));
            assertTrue(ex.getMessage().contains("已被其他操作修改"));
        }
    }

    // ==================== 辅助方法 ====================

    private ProviderApiKeyDO buildKeyDO(Long id) {
        ProviderApiKeyDO dbo = new ProviderApiKeyDO();
        dbo.setId(id);
        dbo.setProviderCode("openai");
        dbo.setApiKeyCiphertext("encrypted-ciphertext");
        dbo.setApiKeyIv("encrypted-iv");
        dbo.setApiKeyPrefix("sk-test-****5678");
        dbo.setRemark("测试Key");
        dbo.setEnabled(true);
        dbo.setWeight(100);
        dbo.setSortOrder(0);
        dbo.setVersionNo(0L);
        dbo.setCreator("system");
        dbo.setCreateTime(LocalDateTime.now());
        dbo.setUpdater("system");
        dbo.setUpdateTime(LocalDateTime.now());
        dbo.setDeleted(false);
        return dbo;
    }
}
