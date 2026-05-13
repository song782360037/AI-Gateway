package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.rsp.RequestLogRsp;
import com.code.aigateway.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestLogServiceTest {

    @Mock
    private RequestLogMapper requestLogMapper;

    private RequestLogService service;

    @BeforeEach
    void setUp() {
        service = new RequestLogService(requestLogMapper);
    }

    @Test
    @DisplayName("getDetailById — 查询成功")
    void getDetailById_success() {
        RequestLogDO record = new RequestLogDO();
        record.setId(1L);
        record.setRequestId("req-abc123");
        record.setAliasModel("gpt-4o");
        record.setTargetModel("gpt-4o-2024-05-13");
        record.setProviderCode("openai");
        record.setStatus("SUCCESS");
        record.setCreateTime(LocalDateTime.of(2026, 5, 10, 10, 30));

        when(requestLogMapper.selectById(1L)).thenReturn(record);

        RequestLogRsp rsp = service.getDetailById(1L);

        assertThat(rsp).isNotNull();
        assertThat(rsp.getId()).isEqualTo(1L);
        assertThat(rsp.getRequestId()).isEqualTo("req-abc123");
        assertThat(rsp.getAliasModel()).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("getDetailById — 记录不存在时抛 BizException")
    void getDetailById_notFound_throwsBizException() {
        when(requestLogMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getDetailById(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("999");
    }
}
