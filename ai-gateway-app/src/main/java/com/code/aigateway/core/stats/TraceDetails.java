package com.code.aigateway.core.stats;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 请求链路追踪详情
 * <p>
 * 记录每次请求经过的所有候选提供商尝试信息，包括每个提供商的状态、错误信息、重试次数等。
 * </p>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceDetails {

    /** 最终选择的提供商编码 */
    private String finalProviderCode;

    /** 最终选择的目标模型 */
    private String finalTargetModel;

    /** 总尝试次数 */
    private int totalAttempts;

    /** 总Failover次数 */
    private int totalFailovers;

    /** 总重试次数 */
    private int totalRetries;

    /** 熔断跳过次数 */
    private int circuitOpenSkippedCount;

    /** Key 选择策略：ROUND_ROBIN / RANDOM / FALLBACK */
    private String keySelectionStrategy;

    /** Key 选择原因说明（如：轮询第5次、加权随机权重200/500、降级排序号0） */
    private String keySelectionReason;

    /**
     * 每个候选提供商的尝试记录。
     * <p>
     * 使用 synchronizedList 替代 CopyOnWriteArrayList，因为：
     * 1. 候选数量通常很小（&lt;10），写操作频率低
     * 2. 序列化前需要加锁读取，避免并发修改导致数据不一致
     * 3. CopyOnWriteArrayList 的写操作会复制整个数组，在小数据量下反而开销更大
     * </p>
     */
    private final List<CandidateAttempt> candidateAttempts = Collections.synchronizedList(new ArrayList<>());

    /**
     * 添加候选尝试记录
     */
    public void addCandidateAttempt(CandidateAttempt attempt) {
        candidateAttempts.add(attempt);
    }

    /**
     * 获取候选尝试记录的快照（用于序列化，避免并发修改问题）
     */
    public List<CandidateAttempt> getCandidateAttemptsSnapshot() {
        synchronized (candidateAttempts) {
            return new ArrayList<>(candidateAttempts);
        }
    }

    /**
     * 按候选索引查找已有尝试记录。
     */
    public CandidateAttempt findCandidateAttempt(int index) {
        synchronized (candidateAttempts) {
            return candidateAttempts.stream()
                    .filter(attempt -> attempt.getIndex() == index)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * 候选提供商尝试记录
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CandidateAttempt {

        /** 候选索引（从0开始） */
        private int index;

        /** 提供商编码 */
        private String providerCode;

        /** 目标模型 */
        private String targetModel;

        /** 尝试状态：SUCCESS / FAILED / CIRCUIT_OPEN / SKIPPED / STREAMING */
        private String status;

        /** 错误消息 */
        private String errorMessage;

        /** 上游HTTP状态码 */
        private Integer httpStatus;

        /** 上游错误类型 */
        private String errorType;

        /** 重试次数 */
        private int retryCount;

        /** 尝试开始时间（毫秒时间戳） */
        private Long attemptStartTime;

        /** 尝试耗时（毫秒） */
        private Long durationMs;

        public CandidateAttempt(int index, String providerCode, String targetModel) {
            this.index = index;
            this.providerCode = providerCode;
            this.targetModel = targetModel;
        }
    }
}
