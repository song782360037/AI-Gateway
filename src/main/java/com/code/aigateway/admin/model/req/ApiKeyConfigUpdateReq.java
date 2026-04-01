package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新 API Key 配置请求对象
 */
@Data
public class ApiKeyConfigUpdateReq {

    /** 主键 ID */
    @NotNull(message = "ID 不能为空")
    private Long id;

    /** 乐观锁版本号 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    /** 名称/备注 */
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 每日调用上限（NULL 不限） */
    private Integer dailyLimit;

    /** 每分钟请求上限（NULL 使用全局默认） */
    private Integer rpmLimit;

    /** 每小时请求上限（NULL 使用全局默认） */
    private Integer hourlyLimit;

    /** 累计调用上限（NULL 不限） */
    private Long totalLimit;

    /** 过期时间（NULL 永不过期） */
    private LocalDateTime expireTime;
}
