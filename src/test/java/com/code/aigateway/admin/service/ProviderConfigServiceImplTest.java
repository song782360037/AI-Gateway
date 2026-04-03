package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * ProviderConfigServiceImpl 单元测试
 *
 * <p>核心场景：新增 Provider 时各字段正确写入数据库。</p>
 */
class ProviderConfigServiceImplTest {

    private ProviderConfigMapper providerConfigMapper;
    private ApiKeyEncryptor apiKeyEncryptor;
    private RuntimeConfigRefreshService runtimeConfigRefreshService;
    private TransactionTemplate transactionTemplate;
    private ProviderConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        providerConfigMapper = Mockito.mock(ProviderConfigMapper.class);
        apiKeyEncryptor = Mockito.mock(ApiKeyEncryptor.class);
        runtimeConfigRefreshService = Mockito.mock(RuntimeConfigRefreshService.class);

        // 构造真实 TransactionTemplate，让 callback 直接执行（不走真实事务）
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
        transactionTemplate = new TransactionTemplate(txManager);

        service = new ProviderConfigServiceImpl(
                providerConfigMapper, apiKeyEncryptor, runtimeConfigRefreshService, transactionTemplate);
    }

    @Test
    void add_success_shouldStoreAllFields() {
        ProviderConfigAddReq req = new ProviderConfigAddReq();
        req.setProviderCode("openai-test1");
        req.setProviderType("OPENAI");
        req.setDisplayName("测试通道");
        req.setEnabled(true);
        req.setBaseUrl("http://localhost:11434");
        req.setApiKey("sk-test-key");
        req.setTimeoutSeconds(30);
        req.setPriority(10);

        Mockito.when(providerConfigMapper.existsByProviderCode("openai-test1")).thenReturn(0);
        Mockito.when(providerConfigMapper.insert(any())).thenReturn(1);
        Mockito.when(apiKeyEncryptor.encrypt("sk-test-key"))
                .thenReturn(new ApiKeyEncryptor.EncryptResult("mock-iv", "mock-cipher"));
        Mockito.when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

        service.add(req);

        // 捕获写入数据库的 DO 对象，验证关键字段正确写入
        ArgumentCaptor<ProviderConfigDO> captor = ArgumentCaptor.forClass(ProviderConfigDO.class);
        verify(providerConfigMapper).insert(captor.capture());
        ProviderConfigDO inserted = captor.getValue();
        assertEquals("openai-test1", inserted.getProviderCode());
        assertEquals("OPENAI", inserted.getProviderType());
        assertEquals("http://localhost:11434", inserted.getBaseUrl());
        assertEquals(10, inserted.getPriority());
        assertEquals("mock-iv", inserted.getApiKeyIv());
        assertEquals("mock-cipher", inserted.getApiKeyCiphertext());
    }
}
