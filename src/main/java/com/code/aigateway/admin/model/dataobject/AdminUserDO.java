package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员账号数据对象（单用户模式）
 */
@Data
public class AdminUserDO {

    /** 主键 ID */
    private Long id;

    /** 单用户模式固定占位键 */
    private String singletonKey;

    /** 管理员用户名 */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 是否启用 */
    private Boolean enabled;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
