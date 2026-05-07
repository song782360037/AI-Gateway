package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.GlobalConfigMapper;
import com.code.aigateway.admin.model.dataobject.GlobalConfigDO;
import com.code.aigateway.admin.model.req.GlobalCustomHeadersUpdateReq;
import com.code.aigateway.admin.model.rsp.GlobalCustomHeadersRsp;
import com.code.aigateway.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GlobalConfigService 单元测试
 *
 * <p>覆盖：全局自定义请求头校验（受保护头、trim 绕过、空键、非法键）、
 * 缺失行首次保存、乐观锁更新。</p>
 */
class GlobalConfigServiceTest {

    private GlobalConfigMapper globalConfigMapper;
    private ObjectMapper objectMapper;
    private RuntimeConfigRefreshService runtimeConfigRefreshService;
    private GlobalConfigService service;

    @BeforeEach
    void setUp() {
        globalConfigMapper = mock(GlobalConfigMapper.class);
        objectMapper = new ObjectMapper();
        runtimeConfigRefreshService = mock(RuntimeConfigRefreshService.class);
        service = new GlobalConfigService(globalConfigMapper, objectMapper, runtimeConfigRefreshService);
    }

    // ==================== 查询场景 ====================

    @Nested
    @DisplayName("getCustomHeaders - 查询全局请求头")
    class GetCustomHeaders {

        @Test
        @DisplayName("不存在时返回空对象和版本号 0")
        void shouldReturnEmptyWhenNoRecord() {
            when(globalConfigMapper.selectByConfigKey("custom_headers")).thenReturn(null);

            GlobalCustomHeadersRsp rsp = service.getCustomHeaders();

            assertNotNull(rsp);
            assertEquals(0, rsp.getCustomHeaders().size());
            assertEquals(0L, rsp.getVersionNo());
        }

        @Test
        @DisplayName("存在时正确解析请求头和版本号")
        void shouldParseExistingRecord() {
            GlobalConfigDO record = new GlobalConfigDO();
            record.setConfigKey("custom_headers");
            record.setConfigValue("{\"X-Custom\":\"value1\"}");
            record.setVersionNo(5L);
            when(globalConfigMapper.selectByConfigKey("custom_headers")).thenReturn(record);

            GlobalCustomHeadersRsp rsp = service.getCustomHeaders();

            assertNotNull(rsp);
            assertEquals(1, rsp.getCustomHeaders().size());
            assertEquals("value1", rsp.getCustomHeaders().get("X-Custom"));
            assertEquals(5L, rsp.getVersionNo());
        }
    }

    // ==================== 受保护头校验 ====================

    @Nested
    @DisplayName("updateCustomHeaders - 受保护头拒绝")
    class ProtectedHeaderValidation {

        @Test
        @DisplayName("拒绝 Authorization 头")
        void shouldRejectAuthorization() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            req.setCustomHeaders(Map.of("Authorization", "Bearer xxx"));

            assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
        }

        @Test
        @DisplayName("trim 绕过：\" Authorization \" 仍被拒绝")
        void shouldRejectTrimmedAuthorization() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            req.setCustomHeaders(Map.of(" Authorization ", "Bearer xxx"));

            assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
        }

        @Test
        @DisplayName("大小写混合 + 空格绕过仍被拒绝")
        void shouldRejectMixedCaseWithSpaces() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            req.setCustomHeaders(Map.of(" X-Api-Key ", "secret"));

            assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
        }
    }

    // ==================== 空键与非法键 ====================

    @Nested
    @DisplayName("updateCustomHeaders - 空键与非法键")
    class HeaderKeyValidation {

        @Test
        @DisplayName("空白键被拒绝")
        void shouldRejectBlankKey() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            req.setCustomHeaders(Map.of("  ", "value"));

            assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
        }

        @Test
        @DisplayName("包含换行符的键被拒绝（HTTP header injection）")
        void shouldRejectHeaderWithNewline() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Evil\nX-Inject", "value");
            req.setCustomHeaders(headers);

            assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
        }

        @Test
        @DisplayName("包含回车符的键被拒绝")
        void shouldRejectHeaderWithCr() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Evil\rX-Inject", "value");
            req.setCustomHeaders(headers);

            assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
        }
    }

    // ==================== 更新成功 ====================

    @Nested
    @DisplayName("updateCustomHeaders - 更新成功")
    class UpdateSuccess {

        @Test
        @DisplayName("合法请求头更新成功并刷新运行时配置")
        void shouldUpdateSuccessfully() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(1L);
            req.setCustomHeaders(Map.of("X-Custom", "value1"));

            when(globalConfigMapper.updateByConfigKey(any())).thenReturn(1);
            when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

            service.updateCustomHeaders(req);

            verify(globalConfigMapper).updateByConfigKey(any());
            verify(runtimeConfigRefreshService).reloadFromDb("admin-update-global-headers");
        }
    }

    // ==================== 缺失行首次保存 ====================

    @Nested
    @DisplayName("updateCustomHeaders - 缺失行首次保存")
    class MissingRowInsert {

        @Test
        @DisplayName("版本号为 0 且更新返回 0 行时，应尝试插入新行")
        void shouldInsertWhenRowMissing() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(0L);
            req.setCustomHeaders(Map.of("X-Custom", "value1"));

            // 更新返回 0 行（行不存在）
            when(globalConfigMapper.updateByConfigKey(any())).thenReturn(0);
            // 插入成功
            when(globalConfigMapper.insertByConfigKey(any())).thenReturn(1);
            when(runtimeConfigRefreshService.reloadFromDb(any())).thenReturn(true);

            service.updateCustomHeaders(req);

            verify(globalConfigMapper).insertByConfigKey(any());
            verify(runtimeConfigRefreshService).reloadFromDb("admin-update-global-headers");
        }

        @Test
        @DisplayName("版本号非 0 且更新返回 0 行时，抛并发修改异常")
        void shouldThrowConcurrentModifiedWhenVersionNotZero() {
            GlobalCustomHeadersUpdateReq req = new GlobalCustomHeadersUpdateReq();
            req.setVersionNo(5L);
            req.setCustomHeaders(Map.of("X-Custom", "value1"));

            when(globalConfigMapper.updateByConfigKey(any())).thenReturn(0);

            BizException ex = assertThrows(BizException.class, () -> service.updateCustomHeaders(req));
            assertTrue(ex.getMessage().contains("已被其他请求修改") || ex.getCode().contains("CONCURRENT"),
                    "应抛出并发修改异常");
        }
    }
}
