package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.AutoRouteCandidateMapper;
import com.code.aigateway.admin.mapper.AutoRouteConfigMapper;
import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.mapper.SupportedModelMapper;
import com.code.aigateway.admin.model.dataobject.AutoRouteCandidateDO;
import com.code.aigateway.admin.model.dataobject.AutoRouteConfigDO;
import com.code.aigateway.admin.model.dataobject.ModelRedirectConfigDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.dataobject.SupportedModelDO;
import com.code.aigateway.core.router.MatchType;
import com.code.aigateway.core.router.RouteCandidate;
import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.runtime.RedisRoutingCacheService;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运行时配置刷新服务单元测试
 *
 * <p>覆盖场景：正常刷新、无效规则过滤、EXACT/GLOB/REGEX 分组、
 * Auto 路由构建、支持模型构建、刷新失败处理、协议解析、版本号生成等。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuntimeConfigRefreshServiceTest {

    @Mock
    private ProviderConfigMapper providerConfigMapper;

    @Mock
    private ModelRedirectConfigMapper modelRedirectConfigMapper;

    @Mock
    private AutoRouteConfigMapper autoRouteConfigMapper;

    @Mock
    private AutoRouteCandidateMapper autoRouteCandidateMapper;

    @Mock
    private SupportedModelMapper supportedModelMapper;

    @Mock
    private ApiKeyEncryptor apiKeyEncryptor;

    @Mock
    private RoutingSnapshotHolder routingSnapshotHolder;

    @Mock
    private RedisRoutingCacheService redisRoutingCacheService;

    @Mock
    private ObjectMapper objectMapper;

    private RuntimeConfigRefreshService service;

    @BeforeEach
    void setUp() {
        service = new RuntimeConfigRefreshService(
                providerConfigMapper,
                modelRedirectConfigMapper,
                autoRouteConfigMapper,
                autoRouteCandidateMapper,
                supportedModelMapper,
                apiKeyEncryptor,
                routingSnapshotHolder,
                redisRoutingCacheService,
                objectMapper
        );
    }

    // ==================== 辅助方法 ====================

    /** 构建启用的 ProviderConfigDO */
    private ProviderConfigDO buildProviderDO(String providerCode, String providerType,
                                              String baseUrl, int priority, String protocols) {
        ProviderConfigDO p = new ProviderConfigDO();
        p.setId(1L);
        p.setProviderCode(providerCode);
        p.setProviderType(providerType);
        p.setEnabled(true);
        p.setBaseUrl(baseUrl);
        p.setApiKeyCiphertext("encrypted-key");
        p.setApiKeyIv("iv-value");
        p.setTimeoutSeconds(60);
        p.setPriority(priority);
        p.setSupportedProtocols(protocols);
        return p;
    }

    /** 构建启用的 ModelRedirectConfigDO */
    private ModelRedirectConfigDO buildRedirectDO(String aliasName, String providerCode,
                                                    String targetModel, String matchType) {
        ModelRedirectConfigDO r = new ModelRedirectConfigDO();
        r.setId(1L);
        r.setAliasName(aliasName);
        r.setProviderCode(providerCode);
        r.setTargetModel(targetModel);
        r.setMatchType(matchType);
        r.setEnabled(true);
        return r;
    }

    /** 构建启用的 AutoRouteConfigDO */
    private AutoRouteConfigDO buildAutoRouteConfigDO(Long id, String routeKey, String strategy) {
        AutoRouteConfigDO c = new AutoRouteConfigDO();
        c.setId(id);
        c.setRouteKey(routeKey);
        c.setSelectionStrategy(strategy);
        c.setEnabled(true);
        return c;
    }

    /** 构建启用的 AutoRouteCandidateDO */
    private AutoRouteCandidateDO buildAutoRouteCandidateDO(Long id, Long configId,
                                                             String providerCode, String targetModel,
                                                             Integer priority) {
        AutoRouteCandidateDO c = new AutoRouteCandidateDO();
        c.setId(id);
        c.setConfigId(configId);
        c.setProviderCode(providerCode);
        c.setTargetModel(targetModel);
        c.setPriority(priority);
        c.setEnabled(true);
        return c;
    }

    /** 构建启用的 SupportedModelDO */
    private SupportedModelDO buildSupportedModelDO(String modelId, String displayName,
                                                     String ownedBy) {
        SupportedModelDO m = new SupportedModelDO();
        m.setModelId(modelId);
        m.setDisplayName(displayName);
        m.setOwnedBy(ownedBy);
        m.setEnabled(true);
        m.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        return m;
    }

    // ==================== 正常刷新流程 ====================

    @Nested
    @DisplayName("reloadFromDb - 正常刷新流程")
    class NormalRefresh {

        @Test
        @DisplayName("正常刷新：构建快照并原子替换到 holder，预热 Redis")
        void shouldRefreshSuccessfully() throws Exception {
            // 准备 Provider 数据
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, "OPENAI_CHAT");
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt("iv-value", "encrypted-key")).thenReturn("sk-plain-key");

            // 准备模型路由数据（EXACT）
            ModelRedirectConfigDO redirect = buildRedirectDO("gpt-4o", "openai-main", "gpt-4o", "EXACT");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(redirect));

            // 无 Auto 路由
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());

            // 无支持模型
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());

            // ObjectMapper 序列化
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"version\":1}");

            // 执行
            boolean result = service.reloadFromDb("startup");

            // 验证结果
            assertTrue(result, "刷新应成功");

            // 验证快照原子替换
            ArgumentCaptor<RoutingConfigSnapshot> snapshotCaptor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(snapshotCaptor.capture());

            RoutingConfigSnapshot snapshot = snapshotCaptor.getValue();
            assertNotNull(snapshot);
            assertEquals(1, snapshot.getCandidatesMapSize(), "应有1个精确别名路由");
            assertNotNull(snapshot.getSource(), "来源不应为 null");

            // 验证 Redis 预热
            verify(redisRoutingCacheService).warmupSnapshot(eq("{\"version\":1}"), anyLong());
            verify(redisRoutingCacheService).clearDirty();
        }

        @Test
        @DisplayName("刷新后快照包含 Provider 配置信息")
        void shouldIncludeProviderInfoInSnapshot() throws Exception {
            ProviderConfigDO provider = buildProviderDO("claude-main", "anthropic",
                    "https://api.anthropic.com", 5, "ANTHROPIC,OPENAI_CHAT");
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-claude-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("admin-manual");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            RoutingConfigSnapshot.ProviderEntry entry = snapshot.getProviderMap().get("claude-main");
            assertNotNull(entry, "Provider 应存在");
            assertEquals("anthropic", entry.providerType());
            assertEquals("https://api.anthropic.com", entry.baseUrl());
            assertEquals("sk-claude-key", entry.apiKey());
            assertEquals(60, entry.timeoutSeconds());
            assertEquals(5, entry.priority());
            assertEquals(2, entry.supportedProtocols().size(), "应解析2个协议");
        }

        @Test
        @DisplayName("刷新后快照包含支持模型列表")
        void shouldIncludeSupportedModelsInSnapshot() throws Exception {
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());

            SupportedModelDO model1 = buildSupportedModelDO("gpt-4o", "GPT-4o", "openai");
            SupportedModelDO model2 = buildSupportedModelDO("claude-sonnet", "Claude Sonnet", "anthropic");
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of(model1, model2));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("scheduled");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            assertEquals(2, snapshot.getSupportedModels().size(), "应有2个支持模型");
            assertEquals("gpt-4o", snapshot.getSupportedModels().get(0).modelId());
            assertEquals("claude-sonnet", snapshot.getSupportedModels().get(1).modelId());
        }
    }

    // ==================== 无效规则过滤 ====================

    @Nested
    @DisplayName("reloadFromDb - 无效路由规则过滤")
    class InvalidRuleFiltering {

        @Test
        @DisplayName("引用不存在 Provider 的路由规则被过滤，不进入快照")
        void shouldFilterOutRulesWithNonExistentProvider() throws Exception {
            // 只有一个 Provider
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            // 路由规则引用了不存在的 provider
            ModelRedirectConfigDO validRule = buildRedirectDO("gpt-4o", "openai-main", "gpt-4o", "EXACT");
            ModelRedirectConfigDO invalidRule = buildRedirectDO("claude-3", "non-existent-provider", "claude-3", "EXACT");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(validRule, invalidRule));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            // 只有 gpt-4o 的规则，claude-3 的规则被过滤
            assertEquals(1, snapshot.getCandidatesMapSize(), "无效规则应被过滤");
            assertTrue(snapshot.getCandidates("claude-3").isEmpty(), "不存在的 Provider 的规则不应出现在快照中");
            assertEquals(1, snapshot.getCandidates("gpt-4o").size(), "有效规则应存在");
        }
    }

    // ==================== EXACT/GLOB/REGEX 分组 ====================

    @Nested
    @DisplayName("reloadFromDb - EXACT/GLOB/REGEX 分组")
    class MatchTypeGrouping {

        @Test
        @DisplayName("EXACT 类型路由进入 aliasRouteMap（精确匹配 HashMap）")
        void shouldPutExactMatchIntoAliasRouteMap() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            ModelRedirectConfigDO exactRule = buildRedirectDO("gpt-4o", "openai-main", "gpt-4o", "EXACT");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(exactRule));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            assertEquals(1, snapshot.getCandidatesMapSize(), "应有1个精确别名");
            assertEquals(0, snapshot.getPatternRoutes().size(), "不应有模式路由");
        }

        @Test
        @DisplayName("GLOB 类型路由进入 patternRoutes，编译为正则")
        void shouldPutGlobMatchIntoPatternRoutes() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            ModelRedirectConfigDO globRule = buildRedirectDO("gpt-4*", "openai-main", "gpt-4-base", "GLOB");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(globRule));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            assertEquals(0, snapshot.getCandidatesMapSize(), "GLOB 不应进精确匹配");
            assertEquals(1, snapshot.getPatternRoutes().size(), "应有1条模式路由");
            assertEquals(MatchType.GLOB, snapshot.getPatternRoutes().get(0).matchType());
            assertEquals("gpt-4*", snapshot.getPatternRoutes().get(0).originalPattern());

            // 验证 GLOB 模式可匹配
            assertTrue(snapshot.getPatternRoutes().get(0).compiledPattern()
                    .matcher("gpt-4o").matches(), "gpt-4o 应匹配 gpt-4*");
            assertTrue(snapshot.getPatternRoutes().get(0).compiledPattern()
                    .matcher("gpt-4o-mini").matches(), "gpt-4o-mini 应匹配 gpt-4*");
            assertFalse(snapshot.getPatternRoutes().get(0).compiledPattern()
                    .matcher("claude-3").matches(), "claude-3 不应匹配 gpt-4*");
        }

        @Test
        @DisplayName("REGEX 类型路由进入 patternRoutes，保留原始正则")
        void shouldPutRegexMatchIntoPatternRoutes() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            ModelRedirectConfigDO regexRule = buildRedirectDO("^gpt-\\d+$", "openai-main", "gpt-base", "REGEX");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(regexRule));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            assertEquals(1, snapshot.getPatternRoutes().size(), "应有1条模式路由");
            assertEquals(MatchType.REGEX, snapshot.getPatternRoutes().get(0).matchType());

            // 验证 REGEX 模式可匹配
            assertTrue(snapshot.getPatternRoutes().get(0).compiledPattern()
                    .matcher("gpt-4").matches(), "gpt-4 应匹配 ^gpt-\\d+$");
            assertFalse(snapshot.getPatternRoutes().get(0).compiledPattern()
                    .matcher("gpt-4o").matches(), "gpt-4o 不应匹配 ^gpt-\\d+$");
        }

        @Test
        @DisplayName("matchType 为 null 或空白时默认为 EXACT")
        void shouldDefaultToExactWhenMatchTypeIsBlank() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            // matchType 为 null
            ModelRedirectConfigDO nullMatchType = buildRedirectDO("gpt-4o", "openai-main", "gpt-4o", null);
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(nullMatchType));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            // matchType 为 null 时应作为 EXACT 处理
            assertEquals(1, snapshot.getCandidatesMapSize(), "默认应为精确匹配");
        }

        @Test
        @DisplayName("无效的 REGEX 语法被跳过，不影响其他规则")
        void shouldSkipInvalidRegexPattern() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            // 无效正则（未闭合的括号）
            ModelRedirectConfigDO invalidRegex = buildRedirectDO("[invalid", "openai-main", "model", "REGEX");
            // 有效的精确匹配
            ModelRedirectConfigDO validExact = buildRedirectDO("gpt-4o", "openai-main", "gpt-4o", "EXACT");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(invalidRegex, validExact));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            // 无效正则被跳过，不影响有效规则
            assertEquals(0, snapshot.getPatternRoutes().size(), "无效正则应被跳过");
            assertEquals(1, snapshot.getCandidatesMapSize(), "有效精确匹配应存在");
        }

        @Test
        @DisplayName("同一 aliasName 不同 matchType 独立分组")
        void shouldGroupSameAliasWithDifferentMatchTypeIndependently() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            // 同名但不同匹配类型：EXACT 和 GLOB
            ModelRedirectConfigDO exactRule = buildRedirectDO("gpt-4", "openai-main", "gpt-4-exact", "EXACT");
            ModelRedirectConfigDO globRule = buildRedirectDO("gpt-4", "openai-main", "gpt-4-glob", "GLOB");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(exactRule, globRule));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            // EXACT 进 aliasRouteMap，GLOB 进 patternRoutes
            assertEquals(1, snapshot.getCandidatesMapSize(), "EXACT 应进精确匹配");
            assertEquals(1, snapshot.getPatternRoutes().size(), "GLOB 应进模式路由");
        }
    }

    // ==================== Auto 路由构建 ====================

    @Nested
    @DisplayName("reloadFromDb - Auto 智能路由构建")
    class AutoRouteBuild {

        @Test
        @DisplayName("Auto 路由配置和候选正确聚合到快照")
        void shouldBuildAutoRouteMapCorrectly() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            ProviderConfigDO provider2 = buildProviderDO("claude-main", "anthropic",
                    "https://api.anthropic.com", 5, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider, provider2));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());

            // Auto 路由配置
            AutoRouteConfigDO autoConfig = buildAutoRouteConfigDO(1L, "default", "SMART_SCORE");
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of(autoConfig));

            // Auto 候选
            AutoRouteCandidateDO candidate1 = buildAutoRouteCandidateDO(1L, 1L, "openai-main", "gpt-4o", 10);
            AutoRouteCandidateDO candidate2 = buildAutoRouteCandidateDO(2L, 1L, "claude-main", "claude-sonnet", 5);
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of(candidate1, candidate2));

            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            Map<String, RoutingConfigSnapshot.AutoRouteEntry> autoRouteMap = snapshot.getAutoRouteMap();
            assertEquals(1, autoRouteMap.size(), "应有1个 Auto 路由");
            assertNotNull(autoRouteMap.get("default"), "default 路由应存在");
            assertEquals("SMART_SCORE", autoRouteMap.get("default").selectionStrategy());
            // 候选按优先级降序排列：openai(10) > claude(5)
            assertEquals(2, autoRouteMap.get("default").candidates().size());
            assertEquals("openai-main", autoRouteMap.get("default").candidates().get(0).getProviderCode());
            assertEquals("claude-main", autoRouteMap.get("default").candidates().get(1).getProviderCode());
        }

        @Test
        @DisplayName("Auto 候选引用不存在的 Provider 时被过滤")
        void shouldFilterAutoCandidateWithNonExistentProvider() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());

            AutoRouteConfigDO autoConfig = buildAutoRouteConfigDO(1L, "default", "SMART_SCORE");
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of(autoConfig));

            // 一个有效候选，一个无效候选（引用不存在的 provider）
            AutoRouteCandidateDO validCandidate = buildAutoRouteCandidateDO(1L, 1L, "openai-main", "gpt-4o", 10);
            AutoRouteCandidateDO invalidCandidate = buildAutoRouteCandidateDO(2L, 1L, "non-existent", "model", 5);
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of(validCandidate, invalidCandidate));

            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            RoutingConfigSnapshot.AutoRouteEntry entry = snapshot.getAutoRouteMap().get("default");
            assertNotNull(entry);
            assertEquals(1, entry.candidates().size(), "无效候选应被过滤");
            assertEquals("openai-main", entry.candidates().get(0).getProviderCode());
        }

        @Test
        @DisplayName("Auto 路由无可用候选时跳过该配置")
        void shouldSkipAutoRouteWithNoValidCandidates() throws Exception {
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());

            // 有配置但无候选
            AutoRouteConfigDO autoConfig = buildAutoRouteConfigDO(1L, "empty-route", "SMART_SCORE");
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of(autoConfig));
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());

            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            assertTrue(snapshot.getAutoRouteMap().isEmpty(), "无候选的 Auto 路由应被跳过");
        }
    }

    // ==================== 刷新失败处理 ====================

    @Nested
    @DisplayName("reloadFromDb - 刷新失败处理")
    class RefreshFailure {

        @Test
        @DisplayName("Provider 查询异常时打脏标记并返回 false")
        void shouldSetDirtyFlagWhenProviderQueryFails() {
            when(providerConfigMapper.selectAllEnabled())
                    .thenThrow(new RuntimeException("DB connection failed"));

            boolean result = service.reloadFromDb("startup");

            assertFalse(result, "刷新应失败");
            verify(routingSnapshotHolder).setDirty(true);
            verify(redisRoutingCacheService).markDirty();
            // 不应更新快照
            verify(routingSnapshotHolder, never()).update(any());
        }

        @Test
        @DisplayName("API Key 解密异常时打脏标记并返回 false")
        void shouldSetDirtyFlagWhenDecryptionFails() {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString()))
                    .thenThrow(new IllegalStateException("decryption failed"));

            boolean result = service.reloadFromDb("startup");

            assertFalse(result, "刷新应失败");
            verify(routingSnapshotHolder).setDirty(true);
            verify(redisRoutingCacheService).markDirty();
        }

        @Test
        @DisplayName("Redis 预热异常时打脏标记并返回 false")
        void shouldSetDirtyFlagWhenRedisWarmupFails() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());

            // ObjectMapper 序列化抛出异常
            when(objectMapper.writeValueAsString(any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialize error") {});

            boolean result = service.reloadFromDb("startup");

            assertFalse(result, "刷新应失败");
            verify(routingSnapshotHolder).setDirty(true);
            verify(redisRoutingCacheService).markDirty();
        }
    }

    // ==================== 协议解析 ====================

    @Nested
    @DisplayName("reloadFromDb - 协议解析")
    class ProtocolParsing {

        @Test
        @DisplayName("supportedProtocols 为 null 时解析为空列表（表示支持所有协议）")
        void shouldParseNullProtocolsAsEmptyList() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot.ProviderEntry entry = captor.getValue().getProviderMap().get("openai-main");
            assertNotNull(entry);
            assertTrue(entry.supportedProtocols().isEmpty(), "null 协议应解析为空列表");
        }

        @Test
        @DisplayName("supportedProtocols 逗号分隔正确解析")
        void shouldParseCommaSeparatedProtocols() throws Exception {
            ProviderConfigDO provider = buildProviderDO("multi-provider", "openai",
                    "https://api.test.com", 10, "OPENAI_CHAT,ANTHROPIC");
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot.ProviderEntry entry = captor.getValue().getProviderMap().get("multi-provider");
            assertNotNull(entry);
            assertEquals(2, entry.supportedProtocols().size(), "应解析2个协议");
            assertTrue(entry.supportedProtocols().contains("OPENAI_CHAT"));
            assertTrue(entry.supportedProtocols().contains("ANTHROPIC"));
        }
    }

    // ==================== 候选排序 ====================

    @Nested
    @DisplayName("reloadFromDb - 候选排序")
    class CandidateSorting {

        @Test
        @DisplayName("精确匹配候选按 Provider 优先级降序排列")
        void shouldSortExactCandidatesByPriorityDesc() throws Exception {
            ProviderConfigDO provider1 = buildProviderDO("low-priority", "openai",
                    "https://low.com", 5, null);
            ProviderConfigDO provider2 = buildProviderDO("high-priority", "openai",
                    "https://high.com", 20, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider1, provider2));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            // 两个规则指向同一个 aliasName，不同 provider
            ModelRedirectConfigDO rule1 = buildRedirectDO("gpt-4o", "low-priority", "gpt-4o", "EXACT");
            ModelRedirectConfigDO rule2 = buildRedirectDO("gpt-4o", "high-priority", "gpt-4o", "EXACT");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of(rule1, rule2));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            List<RouteCandidate> candidates = snapshot.getCandidates("gpt-4o");
            assertEquals(2, candidates.size());
            assertEquals(20, candidates.get(0).getProviderPriority(), "高优先级应排第一");
            assertEquals(5, candidates.get(1).getProviderPriority(), "低优先级应排第二");
        }

        @Test
        @DisplayName("模式路由排序：GLOB 排在 REGEX 前面，同类型内按 pattern 长度降序")
        void shouldSortPatternRoutesGlobFirstThenByLengthDesc() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");

            // 短 GLOB
            ModelRedirectConfigDO shortGlob = buildRedirectDO("gpt-*", "openai-main", "short", "GLOB");
            // 长 GLOB
            ModelRedirectConfigDO longGlob = buildRedirectDO("gpt-4o-*", "openai-main", "long", "GLOB");
            // REGEX
            ModelRedirectConfigDO regexRule = buildRedirectDO("^gpt-4.*$", "openai-main", "regex", "REGEX");
            when(modelRedirectConfigMapper.selectAllEnabled())
                    .thenReturn(List.of(shortGlob, longGlob, regexRule));

            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            List<RoutingConfigSnapshot.PatternRoute> patternRoutes = snapshot.getPatternRoutes();
            assertEquals(3, patternRoutes.size());

            // GLOB 应排在 REGEX 前面
            assertEquals(MatchType.GLOB, patternRoutes.get(0).matchType());
            assertEquals(MatchType.GLOB, patternRoutes.get(1).matchType());
            assertEquals(MatchType.REGEX, patternRoutes.get(2).matchType());

            // 同为 GLOB，长 pattern 优先
            assertEquals("gpt-4o-*", patternRoutes.get(0).originalPattern());
            assertEquals("gpt-*", patternRoutes.get(1).originalPattern());
        }
    }

    // ==================== Provider 超时和优先级默认值 ====================

    @Nested
    @DisplayName("reloadFromDb - Provider 默认值处理")
    class ProviderDefaults {

        @Test
        @DisplayName("Provider timeoutSeconds 为 null 时默认 60 秒")
        void shouldDefaultTimeoutTo60() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            provider.setTimeoutSeconds(null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot.ProviderEntry entry = captor.getValue().getProviderMap().get("openai-main");
            assertNotNull(entry);
            assertEquals(60, entry.timeoutSeconds(), "超时默认应为60秒");
        }

        @Test
        @DisplayName("Provider priority 为 null 时默认 0")
        void shouldDefaultPriorityTo0() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            provider.setPriority(null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot.ProviderEntry entry = captor.getValue().getProviderMap().get("openai-main");
            assertNotNull(entry);
            assertEquals(0, entry.priority(), "优先级默认应为0");
        }
    }

    // ==================== 版本号生成 ====================

    @Nested
    @DisplayName("reloadFromDb - 版本号")
    class VersionGeneration {

        @Test
        @DisplayName("多次刷新版本号单调递增")
        void shouldGenerateMonotonicallyIncreasingVersions() throws Exception {
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("first");
            service.reloadFromDb("second");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder, org.mockito.Mockito.times(2)).update(captor.capture());

            List<RoutingConfigSnapshot> snapshots = captor.getAllValues();
            long v1 = snapshots.get(0).getVersion();
            long v2 = snapshots.get(1).getVersion();
            assertTrue(v2 > v1, "第二次刷新版本号应大于第一次");
        }
    }

    // ==================== 空数据场景 ====================

    @Nested
    @DisplayName("reloadFromDb - 空数据场景")
    class EmptyData {

        @Test
        @DisplayName("所有数据源为空时仍能正常构建空快照")
        void shouldBuildEmptySnapshotWhenNoData() throws Exception {
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            boolean result = service.reloadFromDb("startup");

            assertTrue(result, "空数据刷新应成功");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            assertEquals(0, snapshot.getCandidatesMapSize(), "无精确别名路由");
            assertEquals(0, snapshot.getPatternRoutes().size(), "无模式路由");
            assertTrue(snapshot.getAutoRouteMap().isEmpty(), "无 Auto 路由");
            assertTrue(snapshot.getSupportedModels().isEmpty(), "无支持模型");
        }
    }

    // ==================== 透传候选测试 ====================

    @Nested
    @DisplayName("reloadFromDb - 透传候选（getAllProvidersByPriority）")
    class PassthroughCandidates {

        @Test
        @DisplayName("快照中 getAllProvidersByPriority 按优先级降序返回启用的 Provider")
        void shouldReturnProvidersByPriorityDesc() throws Exception {
            ProviderConfigDO lowP = buildProviderDO("low", "openai",
                    "https://low.com", 5, null);
            ProviderConfigDO highP = buildProviderDO("high", "anthropic",
                    "https://high.com", 20, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(lowP, highP));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of());
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of());
            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            List<RoutingConfigSnapshot.ProviderEntry> providers =
                    captor.getValue().getAllProvidersByPriority();
            assertEquals(2, providers.size());
            assertEquals("high", providers.get(0).providerCode(), "高优先级应排第一");
            assertEquals("low", providers.get(1).providerCode(), "低优先级应排第二");
        }
    }

    // ==================== Auto 候选评分字段 ====================

    @Nested
    @DisplayName("reloadFromDb - Auto 候选评分字段")
    class AutoCandidateScoringFields {

        @Test
        @DisplayName("Auto 候选的评分和能力字段正确映射到 RouteCandidate")
        void shouldMapScoringFieldsToRouteCandidate() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());

            AutoRouteConfigDO autoConfig = buildAutoRouteConfigDO(1L, "default", "SMART_SCORE");
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of(autoConfig));

            AutoRouteCandidateDO candidate = buildAutoRouteCandidateDO(1L, 1L, "openai-main", "gpt-4o", 10);
            candidate.setQualityScore(90);
            candidate.setLatencyScore(70);
            candidate.setCostScore(60);
            candidate.setToolScore(80);
            candidate.setVisionScore(50);
            candidate.setReasoningScore(85);
            candidate.setReliabilityScore(75);
            candidate.setScoreBias(5);
            candidate.setWeight(100);
            candidate.setSupportsVision(true);
            candidate.setSupportsTools(true);
            candidate.setSupportsReasoning(true);
            candidate.setMaxInputTokens(128000);
            candidate.setMaxOutputTokens(4096);
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of(candidate));

            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            RouteCandidate rc = snapshot.getAutoRouteMap().get("default").candidates().get(0);
            assertEquals(90, rc.getQualityScore());
            assertEquals(70, rc.getLatencyScore());
            assertEquals(60, rc.getCostScore());
            assertEquals(80, rc.getToolScore());
            assertEquals(50, rc.getVisionScore());
            assertEquals(85, rc.getReasoningScore());
            assertEquals(75, rc.getReliabilityScore());
            assertEquals(5, rc.getScoreBias());
            assertEquals(100, rc.getWeight());
            assertTrue(rc.getSupportsVision());
            assertTrue(rc.getSupportsTools());
            assertTrue(rc.getSupportsReasoning());
            assertEquals(128000, rc.getMaxInputTokens());
            assertEquals(4096, rc.getMaxOutputTokens());
        }

        @Test
        @DisplayName("Auto 候选的 priority 为 null 时默认为 0")
        void shouldDefaultAutoCandidatePriorityTo0() throws Exception {
            ProviderConfigDO provider = buildProviderDO("openai-main", "openai",
                    "https://api.openai.com", 10, null);
            when(providerConfigMapper.selectAllEnabled()).thenReturn(List.of(provider));
            when(apiKeyEncryptor.decrypt(anyString(), anyString())).thenReturn("sk-key");
            when(modelRedirectConfigMapper.selectAllEnabled()).thenReturn(List.of());

            AutoRouteConfigDO autoConfig = buildAutoRouteConfigDO(1L, "default", "SMART_SCORE");
            when(autoRouteConfigMapper.selectAllEnabled()).thenReturn(List.of(autoConfig));

            AutoRouteCandidateDO candidate = buildAutoRouteCandidateDO(1L, 1L, "openai-main", "gpt-4o", null);
            when(autoRouteCandidateMapper.selectAllEnabled()).thenReturn(List.of(candidate));

            when(supportedModelMapper.selectAllEnabled()).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.reloadFromDb("startup");

            ArgumentCaptor<RoutingConfigSnapshot> captor =
                    ArgumentCaptor.forClass(RoutingConfigSnapshot.class);
            verify(routingSnapshotHolder).update(captor.capture());

            RoutingConfigSnapshot snapshot = captor.getValue();
            RouteCandidate rc = snapshot.getAutoRouteMap().get("default").candidates().get(0);
            assertEquals(0, rc.getProviderPriority(), "候选优先级为 null 时应默认0");
        }
    }
}
