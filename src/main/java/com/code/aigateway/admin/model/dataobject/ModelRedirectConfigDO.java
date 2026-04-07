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
     * 匹配类型：EXACT-精确匹配, GLOB-通配符匹配, REGEX-正则匹配
     */
    private String matchType;

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
