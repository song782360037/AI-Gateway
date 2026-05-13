package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.AutoRouteCandidateMapper;
import com.code.aigateway.admin.mapper.AutoRouteConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.AutoRouteCandidateDO;
import com.code.aigateway.admin.model.dataobject.AutoRouteConfigDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.req.AutoRouteCandidateAddReq;
import com.code.aigateway.admin.model.req.AutoRouteCandidateUpdateReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigAddReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigQueryReq;
import com.code.aigateway.admin.model.req.AutoRouteConfigUpdateReq;
import com.code.aigateway.admin.model.req.AutoRouteEvaluateReq;
import com.code.aigateway.admin.model.rsp.AutoRouteCandidateRsp;
import com.code.aigateway.admin.model.rsp.AutoRouteConfigRsp;
import com.code.aigateway.admin.model.rsp.AutoRouteEvaluateRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.PageResult;
import com.code.aigateway.core.router.RouteCandidate;
import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.router.auto.AutoRequestProfile;
import com.code.aigateway.core.router.auto.AutoRouteRequestClassifier;
import com.code.aigateway.core.router.auto.AutoRouteScorer;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Auto 智能路由配置管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRouteConfigServiceImpl implements IAutoRouteConfigService {

    private static final String STRATEGY_SMART_SCORE = "SMART_SCORE";
    private static final String LEGACY_STRATEGY_PRIORITY = "PRIORITY";

    private final AutoRouteConfigMapper autoRouteConfigMapper;
    private final AutoRouteCandidateMapper autoRouteCandidateMapper;
    private final ProviderConfigMapper providerConfigMapper;
    private final RuntimeConfigRefreshService runtimeConfigRefreshService;
    private final TransactionTemplate transactionTemplate;
    private final RoutingSnapshotHolder routingSnapshotHolder;
    private final AutoRouteRequestClassifier autoRouteRequestClassifier;
    private final AutoRouteScorer autoRouteScorer;

    @Override
    public Long add(AutoRouteConfigAddReq req) {
        String routeKey = normalizeRouteKey(req.getRouteKey());
        String selectionStrategy = normalizeSelectionStrategy(req.getSelectionStrategy());
        validateRouteKeyUnique(routeKey, null);

        AutoRouteConfigDO record = buildConfigInsertRecord(req, routeKey, selectionStrategy);
        transactionTemplate.executeWithoutResult(status -> {
            int rows = autoRouteConfigMapper.insert(record);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "新增 Auto 路由配置失败");
            }
            log.info("[Auto路由配置] 新增成功，id: {}，routeKey: {}，strategy: {}",
                    record.getId(), routeKey, selectionStrategy);
        });

        ensureRuntimeConfigReloaded("admin-add-auto-route");
        return record.getId();
    }

    @Override
    public void update(AutoRouteConfigUpdateReq req) {
        AutoRouteConfigDO existing = getExistingConfig(req.getId());
        String routeKey = normalizeRouteKey(req.getRouteKey());
        String selectionStrategy = normalizeSelectionStrategy(req.getSelectionStrategy());
        validateRouteKeyUnique(routeKey, existing.getId());

        AutoRouteConfigDO record = buildConfigUpdateRecord(req, routeKey, selectionStrategy);
        int rows = autoRouteConfigMapper.updateById(record);
        if (rows <= 0) {
            throw new BizException("CONFIG_CONCURRENT_MODIFIED", "数据已被其他请求修改，请刷新后重试，id: " + req.getId());
        }
        log.info("[Auto路由配置] 更新成功，id: {}，routeKey: {}，strategy: {}", req.getId(), routeKey, selectionStrategy);
        ensureRuntimeConfigReloaded("admin-update-auto-route");
    }

    @Override
    public void delete(Long id, Long versionNo) {
        AutoRouteConfigDO existing = getExistingConfig(id);
        AutoRouteConfigDO record = new AutoRouteConfigDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            int rows = autoRouteConfigMapper.softDeleteById(record);
            if (rows <= 0) {
                throw new BizException("CONFIG_CONCURRENT_MODIFIED", "数据已被其他请求修改，请刷新后重试，id: " + id);
            }
            autoRouteCandidateMapper.softDeleteByConfigId(buildCandidateDeleteByConfigRecord(id));
            log.info("[Auto路由配置] 删除成功，id: {}，routeKey: {}", id, existing.getRouteKey());
        });
        ensureRuntimeConfigReloaded("admin-delete-auto-route");
    }

    @Override
    public AutoRouteConfigRsp getById(Long id) {
        AutoRouteConfigDO record = getExistingConfig(id);
        List<AutoRouteCandidateDO> candidates = autoRouteCandidateMapper.selectByConfigId(id);
        return toConfigRsp(record, candidates);
    }

    @Override
    public PageResult<AutoRouteConfigRsp> list(AutoRouteConfigQueryReq req) {
        int offset = (req.getPage() - 1) * req.getPageSize();
        List<AutoRouteConfigDO> records = autoRouteConfigMapper.selectList(
                req.getRouteKey(), req.getDisplayName(), req.getEnabled(), offset, req.getPageSize());
        long total = autoRouteConfigMapper.countList(req.getRouteKey(), req.getDisplayName(), req.getEnabled());

        // 批量统计候选数量，避免 N+1 查询
        Map<Long, Integer> countMap = Map.of();
        if (!records.isEmpty()) {
            List<Long> configIds = records.stream().map(AutoRouteConfigDO::getId).toList();
            countMap = autoRouteCandidateMapper.countByConfigIds(configIds).stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("configId")).longValue(),
                            m -> ((Number) m.get("cnt")).intValue()));
        }

        Map<Long, Integer> finalCountMap = countMap;
        List<AutoRouteConfigRsp> list = records.stream().map(record -> {
            AutoRouteConfigRsp rsp = toConfigRsp(record, null);
            rsp.setCandidateCount(finalCountMap.getOrDefault(record.getId(), 0));
            return rsp;
        }).toList();
        return PageResult.of(list, total, req.getPage(), req.getPageSize());
    }

    @Override
    public AutoRouteEvaluateRsp evaluate(AutoRouteEvaluateReq req) {
        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
        if (snapshot == null) {
            throw new BizException("CONFIG_NOT_READY", "运行时路由配置尚未加载，请先刷新配置");
        }
        RoutingConfigSnapshot.AutoRouteEntry entry = snapshot.getAutoRoute(req.getRouteKey());
        if (entry == null) {
            throw new BizException("CONFIG_NOT_FOUND", "Auto 路由配置不存在: " + req.getRouteKey());
        }

        AutoRequestProfile profile = autoRouteRequestClassifier.classify(req.getRequest());
        List<RouteCandidate> rankedCandidates = autoRouteScorer.rank(
                entry.candidates(), profile, req.getRequest().getRequestProtocol());
        Map<RouteCandidate, Integer> rankMap = buildRankMap(rankedCandidates);

        AutoRouteEvaluateRsp rsp = new AutoRouteEvaluateRsp();
        rsp.setRouteKey(req.getRouteKey());
        rsp.setProfile(profile);
        rsp.setCandidates(entry.candidates().stream()
                .map(candidate -> toCandidateEvaluation(candidate, profile, req.getRequest().getRequestProtocol(), rankMap))
                .sorted(Comparator
                        .comparing((AutoRouteEvaluateRsp.CandidateEvaluation item) -> item.getRank() == null)
                        .thenComparing(item -> item.getRank() == null ? Integer.MAX_VALUE : item.getRank()))
                .toList());
        return rsp;
    }

    @Override
    public void toggle(Long id, Long versionNo) {
        AutoRouteConfigDO existing = getExistingConfig(id);
        AutoRouteConfigDO record = new AutoRouteConfigDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setEnabled(!existing.getEnabled());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        int rows = autoRouteConfigMapper.updateEnabled(record);
        if (rows <= 0) {
            throw new BizException("CONFIG_CONCURRENT_MODIFIED", "数据已被其他请求修改，请刷新后重试，id: " + id);
        }
        log.info("[Auto路由配置] 状态切换成功，id: {}，enabled: {} -> {}", id, existing.getEnabled(), !existing.getEnabled());
        ensureRuntimeConfigReloaded("admin-toggle-auto-route");
    }

    @Override
    public Long addCandidate(AutoRouteCandidateAddReq req) {
        getExistingConfig(req.getConfigId());
        validateProviderExists(req.getProviderCode());
        validateCandidateUnique(req.getConfigId(), req.getProviderCode(), req.getTargetModel(), null);

        AutoRouteCandidateDO record = buildCandidateInsertRecord(req);
        transactionTemplate.executeWithoutResult(status -> {
            int rows = autoRouteCandidateMapper.insert(record);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "新增 Auto 路由候选失败");
            }
            log.info("[Auto路由候选] 新增成功，id: {}，configId: {}，provider: {}，targetModel: {}",
                    record.getId(), req.getConfigId(), req.getProviderCode(), req.getTargetModel());
        });
        ensureRuntimeConfigReloaded("admin-add-auto-route-candidate");
        return record.getId();
    }

    @Override
    public void updateCandidate(AutoRouteCandidateUpdateReq req) {
        AutoRouteCandidateDO existing = getExistingCandidate(req.getId());
        validateProviderExists(req.getProviderCode());
        validateCandidateUnique(existing.getConfigId(), req.getProviderCode(), req.getTargetModel(), existing);

        AutoRouteCandidateDO record = buildCandidateUpdateRecord(req);
        int rows = autoRouteCandidateMapper.updateById(record);
        if (rows <= 0) {
            throw new BizException("CONFIG_CONCURRENT_MODIFIED", "数据已被其他请求修改，请刷新后重试，id: " + req.getId());
        }
        log.info("[Auto路由候选] 更新成功，id: {}，provider: {}，targetModel: {}", req.getId(), req.getProviderCode(), req.getTargetModel());
        ensureRuntimeConfigReloaded("admin-update-auto-route-candidate");
    }

    @Override
    public void deleteCandidate(Long id, Long versionNo) {
        AutoRouteCandidateDO existing = getExistingCandidate(id);
        AutoRouteCandidateDO record = new AutoRouteCandidateDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        int rows = autoRouteCandidateMapper.softDeleteById(record);
        if (rows <= 0) {
            throw new BizException("CONFIG_CONCURRENT_MODIFIED", "数据已被其他请求修改，请刷新后重试，id: " + id);
        }
        log.info("[Auto路由候选] 删除成功，id: {}，configId: {}，provider: {}", id, existing.getConfigId(), existing.getProviderCode());
        ensureRuntimeConfigReloaded("admin-delete-auto-route-candidate");
    }

    @Override
    public void toggleCandidate(Long id, Long versionNo) {
        AutoRouteCandidateDO existing = getExistingCandidate(id);
        AutoRouteCandidateDO record = new AutoRouteCandidateDO();
        record.setId(id);
        record.setVersionNo(versionNo);
        record.setEnabled(!existing.getEnabled());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        int rows = autoRouteCandidateMapper.updateEnabled(record);
        if (rows <= 0) {
            throw new BizException("CONFIG_CONCURRENT_MODIFIED", "数据已被其他请求修改，请刷新后重试，id: " + id);
        }
        log.info("[Auto路由候选] 状态切换成功，id: {}，enabled: {} -> {}", id, existing.getEnabled(), !existing.getEnabled());
        ensureRuntimeConfigReloaded("admin-toggle-auto-route-candidate");
    }

    private AutoRouteConfigDO getExistingConfig(Long id) {
        AutoRouteConfigDO record = autoRouteConfigMapper.selectById(id);
        if (record == null) {
            throw new BizException("CONFIG_NOT_FOUND", "Auto 路由配置不存在，id: " + id);
        }
        return record;
    }

    private AutoRouteCandidateDO getExistingCandidate(Long id) {
        AutoRouteCandidateDO record = autoRouteCandidateMapper.selectById(id);
        if (record == null) {
            throw new BizException("CONFIG_NOT_FOUND", "Auto 路由候选不存在，id: " + id);
        }
        return record;
    }

    private String normalizeRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            throw new BizException("CONFIG_INVALID", "路由键不能为空");
        }
        return routeKey.trim();
    }

    private String normalizeSelectionStrategy(String selectionStrategy) {
        if (!StringUtils.hasText(selectionStrategy)) {
            return STRATEGY_SMART_SCORE;
        }
        String normalized = selectionStrategy.trim().toUpperCase();
        if (LEGACY_STRATEGY_PRIORITY.equals(normalized)) {
            return STRATEGY_SMART_SCORE;
        }
        if (!STRATEGY_SMART_SCORE.equals(normalized)) {
            throw new BizException("CONFIG_INVALID", "暂不支持的 Auto 路由策略: " + selectionStrategy);
        }
        return normalized;
    }

    private void validateRouteKeyUnique(String routeKey, Long selfId) {
        if (autoRouteConfigMapper.existsByRouteKey(routeKey) <= 0) {
            return;
        }
        if (selfId != null) {
            AutoRouteConfigDO self = autoRouteConfigMapper.selectById(selfId);
            if (self != null && routeKey.equals(self.getRouteKey())) {
                return;
            }
        }
        throw new BizException("CONFIG_CONFLICT", "Auto 路由键已存在: " + routeKey);
    }

    private void validateProviderExists(String providerCode) {
        ProviderConfigDO provider = providerConfigMapper.selectByProviderCode(providerCode);
        if (provider == null) {
            throw new BizException("CONFIG_NOT_FOUND", "目标提供商不存在: " + providerCode);
        }
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BizException("CONFIG_INVALID", "目标提供商未启用: " + providerCode);
        }
    }

    private void validateCandidateUnique(Long configId, String providerCode, String targetModel, AutoRouteCandidateDO existing) {
        if (existing != null
                && existing.getProviderCode().equals(providerCode)
                && existing.getTargetModel().equals(targetModel)) {
            return;
        }
        if (autoRouteCandidateMapper.existsCandidate(configId, providerCode, targetModel) > 0) {
            throw new BizException("CONFIG_CONFLICT", "该 Auto 路由候选已存在: provider=" + providerCode + ", model=" + targetModel);
        }
    }

    /**
     * 确保 DB 变更后运行时配置同步刷新。
     * DB 已提交，reload 失败时降级为日志，依赖定时刷新兜底。
     */
    private void ensureRuntimeConfigReloaded(String source) {
        try {
            if (!runtimeConfigRefreshService.reloadFromDb(source)) {
                log.warn("[Auto路由配置] 运行时配置刷新失败，来源: {}，数据已持久化，等待下次定时刷新", source);
            }
        } catch (Exception ex) {
            log.error("[Auto路由配置] 运行时配置刷新异常，来源: {}，数据已持久化，等待下次定时刷新", source, ex);
        }
    }

    private AutoRouteConfigDO buildConfigInsertRecord(AutoRouteConfigAddReq req, String routeKey, String selectionStrategy) {
        AutoRouteConfigDO record = new AutoRouteConfigDO();
        record.setRouteKey(routeKey);
        record.setDisplayName(req.getDisplayName());
        record.setDescription(req.getDescription());
        record.setEnabled(req.getEnabled());
        record.setSelectionStrategy(selectionStrategy);
        record.setVersionNo(0L);
        record.setCreator("");
        record.setUpdater("");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        return record;
    }

    private AutoRouteConfigDO buildConfigUpdateRecord(AutoRouteConfigUpdateReq req, String routeKey, String selectionStrategy) {
        AutoRouteConfigDO record = new AutoRouteConfigDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        record.setRouteKey(routeKey);
        record.setDisplayName(req.getDisplayName());
        record.setDescription(req.getDescription());
        record.setEnabled(req.getEnabled());
        record.setSelectionStrategy(selectionStrategy);
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private AutoRouteCandidateDO buildCandidateInsertRecord(AutoRouteCandidateAddReq req) {
        AutoRouteCandidateDO record = new AutoRouteCandidateDO();
        record.setConfigId(req.getConfigId());
        record.setProviderCode(req.getProviderCode());
        record.setTargetModel(req.getTargetModel());
        record.setPriority(req.getPriority());
        record.setWeight(req.getWeight());
        applyCandidateSmartRouteFields(record, req.getSupportsVision(), req.getSupportsTools(),
                req.getSupportsToolChoiceRequired(), req.getSupportsReasoning(), req.getSupportsJson(),
                req.getSupportsStream(), req.getMaxInputTokens(), req.getMaxOutputTokens(), req.getQualityScore(),
                req.getLatencyScore(), req.getCostScore(), req.getToolScore(), req.getVisionScore(),
                req.getReasoningScore(), req.getReliabilityScore(), req.getScoreBias());
        record.setEnabled(req.getEnabled());
        record.setDescription(req.getDescription());
        record.setVersionNo(0L);
        record.setCreator("");
        record.setUpdater("");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        return record;
    }

    private AutoRouteCandidateDO buildCandidateUpdateRecord(AutoRouteCandidateUpdateReq req) {
        AutoRouteCandidateDO record = new AutoRouteCandidateDO();
        record.setId(req.getId());
        record.setVersionNo(req.getVersionNo());
        record.setProviderCode(req.getProviderCode());
        record.setTargetModel(req.getTargetModel());
        record.setPriority(req.getPriority());
        record.setWeight(req.getWeight());
        applyCandidateSmartRouteFields(record, req.getSupportsVision(), req.getSupportsTools(),
                req.getSupportsToolChoiceRequired(), req.getSupportsReasoning(), req.getSupportsJson(),
                req.getSupportsStream(), req.getMaxInputTokens(), req.getMaxOutputTokens(), req.getQualityScore(),
                req.getLatencyScore(), req.getCostScore(), req.getToolScore(), req.getVisionScore(),
                req.getReasoningScore(), req.getReliabilityScore(), req.getScoreBias());
        record.setEnabled(req.getEnabled());
        record.setDescription(req.getDescription());
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private void applyCandidateSmartRouteFields(AutoRouteCandidateDO record,
                                                Boolean supportsVision,
                                                Boolean supportsTools,
                                                Boolean supportsToolChoiceRequired,
                                                Boolean supportsReasoning,
                                                Boolean supportsJson,
                                                Boolean supportsStream,
                                                Integer maxInputTokens,
                                                Integer maxOutputTokens,
                                                Integer qualityScore,
                                                Integer latencyScore,
                                                Integer costScore,
                                                Integer toolScore,
                                                Integer visionScore,
                                                Integer reasoningScore,
                                                Integer reliabilityScore,
                                                Integer scoreBias) {
        record.setSupportsVision(supportsVision);
        record.setSupportsTools(supportsTools);
        record.setSupportsToolChoiceRequired(supportsToolChoiceRequired);
        record.setSupportsReasoning(supportsReasoning);
        record.setSupportsJson(supportsJson);
        record.setSupportsStream(supportsStream);
        record.setMaxInputTokens(maxInputTokens);
        record.setMaxOutputTokens(maxOutputTokens);
        record.setQualityScore(qualityScore);
        record.setLatencyScore(latencyScore);
        record.setCostScore(costScore);
        record.setToolScore(toolScore);
        record.setVisionScore(visionScore);
        record.setReasoningScore(reasoningScore);
        record.setReliabilityScore(reliabilityScore);
        record.setScoreBias(scoreBias);
    }

    private AutoRouteCandidateDO buildCandidateDeleteByConfigRecord(Long configId) {
        AutoRouteCandidateDO record = new AutoRouteCandidateDO();
        record.setConfigId(configId);
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private Map<RouteCandidate, Integer> buildRankMap(List<RouteCandidate> rankedCandidates) {
        return IntStream.range(0, rankedCandidates.size())
                .boxed()
                .collect(Collectors.toMap(rankedCandidates::get, index -> index + 1));
    }

    private AutoRouteEvaluateRsp.CandidateEvaluation toCandidateEvaluation(RouteCandidate candidate,
                                                                           AutoRequestProfile profile,
                                                                           String requestProtocol,
                                                                           Map<RouteCandidate, Integer> rankMap) {
        String rejectReason = autoRouteScorer.rejectReason(candidate, profile, requestProtocol);
        AutoRouteEvaluateRsp.CandidateEvaluation evaluation = new AutoRouteEvaluateRsp.CandidateEvaluation();
        evaluation.setProviderCode(candidate.getProviderCode());
        evaluation.setTargetModel(candidate.getTargetModel());
        evaluation.setEligible(rejectReason == null);
        evaluation.setRejectReason(rejectReason);
        evaluation.setScore(autoRouteScorer.score(candidate, profile));
        evaluation.setPriority(candidate.getProviderPriority());
        evaluation.setWeight(candidate.getWeight());
        evaluation.setRank(rankMap.get(candidate));
        return evaluation;
    }

    private AutoRouteConfigRsp toConfigRsp(AutoRouteConfigDO record, List<AutoRouteCandidateDO> candidates) {
        AutoRouteConfigRsp rsp = new AutoRouteConfigRsp();
        rsp.setId(record.getId());
        rsp.setRouteKey(record.getRouteKey());
        rsp.setDisplayName(record.getDisplayName());
        rsp.setDescription(record.getDescription());
        rsp.setEnabled(record.getEnabled());
        rsp.setSelectionStrategy(record.getSelectionStrategy());
        rsp.setVersionNo(record.getVersionNo());
        rsp.setCreateTime(record.getCreateTime());
        rsp.setUpdateTime(record.getUpdateTime());
        if (candidates != null) {
            rsp.setCandidates(candidates.stream().map(this::toCandidateRsp).toList());
            rsp.setCandidateCount(candidates.size());
        }
        return rsp;
    }

    private AutoRouteCandidateRsp toCandidateRsp(AutoRouteCandidateDO record) {
        AutoRouteCandidateRsp rsp = new AutoRouteCandidateRsp();
        rsp.setId(record.getId());
        rsp.setConfigId(record.getConfigId());
        rsp.setProviderCode(record.getProviderCode());
        rsp.setTargetModel(record.getTargetModel());
        rsp.setPriority(record.getPriority());
        rsp.setWeight(record.getWeight());
        rsp.setSupportsVision(record.getSupportsVision());
        rsp.setSupportsTools(record.getSupportsTools());
        rsp.setSupportsToolChoiceRequired(record.getSupportsToolChoiceRequired());
        rsp.setSupportsReasoning(record.getSupportsReasoning());
        rsp.setSupportsJson(record.getSupportsJson());
        rsp.setSupportsStream(record.getSupportsStream());
        rsp.setMaxInputTokens(record.getMaxInputTokens());
        rsp.setMaxOutputTokens(record.getMaxOutputTokens());
        rsp.setQualityScore(record.getQualityScore());
        rsp.setLatencyScore(record.getLatencyScore());
        rsp.setCostScore(record.getCostScore());
        rsp.setToolScore(record.getToolScore());
        rsp.setVisionScore(record.getVisionScore());
        rsp.setReasoningScore(record.getReasoningScore());
        rsp.setReliabilityScore(record.getReliabilityScore());
        rsp.setScoreBias(record.getScoreBias());
        rsp.setEnabled(record.getEnabled());
        rsp.setDescription(record.getDescription());
        rsp.setVersionNo(record.getVersionNo());
        rsp.setCreateTime(record.getCreateTime());
        rsp.setUpdateTime(record.getUpdateTime());
        return rsp;
    }
}
