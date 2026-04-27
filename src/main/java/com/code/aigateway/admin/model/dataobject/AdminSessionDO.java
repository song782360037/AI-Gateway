package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员会话数据对象
 */
@Data
public class AdminSessionDO {

    /** 主键 ID */
    private Long id;

    /** 管理员 ID */
    private Long userId;

    /** 会话令牌 SHA-256 哈希 */
    private String sessionTokenHash;

    /** 会话过期时间 */
    private LocalDateTime expireTime;

    /** 最后访问时间 */
    private LocalDateTime lastAccessTime;

    /** 是否已吊销 */
    private Boolean revoked;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
