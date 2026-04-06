package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ModelRedirectConfigMapper;
import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ModelRedirectConfigDO;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.router.RouteCandidate;
import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.runtime.RedisRoutingCacheService;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 运行时配置刷新服务
 *
 * <p>负责从数据库加载启用中的提供商配置与模型路由配置，
 * 聚合为运行时快照并原子替换到内存，同时尽力预热到 Redis。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeConfigRefreshService {

    /** 提供商配置数据访问层 */
    private final ProviderConfigMapper providerConfigMapper;

    /** 模型重定向配置数据访问层 */
    private final ModelRedirectConfigMapper modelRedirectConfigMapper;

    /** API Key 加解密组件，用于把密文转换为运行时明文 */
    private final ApiKeyEncryptor apiKeyEncryptor;

    /** 本地快照持有器，负责原子替换当前生效快照 */
    private final RoutingSnapshotHolder routingSnapshotHolder;

    /** Redis 路由缓存服务，负责快照远程预热和兜底 */
    private final RedisRoutingCacheService redisRoutingCacheService;

    /** JSON 序列化组件，用于将快照写入 Redis */
    private final ObjectMapper objectMapper;

    /**
     * 本地版本号生成器。
     *
     * <p>当前实现基于时间戳生成快照版本，
     * 该计数器用于极端情况下避免同毫秒内版本碰撞。</p>
     */
    private final AtomicLong versionSequence = new AtomicLong(0L);

    /**
     * 从数据库重新加载配置并刷新运行时快照。
     *
     * @param source 刷新来源，例如 startup、admin-manual、scheduled
     * @return true 表示刷新成功；false 表示刷新失败且已打脏标记
     */
    public boolean reloadFromDb(String source) {
        try {
            // 1. 查询所有启用中的提供商配置，作为路由候选的基础数据。
            List<ProviderConfigDO> providerConfigs = providerConfigMapper.selectAllEnabled();

            // 2. 解密每个 provider 的 API Key，并构建 providerCode -> ProviderEntry 的只读视图。
            Map<String, RoutingConfigSnapshot.ProviderEntry> providerMap = providerConfigs.stream()
                    .collect(Collectors.toMap(
                            ProviderConfigDO::getProviderCode,
                            this::buildProviderEntry,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            // 3. 查询所有启用中的模型重定向配置。
            List<ModelRedirectConfigDO> redirectConfigs = modelRedirectConfigMapper.selectAllEnabled();

            // 4. 先过滤掉引用了不存在或不可用 provider 的规则，避免生成脏候选项。
            Map<String, List<RouteCandidate>> aliasRouteMap = redirectConfigs.stream()
                    .filter(Objects::nonNull)
                    .filter(config -> {
                        boolean exists = providerMap.containsKey(config.getProviderCode());
                        if (!exists) {
                            log.warn("[运行时配置刷新] 忽略无效路由规则，aliasName: {}，providerCode: {}",
                                    config.getAliasName(), config.getProviderCode());
                        }
                        return exists;
                    })
                    // 5. 构建路由候选项，并按 aliasName 分组。
                    .collect(Collectors.groupingBy(
                            ModelRedirectConfigDO::getAliasName,
                            LinkedHashMap::new,
                            Collectors.mapping(config -> buildRouteCandidate(config, providerMap.get(config.getProviderCode())),
                                    Collectors.toList())
                    ));

            // 6. 对每个 alias 的候选列表按 provider 优先级降序排序。
            aliasRouteMap.replaceAll((aliasName, candidates) -> candidates.stream()
                    .sorted(Comparator
                            // 仅按 provider 优先级排序，数值越大越优先，空值按 0 处理
                            .comparing((RouteCandidate candidate) ->
                                            candidate.getProviderPriority() == null ? 0 : candidate.getProviderPriority(),
                                    Comparator.reverseOrder()))
                    .toList());

            // 7. 生成新的快照版本号，并构建不可变快照对象。
            long version = nextVersion();
            RoutingConfigSnapshot snapshot = new RoutingConfigSnapshot(aliasRouteMap, providerMap, version, source);

            // 8. 原子替换本地快照，保证热路径读取始终一致。
            routingSnapshotHolder.update(snapshot);

            // 9. 将快照序列化后预热到 Redis，便于其他节点快速同步。
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            redisRoutingCacheService.warmupSnapshot(snapshotJson, version);
            redisRoutingCacheService.clearDirty();

            log.info("[运行时配置刷新] 刷新成功，来源: {}，版本: {}，provider数: {}，alias数: {}",
                    source, version, providerMap.size(), aliasRouteMap.size());
            return true;
        } catch (Exception ex) {
            // 10. 任意环节异常都不能影响主流程，需要打脏标记并记录错误日志。
            log.error("[运行时配置刷新] 从数据库刷新运行时快照失败，来源: {}", source, ex);
            routingSnapshotHolder.setDirty(true);
            redisRoutingCacheService.markDirty();
            return false;
        }
    }

    /**
     * 将提供商数据库对象转换为运行时 ProviderEntry。
     */
    private RoutingConfigSnapshot.ProviderEntry buildProviderEntry(ProviderConfigDO providerConfig) {
        // 运行时路由需要使用明文 API Key，因此在构建快照时统一解密。
        String apiKey = apiKeyEncryptor.decrypt(
                providerConfig.getApiKeyIv(),
                providerConfig.getApiKeyCiphertext()
        );

        return new RoutingConfigSnapshot.ProviderEntry(
                providerConfig.getProviderType(),
                providerConfig.getProviderCode(),
                Boolean.TRUE.equals(providerConfig.getEnabled()),
                providerConfig.getBaseUrl(),
                apiKey,
                providerConfig.getTimeoutSeconds() == null ? 60 : providerConfig.getTimeoutSeconds(),
                providerConfig.getPriority() == null ? 0 : providerConfig.getPriority(),
                parseProtocols(providerConfig.getSupportedProtocols())
        );
    }

    /**
     * 将模型重定向规则与提供商运行时配置聚合为一个候选路由项。
     */
    private RouteCandidate buildRouteCandidate(ModelRedirectConfigDO redirectConfig,
                                               RoutingConfigSnapshot.ProviderEntry providerEntry) {
        return RouteCandidate.builder()
                .providerType(providerEntry.providerType())
                .providerCode(providerEntry.providerCode())
                .targetModel(redirectConfig.getTargetModel())
                .providerBaseUrl(providerEntry.baseUrl())
                .providerApiKey(providerEntry.apiKey())
                .providerTimeoutSeconds(providerEntry.timeoutSeconds())
                .providerPriority(providerEntry.priority())
                .supportedProtocols(providerEntry.supportedProtocols())
                .build();
    }

    /**
     * 生成快照版本号。
     *
     * <p>优先使用当前时间毫秒值；
     * 若同毫秒内存在并发调用，则追加单调递增序列保证版本单调递增。</p>
     */
    private long nextVersion() {
        long now = System.currentTimeMillis();
        long sequence = versionSequence.updateAndGet(previous -> previous >= now ? previous + 1 : now);
        return Math.max(now, sequence);
    }

    /**
     * 解析逗号分隔的协议字符串为列表。
     * <p>null 或空白 → 空列表（语义为支持所有协议）</p>
     */
    private List<String> parseProtocols(String commaSeparated) {
        return ResponseProtocol.parseCommaSeparated(commaSeparated);
    }
}
