package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.AutoRouteCandidateMapper;
import com.code.aigateway.admin.mapper.AutoRouteConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.AutoRouteCandidateDO;
import com.code.aigateway.admin.model.dataobject.AutoRouteConfigDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.AutoRouteCandidateAddReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigAddReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigQueryReq;
import com.code.aigateway.admin.model.req.AutoRouteEvaluateReq;
import com.code.aigateway.admin.model.rsp.AutoRouteConfigRsp;
import com.code.aigateway.admin.model.rsp.AutoRouteEvaluateRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteCandidate;
import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.router.auto.AutoRouteRequestClassifier;
import com.code.aigateway.core.router.auto.AutoRouteScorer;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AutoRouteConfigServiceImpl 单元测试
 */
class AutoRouteConfigServiceImplTest {

    private AutoRouteConfigMapper configMapper;
    private AutoRouteCandidateMapper candidateMapper;
    private ProviderConfigMapper providerMapper;
    private RuntimeConfigRefreshService refreshService;
    private TransactionTemplate transactionTemplate;
    private RoutingSnapshotHolder routingSnapshotHolder;
    private AutoRouteConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        configMapper = Mockito.mock(AutoRouteConfigMapper.class);
        candidateMapper = Mockito.mock(AutoRouteCandidateMapper.class);
        providerMapper = Mockito.mock(ProviderConfigMapper.class);
        refreshService = Mockito.mock(RuntimeConfigRefreshService.class);

        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
        transactionTemplate = new TransactionTemplate(txManager);
        routingSnapshotHolder = new RoutingSnapshotHolder();

