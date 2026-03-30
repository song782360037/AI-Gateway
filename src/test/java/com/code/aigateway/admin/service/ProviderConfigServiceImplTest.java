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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * ProviderConfigServiceImpl 单元测试
 *
 * <p>核心场景：extConfigJson 空白字符串不应写入 MySQL JSON 列。</p>
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
    void add_extConfigJsonBlank_shouldStoreNull() {
        ProviderConfigAddReq req = new ProviderConfigAddReq();
        req.setProviderCode("openai-test1");
        req.setProviderType("OPENAI");
        req.setDisplayName("测试通道");
        req.setEnabled(true);
        req.setBaseUrl("http://localhost:11434");
        req.setApiKey("sk-test-key");
        req.setTimeoutSeconds(30);
        req.setPriority(0);
        req.setExtConfigJson("");

        Mockito.when(providerConfigMapper.existsByProviderCode("openai-test1")).thenReturn(0);
        Mockito.when(providerConfigMapper.insert(any())).thenReturn(1);
        Mockito.when(apiKeyEncryptor.encrypt("sk-test-key"))
                .thenReturn(new ApiKeyEncryptor.EncryptResult("cipher", "iv"));
        Mockito.when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

        service.add(req);

        // 捕获写入数据库的 DO 对象，验证 extConfigJson 被归一化为 null
        ArgumentCaptor<ProviderConfigDO> captor = ArgumentCaptor.forClass(ProviderConfigDO.class);
        verify(providerConfigMapper).insert(captor.capture());
        assertNull(captor.getValue().getExtConfigJson());
    }

    @Test
    void add_extConfigJsonValid_shouldStoreAsIs() {
        ProviderConfigAddReq req = new ProviderConfigAddReq();
        req.setProviderCode("openai-test2");
        req.setProviderType("OPENAI");
        req.setDisplayName("测试通道");
        req.setEnabled(true);
        req.setBaseUrl("http://localhost:11434");
        req.setApiKey("sk-test-key");
        req.setTimeoutSeconds(30);
        req.setPriority(0);
        req.setExtConfigJson("{\"region\":\"us-east-1\"}");

        Mockito.when(providerConfigMapper.existsByProviderCode("openai-test2")).thenReturn(0);
        Mockito.when(providerConfigMapper.insert(any())).thenReturn(1);
        Mockito.when(apiKeyEncryptor.encrypt("sk-test-key"))
                .thenReturn(new ApiKeyEncryptor.EncryptResult("cipher", "iv"));
        Mockito.when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

        service.add(req);

        ArgumentCaptor<ProviderConfigDO> captor = ArgumentCaptor.forClass(ProviderConfigDO.class);
        verify(providerConfigMapper).insert(captor.capture());
        assertEquals("{\"region\":\"us-east-1\"}", captor.getValue().getExtConfigJson());
    }
}
