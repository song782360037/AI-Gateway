package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型重定向配置数据对象
 */
@Data
public class ModelRedirectConfigDO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 模型别名
     */
    private String aliasName;

    /**
     * 提供商业务编码
     */
    private String providerCode;

    /**
     * 实际目标模型
     */
    private String targetModel;

    /**
     * 是否启用，映射 MySQL bit(1)
     */
    private Boolean enabled;

    /**
     * 优先级，值越大越优先
     */
    private Integer priority;

    /**
     * 路由策略
     */
    private String routeStrategy;

    /**
     * 路由权重
     */
    private Integer weight;

    /**
     * 匹配条件 JSON
     */
    private String matchConditionJson;

    /**
     * 扩展配置 JSON
     */
    private String extConfigJson;

    /**
     * 乐观锁版本号
     */
    private Long versionNo;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updater;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记，映射 MySQL bit(1)
     */
    private Boolean deleted;
}