        service = new AutoRouteConfigServiceImpl(
                configMapper,
                candidateMapper,
                providerMapper,
                refreshService,
                transactionTemplate,
                routingSnapshotHolder,
                new AutoRouteRequestClassifier(),
                new AutoRouteScorer());
    }

    // ==================== 新增配置 ====================

    @Test
    void add_success() {
        when(configMapper.existsByRouteKey("default")).thenReturn(0);
        when(configMapper.insert(any())).thenReturn(1);
        when(refreshService.reloadFromDb(anyString())).thenReturn(true);

        AutoRouteConfigAddReq req = new AutoRouteConfigAddReq();
        req.setRouteKey("default");
        req.setDisplayName("默认路由");
        req.setEnabled(true);
        req.setSelectionStrategy("PRIORITY");

        Long id = service.add(req);

        // 验证 insert 被调用，返回自增 ID
        verify(configMapper).insert(any());
    }

    @Test
    void add_duplicateRouteKey_throwsConflict() {
        when(configMapper.existsByRouteKey("default")).thenReturn(1);
        when(configMapper.selectById(anyLong())).thenReturn(null);

        AutoRouteConfigAddReq req = new AutoRouteConfigAddReq();
        req.setRouteKey("default");
        req.setDisplayName("重复");
        req.setEnabled(true);

        assertThrows(BizException.class, () -> service.add(req));
        verify(configMapper, never()).insert(any());
    }

    @Test
    void add_blankRouteKey_throwsInvalid() {
        AutoRouteConfigAddReq req = new AutoRouteConfigAddReq();
        req.setRouteKey("  ");
        req.setDisplayName("空键");
        req.setEnabled(true);

        assertThrows(BizException.class, () -> service.add(req));
    }

    // ==================== 查询列表（批量统计） ====================

    @Test
    void list_usesBatchCount_avoidsN1() {
        AutoRouteConfigDO record = new AutoRouteConfigDO();
        record.setId(1L);
        record.setRouteKey("default");
        record.setDisplayName("测试");
        record.setEnabled(true);
        record.setSelectionStrategy("PRIORITY");
        record.setVersionNo(1L);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        when(configMapper.selectList(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(record));
        when(configMapper.countList(any(), any(), any())).thenReturn(1L);
        when(candidateMapper.countByConfigIds(List.of(1L)))
                .thenReturn(List.of(Map.of("configId", 1L, "cnt", 3L)));

        AutoRouteConfigQueryReq req = new AutoRouteConfigQueryReq();
        req.setPage(1);
        req.setPageSize(20);
        PageResult<AutoRouteConfigRsp> result = service.list(req);

        assertEquals(1, result.getList().size());
        assertEquals(3, result.getList().get(0).getCandidateCount());
        assertEquals(1, result.getTotal());

        // 验证使用批量查询而非逐条查询
        verify(candidateMapper).countByConfigIds(List.of(1L));
        verify(candidateMapper, never()).countByConfigId(anyLong());
    }

    // ==================== 删除配置 ====================

    @Test
    void delete_success() {
        AutoRouteConfigDO existing = new AutoRouteConfigDO();
        existing.setId(1L);
        existing.setRouteKey("default");
        when(configMapper.selectById(1L)).thenReturn(existing);
        when(candidateMapper.softDeleteByConfigId(any())).thenReturn(2);
        when(configMapper.softDeleteById(any())).thenReturn(1);
        when(refreshService.reloadFromDb(anyString())).thenReturn(true);

        service.delete(1L, 1L);

        ArgumentCaptor<AutoRouteConfigDO> configCaptor = ArgumentCaptor.forClass(AutoRouteConfigDO.class);
        ArgumentCaptor<AutoRouteCandidateDO> candidateCaptor = ArgumentCaptor.forClass(AutoRouteCandidateDO.class);
        var inOrder = Mockito.inOrder(configMapper, candidateMapper);
        inOrder.verify(configMapper).softDeleteById(configCaptor.capture());
        inOrder.verify(candidateMapper).softDeleteByConfigId(candidateCaptor.capture());
        assertEquals(1L, configCaptor.getValue().getId());
        assertEquals(1L, configCaptor.getValue().getVersionNo());
        assertEquals(1L, candidateCaptor.getValue().getConfigId());
        verify(refreshService).reloadFromDb("admin-delete-auto-route");
    }

    @Test
    void delete_configConcurrentModified_doesNotDeleteCandidatesOrReload() {
        AutoRouteConfigDO existing = new AutoRouteConfigDO();
        existing.setId(1L);
        existing.setRouteKey("default");
        when(configMapper.selectById(1L)).thenReturn(existing);
        when(configMapper.softDeleteById(any())).thenReturn(0);

        assertThrows(BizException.class, () -> service.delete(1L, 1L));

        verify(candidateMapper, never()).softDeleteByConfigId(any());
        verify(refreshService, never()).reloadFromDb(anyString());
    }

    @Test
    void delete_notFound_throwsException() {
        when(configMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(999L, 1L));
    }

    // ==================== 新增候选 ====================

    @Test
    void addCandidate_success() {
        AutoRouteConfigDO config = new AutoRouteConfigDO();
        config.setId(1L);
        when(configMapper.selectById(1L)).thenReturn(config);

        ProviderConfigDO provider = new ProviderConfigDO();
        provider.setProviderCode("openai");
        provider.setEnabled(true);
        when(providerMapper.selectByProviderCode("openai")).thenReturn(provider);
        when(candidateMapper.existsCandidate(1L, "openai", "gpt-4o")).thenReturn(0);
        when(candidateMapper.insert(any())).thenReturn(1);
        when(refreshService.reloadFromDb(anyString())).thenReturn(true);

        AutoRouteCandidateAddReq req = new AutoRouteCandidateAddReq();
        req.setConfigId(1L);
        req.setProviderCode("openai");
        req.setTargetModel("gpt-4o");
        req.setPriority(10);
        req.setEnabled(true);

        Long id = service.addCandidate(req);

        verify(candidateMapper).insert(any());
    }

    @Test
    void addCandidate_providerNotFound_throwsException() {
        AutoRouteConfigDO config = new AutoRouteConfigDO();
        config.setId(1L);
        when(configMapper.selectById(1L)).thenReturn(config);
        when(providerMapper.selectByProviderCode("nonexist")).thenReturn(null);

        AutoRouteCandidateAddReq req = new AutoRouteCandidateAddReq();
        req.setConfigId(1L);
        req.setProviderCode("nonexist");
        req.setTargetModel("gpt-4o");

        assertThrows(BizException.class, () -> service.addCandidate(req));
    }

    // ==================== reload 降级 ====================

    @Test
    void add_reloadFails_doesNotThrow() {
        when(configMapper.existsByRouteKey("default")).thenReturn(0);
        when(configMapper.insert(any())).thenReturn(1);
        when(refreshService.reloadFromDb(anyString())).thenReturn(false);

        AutoRouteConfigAddReq req = new AutoRouteConfigAddReq();
        req.setRouteKey("default");
        req.setDisplayName("测试降级");
        req.setEnabled(true);

        // reload 失败时不应抛出异常，降级为日志
        assertDoesNotThrow(() -> service.add(req));
    }

    // ==================== evaluate 调试接口 ====================

    @Test
    void evaluate_snapshotNotReady_throwsException() {
        AutoRouteEvaluateReq req = new AutoRouteEvaluateReq();
        req.setRouteKey("default");
        req.setRequest(buildTextRequest());

        assertThrows(BizException.class, () -> service.evaluate(req));
    }

    @Test
    void evaluate_routeKeyNotFound_throwsException() {
        routingSnapshotHolder.update(new RoutingConfigSnapshot(
                Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), 1L, "test"));

        AutoRouteEvaluateReq req = new AutoRouteEvaluateReq();
        req.setRouteKey("missing");
        req.setRequest(buildTextRequest());

        assertThrows(BizException.class, () -> service.evaluate(req));
    }

    @Test
    void evaluate_success_returnsProfileAndCandidateRanks() {
        RouteCandidate strongCandidate = buildCandidate("openai", "gpt-4o", 90, 80, List.of("OPENAI_CHAT"));
        RouteCandidate filteredCandidate = buildCandidate("anthropic", "claude-3", 70, 10, List.of("ANTHROPIC"));
        RoutingConfigSnapshot.AutoRouteEntry entry = new RoutingConfigSnapshot.AutoRouteEntry(
                "default", "PRIORITY", List.of(filteredCandidate, strongCandidate));
        routingSnapshotHolder.update(new RoutingConfigSnapshot(
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Map.of("default", entry),
                1L,
                "test"));

        AutoRouteEvaluateReq req = new AutoRouteEvaluateReq();
        req.setRouteKey("default");
        req.setRequest(buildTextRequest());

        AutoRouteEvaluateRsp rsp = service.evaluate(req);

        assertEquals("default", rsp.getRouteKey());
        assertNotNull(rsp.getProfile());
        assertEquals(2, rsp.getCandidates().size());
        assertTrue(rsp.getCandidates().get(0).isEligible());
        assertEquals("openai", rsp.getCandidates().get(0).getProviderCode());
        assertEquals(1, rsp.getCandidates().get(0).getRank());
        assertFalse(rsp.getCandidates().get(1).isEligible());
        assertEquals("请求协议不匹配", rsp.getCandidates().get(1).getRejectReason());
        assertNotNull(rsp.getCandidates().get(0).getScore());
    }

    private UnifiedRequest buildTextRequest() {
        UnifiedPart part = new UnifiedPart();
        part.setType(UnifiedPart.TYPE_TEXT);
        part.setText("hello");

        UnifiedMessage message = new UnifiedMessage();
        message.setRole("user");
        message.setParts(List.of(part));

        UnifiedRequest request = new UnifiedRequest();
        request.setRequestProtocol("OPENAI_CHAT");
        request.setMessages(List.of(message));
        request.setStream(true);
        return request;
    }

    private RouteCandidate buildCandidate(String providerCode,
                                          String targetModel,
                                          int qualityScore,
                                          int priority,
                                          List<String> supportedProtocols) {
        return RouteCandidate.builder()
                .providerType("OPENAI")
                .providerCode(providerCode)
                .targetModel(targetModel)
                .providerPriority(priority)
                .supportedProtocols(supportedProtocols)
                .supportsJson(true)
                .supportsStream(true)
                .qualityScore(qualityScore)
                .latencyScore(50)
                .costScore(50)
                .reliabilityScore(80)
                .weight(100)
                .build();
    }
}
