package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 配置数据对象
 */
@Data
public class ApiKeyConfigDO {

    /** 主键 ID */
    private Long id;

    /** API Key SHA-256 哈希（hex，64 字符），原始 key 永不落库 */
    private String keyHash;

    /** 前缀（如 ak-a1b2c），列表展示用 */
    private String keyPrefix;

    /** 名称/备注 */
    private String name;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 每日调用上限（NULL 不限） */
    private Integer dailyLimit;

    /** 累计调用上限（NULL 不限） */
    private Long totalLimit;

    /** 累计已使用次数 */
    private Long usedCount;

    /** 过期时间（NULL 永不过期） */
    private LocalDateTime expireTime;

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

    /** 逻辑删除标记，映射 MySQL bit(1) */
    private Boolean deleted;
}
