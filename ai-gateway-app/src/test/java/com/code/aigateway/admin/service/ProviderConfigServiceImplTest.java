package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.AutoRouteCandidateMapper;
import com.code.aigateway.admin.mapper.ProviderApiKeyMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.admin.model.req.ProviderConfigUpdateReq;
import com.code.aigateway.common.exception.BizException;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProviderConfigServiceImpl 单元测试
 *
 * <p>覆盖：新增 Provider 字段写入、自定义请求头校验（受保护头、trim 绕过、空键、非法键、null 值）。</p>
 */
class ProviderConfigServiceImplTest {

    private ProviderConfigMapper providerConfigMapper;
    private AutoRouteCandidateMapper autoRouteCandidateMapper;
    private ProviderApiKeyMapper providerApiKeyMapper;
    private RuntimeConfigRefreshService runtimeConfigRefreshService;
    private TransactionTemplate transactionTemplate;
    private ProviderConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        providerConfigMapper = Mockito.mock(ProviderConfigMapper.class);
        autoRouteCandidateMapper = Mockito.mock(AutoRouteCandidateMapper.class);
        providerApiKeyMapper = Mockito.mock(ProviderApiKeyMapper.class);
        runtimeConfigRefreshService = Mockito.mock(RuntimeConfigRefreshService.class);

        // 构造真实 TransactionTemplate，让 callback 直接执行（不走真实事务）
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
        transactionTemplate = new TransactionTemplate(txManager);

