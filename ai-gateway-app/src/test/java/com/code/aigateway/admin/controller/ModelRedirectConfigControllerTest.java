package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.req.ModelRedirectConfigAddReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigQueryReq;
import com.code.aigateway.admin.model.req.ModelRedirectConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ModelRedirectConfigRsp;
import com.code.aigateway.admin.service.IModelRedirectConfigService;
import com.code.aigateway.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型重定向配置管理接口 WebFlux 切片测试
 *
 * <p>验证 CRUD 端点的 HTTP 状态码、R 统一响应格式和分页结构。</p>
 */
class ModelRedirectConfigControllerTest {

    private IModelRedirectConfigService modelRedirectConfigService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        modelRedirectConfigService = Mockito.mock(IModelRedirectConfigService.class);
        ModelRedirectConfigController controller =
                new ModelRedirectConfigController(modelRedirectConfigService);

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
        ModelRedirectConfigAddReq req = new ModelRedirectConfigAddReq();
        req.setAliasName("gpt-4o");
        req.setProviderCode("openai-main");
        req.setTargetModel("gpt-4o-2024-08-06");
        req.setEnabled(true);

        Mockito.when(modelRedirectConfigService.add(Mockito.any(ModelRedirectConfigAddReq.class)))
                .thenReturn(1L);

        webTestClient.post()
                .uri("/admin/model-redirect-config/add")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEqualTo(1);
    }

    @Test
    void add_missingAliasName_returns400() {
        // aliasName 是必填字段，传空字符串应触发校验
        ModelRedirectConfigAddReq req = new ModelRedirectConfigAddReq();
        req.setAliasName("");
        req.setProviderCode("openai-main");
        req.setTargetModel("gpt-4o");

        webTestClient.post()
                .uri("/admin/model-redirect-config/add")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("INVALID_PARAM");
    }

    // ==================== update ====================

    @Test
    void update_success() {
        ModelRedirectConfigUpdateReq req = new ModelRedirectConfigUpdateReq();
        req.setId(1L);
        req.setVersionNo(1L);
        req.setAliasName("gpt-4o");
        req.setProviderCode("openai-main");
        req.setTargetModel("gpt-4o-2024-11-20");

        Mockito.doNothing().when(modelRedirectConfigService).update(Mockito.any());

        webTestClient.post()
                .uri("/admin/model-redirect-config/update")
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
        Mockito.doNothing().when(modelRedirectConfigService).delete(1L);

        webTestClient.post()
                .uri("/admin/model-redirect-config/delete/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    // ==================== getById ====================

    @Test
    void getById_success() {
        ModelRedirectConfigRsp rsp = new ModelRedirectConfigRsp();
        rsp.setId(1L);
        rsp.setAliasName("gpt-4o");
        rsp.setProviderCode("openai-main");
        rsp.setTargetModel("gpt-4o-2024-08-06");
        rsp.setEnabled(true);
        rsp.setVersionNo(1L);
        rsp.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        Mockito.when(modelRedirectConfigService.getById(1L)).thenReturn(rsp);

        webTestClient.get()
                .uri("/admin/model-redirect-config/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.aliasName").isEqualTo("gpt-4o")
                .jsonPath("$.data.targetModel").isEqualTo("gpt-4o-2024-08-06");
    }

    // ==================== list ====================

    @Test
    void list_success() {
        ModelRedirectConfigRsp item1 = new ModelRedirectConfigRsp();
        item1.setId(1L);
        item1.setAliasName("gpt-4o");
        item1.setProviderCode("openai-main");

        ModelRedirectConfigRsp item2 = new ModelRedirectConfigRsp();
        item2.setId(2L);
        item2.setAliasName("claude-3.5-sonnet");
        item2.setProviderCode("anthropic-main");

        PageResult<ModelRedirectConfigRsp> pageResult =
                PageResult.of(List.of(item1, item2), 2, 1, 20);

        Mockito.when(modelRedirectConfigService.list(Mockito.any(ModelRedirectConfigQueryReq.class)))
                .thenReturn(pageResult);

        ModelRedirectConfigQueryReq queryReq = new ModelRedirectConfigQueryReq();

        queryReq.setPage(1);
        queryReq.setPageSize(20);

        webTestClient.post()
                .uri("/admin/model-redirect-config/list")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.list.length()").isEqualTo(2)
                .jsonPath("$.data.total").isEqualTo(2)
                .jsonPath("$.data.list[0].aliasName").isEqualTo("gpt-4o")
                .jsonPath("$.data.list[1].aliasName").isEqualTo("claude-3.5-sonnet");
    }
}
