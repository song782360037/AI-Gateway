package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新增 API Key 配置请求对象
 */
@Data
public class ApiKeyConfigAddReq {

    /** 名称/备注 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 状态，默认 ACTIVE */
    private String status = "ACTIVE";

    /** 每日调用上限（NULL 不限） */
    private Integer dailyLimit;

    /** 累计调用上限（NULL 不限） */
    private Long totalLimit;

    /** 过期时间（NULL 永不过期） */
    private LocalDateTime expireTime;
}
