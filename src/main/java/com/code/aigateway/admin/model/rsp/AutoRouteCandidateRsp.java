package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Auto 智能路由候选模型响应对象
 */
@Data
public class AutoRouteCandidateRsp {

    private Long id;

    private Long configId;

    private String providerCode;

    private String targetModel;

    private Integer priority;

    private Integer weight;

    private Boolean supportsVision;

    private Boolean supportsTools;

    private Boolean supportsToolChoiceRequired;

    private Boolean supportsReasoning;

    private Boolean supportsJson;

    private Boolean supportsStream;

    private Integer maxInputTokens;

    private Integer maxOutputTokens;

    private Integer qualityScore;

    private Integer latencyScore;

    private Integer costScore;

    private Integer toolScore;

    private Integer visionScore;

    private Integer reasoningScore;

    private Integer reliabilityScore;

    private Integer scoreBias;

    private Boolean enabled;

    private String description;

    private Long versionNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