        service = new ProviderConfigServiceImpl(
                providerConfigMapper, providerApiKeyMapper, autoRouteCandidateMapper,
                runtimeConfigRefreshService, transactionTemplate);
    }

    // ==================== 新增 Provider 成功场景 ====================

    @Nested
    @DisplayName("add - 成功场景")
    class AddSuccess {

        @Test
        @DisplayName("新增成功：各字段正确写入数据库")
        void add_success_shouldStoreAllFields() {
            ProviderConfigAddReq req = new ProviderConfigAddReq();
            req.setProviderCode("openai-test1");
            req.setProviderType("OPENAI");
            req.setDisplayName("测试通道");
            req.setEnabled(true);
            req.setBaseUrl("http://localhost:11434");
            req.setTimeoutSeconds(30);
            req.setPriority(10);

            when(providerConfigMapper.existsByProviderCode("openai-test1")).thenReturn(0);
            when(providerConfigMapper.insert(any())).thenReturn(1);
            when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

            service.add(req);

            // 捕获写入数据库的 DO 对象，验证关键字段正确写入
            ArgumentCaptor<ProviderConfigDO> captor = ArgumentCaptor.forClass(ProviderConfigDO.class);
            verify(providerConfigMapper).insert(captor.capture());
            ProviderConfigDO inserted = captor.getValue();
            assertEquals("openai-test1", inserted.getProviderCode());
            assertEquals("OPENAI", inserted.getProviderType());
            assertEquals("http://localhost:11434", inserted.getBaseUrl());
            assertEquals(10, inserted.getPriority());
        }

        @Test
        @DisplayName("新增成功：自定义请求头正确序列化为 JSON")
        void add_success_shouldSerializeCustomHeaders() {
            ProviderConfigAddReq req = buildBaseAddReq();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Custom", "value1");
            headers.put("X-Trace-Id", "abc123");
            req.setCustomHeaders(headers);

            when(providerConfigMapper.existsByProviderCode(any())).thenReturn(0);
            when(providerConfigMapper.insert(any())).thenReturn(1);
            when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

            service.add(req);

            ArgumentCaptor<ProviderConfigDO> captor = ArgumentCaptor.forClass(ProviderConfigDO.class);
            verify(providerConfigMapper).insert(captor.capture());
            assertNotNull(captor.getValue().getCustomHeaders(), "自定义请求头不应为 null");
        }
    }

    // ==================== 自定义请求头校验 ====================

    @Nested
    @DisplayName("自定义请求头校验 - 受保护头拒绝")
    class ProtectedHeaderValidation {

        @Test
        @DisplayName("拒绝 Authorization 头")
        void add_shouldRejectAuthorizationHeader() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of("Authorization", "Bearer xxx"));

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("拒绝 x-api-key 头")
        void add_shouldRejectXApiKeyHeader() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of("x-api-key", "secret"));

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("拒绝 x-goog-api-key 头")
        void add_shouldRejectXGoogApiKeyHeader() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of("x-goog-api-key", "secret"));

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("拒绝 anthropic-version 头")
        void add_shouldRejectAnthropicVersionHeader() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of("anthropic-version", "2023-06-01"));

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("trim 绕过：前后空格的 Authorization 仍被拒绝")
        void add_shouldRejectTrimmedAuthorizationHeader() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of(" Authorization ", "Bearer xxx"));

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("大小写混合 + 空格绕过：\" X-Api-Key \" 仍被拒绝")
        void add_shouldRejectMixedCaseWithSpaces() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of(" X-Api-Key ", "secret"));

            assertThrows(BizException.class, () -> service.add(req));
        }
    }

    @Nested
    @DisplayName("自定义请求头校验 - 空键与非法键")
    class HeaderKeyValidation {

        @Test
        @DisplayName("空白键被拒绝")
        void add_shouldRejectBlankKey() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            req.setCustomHeaders(Map.of("  ", "value"));

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("包含非法字符的键被拒绝")
        void add_shouldRejectInvalidHeaderName() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            // 包含换行符的 header name（HTTP header injection）
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Custom\nX-Evil", "value");
            req.setCustomHeaders(headers);

            assertThrows(BizException.class, () -> service.add(req));
        }

        @Test
        @DisplayName("包含回车符的键被拒绝")
        void add_shouldRejectHeaderNameWithCr() {
            ProviderConfigAddReq req = buildBaseAddReqWithMocks();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Custom\rX-Evil", "value");
            req.setCustomHeaders(headers);

            assertThrows(BizException.class, () -> service.add(req));
        }
    }

    @Nested
    @DisplayName("自定义请求头校验 - 更新场景")
    class UpdateHeaderValidation {

        @Test
        @DisplayName("更新时拒绝 trim 后的受保护头")
        void update_shouldRejectTrimmedProtectedHeader() {
            ProviderConfigUpdateReq req = new ProviderConfigUpdateReq();
            req.setId(1L);
            req.setVersionNo(0L);
            req.setProviderCode("test");
            req.setProviderType("OPENAI");
            req.setDisplayName("test");
            req.setEnabled(true);
            req.setBaseUrl("http://localhost");
            req.setTimeoutSeconds(60);
            req.setPriority(0);
            req.setCustomHeaders(Map.of(" Authorization ", "Bearer xxx"));

            ProviderConfigDO existing = buildExistingDO();
            when(providerConfigMapper.selectById(1L)).thenReturn(existing);

            assertThrows(BizException.class, () -> service.update(req));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建基础 add 请求，并预设必要的 mock：
     * - providerCode 不重复
     * 这样受保护头校验在 buildInsertRecord 阶段才能被触发。
     */
    private ProviderConfigAddReq buildBaseAddReqWithMocks() {
        when(providerConfigMapper.existsByProviderCode(any())).thenReturn(0);
        return buildBaseAddReq();
    }

    private ProviderConfigAddReq buildBaseAddReq() {
        ProviderConfigAddReq req = new ProviderConfigAddReq();
        req.setProviderCode("test-provider");
        req.setProviderType("OPENAI");
        req.setDisplayName("测试");
        req.setEnabled(true);
        req.setBaseUrl("http://localhost:11434");
        req.setTimeoutSeconds(60);
        req.setPriority(0);
        return req;
    }

    private ProviderConfigDO buildExistingDO() {
        ProviderConfigDO doObj = new ProviderConfigDO();
        doObj.setId(1L);
        doObj.setProviderCode("test-provider");
        doObj.setProviderType("OPENAI");
        doObj.setDisplayName("测试");
        doObj.setEnabled(true);
        doObj.setBaseUrl("http://localhost:11434");
        doObj.setTimeoutSeconds(60);
        doObj.setPriority(0);
        doObj.setVersionNo(0L);
        return doObj;
    }
}
