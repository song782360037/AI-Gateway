package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.req.ProviderConfigAddReq;
import com.code.aigateway.admin.model.req.ProviderConfigQueryReq;
import com.code.aigateway.admin.model.req.ProviderConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ProviderConfigRsp;
import com.code.aigateway.admin.service.IProviderConfigService;
import com.code.aigateway.admin.service.ProviderConnectionTestService;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 提供商配置管理接口 WebFlux 切片测试
 *
 * <p>使用 WebTestClient.bindToController 手动装配，避免启动完整 Spring 上下文。
 * 通过 ControllerSpec.validator() 注册 LocalValidatorFactoryBean 使 @Valid 注解校验生效。</p>
 */
class ProviderConfigControllerTest {

    private IProviderConfigService providerConfigService;
    private ProviderConnectionTestService connectionTestService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        providerConfigService = Mockito.mock(IProviderConfigService.class);
        connectionTestService = Mockito.mock(ProviderConnectionTestService.class);
        ProviderConfigController controller = new ProviderConfigController(providerConfigService, connectionTestService);

        // 创建 JSR-303 校验器，使 @Valid / @NotBlank 等注解生效
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new AdminExceptionHandler())
                .validator(validator)
                .build();
    }

    // ==================== add ====================

    @Test
    void add_success() {
        // 准备合法的新增请求
        ProviderConfigAddReq req = new ProviderConfigAddReq();
        req.setProviderCode("openai-main");
        req.setProviderType("OPENAI");
        req.setDisplayName("主 OpenAI 通道");
        req.setBaseUrl("https://api.openai.com");
        req.setApiKey("sk-test-key-123");

        Mockito.when(providerConfigService.add(Mockito.any(ProviderConfigAddReq.class)))
                .thenReturn(1L);

        webTestClient.post()
                .uri("/admin/provider-config/add")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                // 成功响应：success=true, data=新增记录主键
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data").isEqualTo(1);
    }

    @Test
    void add_missingProviderCode_returns400() {
        // 缺少必填字段 providerCode（null），校验应返回 400
        ProviderConfigAddReq req = new ProviderConfigAddReq();
        req.setProviderType("OPENAI");
        req.setBaseUrl("https://api.openai.com");
        req.setApiKey("sk-test");

        webTestClient.post()
                .uri("/admin/provider-config/add")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest()
                // 参数校验失败：success=false, code=INVALID_PARAM
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("INVALID_PARAM");

        verify(providerConfigService, never()).add(any());
    }

    // ==================== update ====================

    @Test
    void update_success() {
        ProviderConfigUpdateReq req = new ProviderConfigUpdateReq();
        req.setId(1L);
        req.setVersionNo(1L);
        req.setProviderCode("openai-main");
        req.setProviderType("OPENAI");
        req.setBaseUrl("https://api.openai.com");
        req.setDisplayName("更新后的名称");

        Mockito.doNothing().when(providerConfigService).update(Mockito.any());

        webTestClient.post()
                .uri("/admin/provider-config/update")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    // ==================== delete ====================

    @Test
    void delete_success() {
        Mockito.doNothing().when(providerConfigService).delete(1L);

        webTestClient.post()
                .uri("/admin/provider-config/delete/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    // ==================== getById ====================

    @Test
    void getById_success() {
        ProviderConfigRsp rsp = new ProviderConfigRsp();
        rsp.setId(1L);
        rsp.setProviderCode("openai-main");
        rsp.setProviderType("OPENAI");
        rsp.setDisplayName("主 OpenAI 通道");
        rsp.setEnabled(true);
        rsp.setBaseUrl("https://api.openai.com");
        rsp.setApiKeyMasked("sk-*** masked");
        rsp.setTimeoutSeconds(60);
        rsp.setPriority(0);
        rsp.setVersionNo(1L);
        rsp.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        rsp.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        Mockito.when(providerConfigService.getById(1L)).thenReturn(rsp);

        webTestClient.get()
                .uri("/admin/provider-config/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.providerCode").isEqualTo("openai-main")
                .jsonPath("$.data.apiKeyMasked").isEqualTo("sk-*** masked");
    }

    @Test
    void getById_notFound_returns400() {
        // 模拟服务层抛出业务异常（记录不存在）
        Mockito.when(providerConfigService.getById(999L))
                .thenThrow(new BizException("NOT_FOUND", "提供商配置不存在"));

        webTestClient.get()
                .uri("/admin/provider-config/999")
                .exchange()
                // BizException 被 AdminExceptionHandler 捕获，返回 400
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("提供商配置不存在");
    }

    // ==================== list ====================

    @Test
    void list_success() {
        ProviderConfigRsp item = new ProviderConfigRsp();
        item.setId(1L);
        item.setProviderCode("openai-main");
        item.setProviderType("OPENAI");

        PageResult<ProviderConfigRsp> pageResult =
                PageResult.of(List.of(item), 1, 1, 20);

        Mockito.when(providerConfigService.list(Mockito.any(ProviderConfigQueryReq.class)))
                .thenReturn(pageResult);

        ProviderConfigQueryReq queryReq = new ProviderConfigQueryReq();

        webTestClient.post()
                .uri("/admin/provider-config/list")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                // 验证分页结构：list、total、page、pageSize
                .jsonPath("$.data.list.length()").isEqualTo(1)
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.page").isEqualTo(1)
                .jsonPath("$.data.pageSize").isEqualTo(20)
                .jsonPath("$.data.list[0].providerCode").isEqualTo("openai-main");
    }
}
