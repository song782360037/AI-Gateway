package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.req.ApiKeyConfigAddReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigQueryReq;
import com.code.aigateway.admin.model.req.ApiKeyConfigUpdateReq;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigCreateRsp;
import com.code.aigateway.admin.model.rsp.ApiKeyConfigRsp;
import com.code.aigateway.admin.service.IApiKeyConfigService;
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
 * API Key 配置管理接口 WebFlux 切片测试
 *
 * <p>重点验证：创建时返回完整明文 key、参数校验、CRUD 正常流程。</p>
 */
class ApiKeyConfigControllerTest {

    private IApiKeyConfigService apiKeyConfigService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        apiKeyConfigService = Mockito.mock(IApiKeyConfigService.class);
        ApiKeyConfigController controller = new ApiKeyConfigController(apiKeyConfigService);

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
    void add_success_returnsFullKey() {
        ApiKeyConfigAddReq req = new ApiKeyConfigAddReq();
        req.setName("测试 Key");
        req.setStatus("ACTIVE");
        req.setDailyLimit(1000);

        // 服务层返回 ApiKeyConfigCreateRsp，包含完整明文 key
        ApiKeyConfigCreateRsp createRsp = new ApiKeyConfigCreateRsp();
        createRsp.setId(1L);
        createRsp.setKeyPrefix("ak-a1b2c");
        createRsp.setName("测试 Key");
        createRsp.setStatus("ACTIVE");
        createRsp.setDailyLimit(1000);
        createRsp.setUsedCount(0L);
        createRsp.setVersionNo(1L);
        createRsp.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        createRsp.setUpdateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        // 完整明文 key 仅在创建响应中返回
        createRsp.setApiKey("ak-a1b2c3d4e5f6g7h8i9j0");

        Mockito.when(apiKeyConfigService.add(Mockito.any(ApiKeyConfigAddReq.class)))
                .thenReturn(createRsp);

        webTestClient.post()
                .uri("/admin/api-key-config/add")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                // 验证返回了完整 API Key
                .jsonPath("$.data.apiKey").isEqualTo("ak-a1b2c3d4e5f6g7h8i9j0")
                .jsonPath("$.data.keyPrefix").isEqualTo("ak-a1b2c")
                .jsonPath("$.data.name").isEqualTo("测试 Key")
                .jsonPath("$.data.status").isEqualTo("ACTIVE");
    }

    @Test
    void add_missingName_returns400() {
        // name 是必填字段，为空时应触发校验
        ApiKeyConfigAddReq req = new ApiKeyConfigAddReq();
        req.setName("");

        webTestClient.post()
                .uri("/admin/api-key-config/add")
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
        ApiKeyConfigUpdateReq req = new ApiKeyConfigUpdateReq();
        req.setId(1L);
        req.setVersionNo(1L);
        req.setDailyLimit(2000);

        Mockito.doNothing().when(apiKeyConfigService).update(Mockito.any());

        webTestClient.post()
                .uri("/admin/api-key-config/update")
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
        Mockito.doNothing().when(apiKeyConfigService).delete(1L);

        webTestClient.post()
                .uri("/admin/api-key-config/delete/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    // ==================== list ====================

    @Test
    void list_success() {
        // 列表查询返回的是 ApiKeyConfigRsp（不含完整 key）
        ApiKeyConfigRsp item = new ApiKeyConfigRsp();
        item.setId(1L);
        item.setKeyPrefix("ak-a1b2c");
        item.setName("测试 Key");
        item.setStatus("ACTIVE");
        item.setDailyLimit(1000);
        item.setUsedCount(50L);

        PageResult<ApiKeyConfigRsp> pageResult =
                PageResult.of(List.of(item), 1, 1, 20);

        Mockito.when(apiKeyConfigService.list(Mockito.any(ApiKeyConfigQueryReq.class)))
                .thenReturn(pageResult);

        ApiKeyConfigQueryReq queryReq = new ApiKeyConfigQueryReq();

        webTestClient.post()
                .uri("/admin/api-key-config/list")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.list.length()").isEqualTo(1)
                .jsonPath("$.data.total").isEqualTo(1)
                // 列表只返回 key 前缀，不含完整 key
                .jsonPath("$.data.list[0].keyPrefix").isEqualTo("ak-a1b2c")
                .jsonPath("$.data.list[0].name").isEqualTo("测试 Key");
    }
}
