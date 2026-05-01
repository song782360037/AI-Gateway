package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Auto 智能路由配置数据对象
 */
@Data
public class AutoRouteConfigDO {

    private Long id;

    private String routeKey;

    private String displayName;

    private String description;

    private Boolean enabled;

    private String selectionStrategy;

    private Long versionNo;

    private String creator;

    private LocalDateTime createTime;

    private String updater;

    private LocalDateTime updateTime;

    private Boolean deleted;
}
