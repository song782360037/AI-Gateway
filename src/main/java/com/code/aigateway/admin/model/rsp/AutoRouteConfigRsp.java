package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Auto 智能路由配置响应对象
 */
@Data
public class AutoRouteConfigRsp {

    private Long id;

    private String routeKey;

    private String displayName;

    private String description;

    private Boolean enabled;

    private String selectionStrategy;

    private Integer candidateCount;

    private Long versionNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<AutoRouteCandidateRsp> candidates;
}
