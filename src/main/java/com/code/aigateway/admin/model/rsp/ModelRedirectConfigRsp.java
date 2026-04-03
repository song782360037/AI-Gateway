package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型重定向配置响应对象
 *
 * <p>用于后台返回模型别名路由配置详情或列表数据。</p>
 */
@Data
public class ModelRedirectConfigRsp {

    /** 主键 ID */
    private Long id;

    /** 模型别名，例如 gpt-4o */
    private String aliasName;

    /** 目标提供商编码 */
    private String providerCode;

    /** 目标模型名称 */
    private String targetModel;

    /** 是否启用 */
    private Boolean enabled;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
