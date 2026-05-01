package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 Auto 智能路由候选模型请求对象
 */
@Data
public class AutoRouteCandidateUpdateReq {

    @NotNull(message = "ID 不能为空")
    private Long id;

    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    @NotBlank(message = "目标模型不能为空")
    private String targetModel;

    @Min(value = 0, message = "优先级不能为负数")
    @Max(value = 9999, message = "优先级不能超过 9999")
    private Integer priority = 0;

    @Min(value = 1, message = "权重必须大于 0")
    @Max(value = 10000, message = "权重不能超过 10000")
    private Integer weight = 100;

    private Boolean supportsVision = false;

    private Boolean supportsTools = false;

    private Boolean supportsToolChoiceRequired = false;

    private Boolean supportsReasoning = false;

    private Boolean supportsJson = true;

    private Boolean supportsStream = true;

    @Min(value = 0, message = "最大输入 Token 不能为负数")
    private Integer maxInputTokens;

    @Min(value = 0, message = "最大输出 Token 不能为负数")
    private Integer maxOutputTokens;

    @Min(value = 0, message = "质量评分不能为负数")
    @Max(value = 100, message = "质量评分不能超过 100")
    private Integer qualityScore = 50;

    @Min(value = 0, message = "延迟评分不能为负数")
    @Max(value = 100, message = "延迟评分不能超过 100")
    private Integer latencyScore = 50;

    @Min(value = 0, message = "成本评分不能为负数")
    @Max(value = 100, message = "成本评分不能超过 100")
    private Integer costScore = 50;

    @Min(value = 0, message = "工具评分不能为负数")
    @Max(value = 100, message = "工具评分不能超过 100")
    private Integer toolScore = 50;

    @Min(value = 0, message = "视觉评分不能为负数")
    @Max(value = 100, message = "视觉评分不能超过 100")
    private Integer visionScore = 50;

    @Min(value = 0, message = "推理评分不能为负数")
    @Max(value = 100, message = "推理评分不能超过 100")
    private Integer reasoningScore = 50;

    @Min(value = 0, message = "可靠性评分不能为负数")
    @Max(value = 100, message = "可靠性评分不能超过 100")
    private Integer reliabilityScore = 50;

    @Min(value = -100, message = "评分偏置不能小于 -100")
    @Max(value = 100, message = "评分偏置不能超过 100")
    private Integer scoreBias = 0;

    private Boolean enabled = true;

    @Size(max = 512, message = "候选说明不能超过 512 个字符")
    private String description;
}
