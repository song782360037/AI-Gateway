package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局配置数据对象
 */
@Data
public class GlobalConfigDO {

    /** 主键 ID */
    private Long id;

    /** 配置键（唯一标识） */
    private String configKey;

    /** 配置值（JSON 或纯文本） */
    private String configValue;

    /** 配置描述 */
    private String description;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建者 */
    private String creator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updater;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Boolean deleted;
}
