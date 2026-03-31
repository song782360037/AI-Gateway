package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 配置列表/详情响应（不含完整 key）
 */
@Data
public class ApiKeyConfigRsp {

    /** 主键 ID */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
