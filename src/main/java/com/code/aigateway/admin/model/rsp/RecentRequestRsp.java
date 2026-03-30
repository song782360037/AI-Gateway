package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 最近请求记录响应
 */
@Data
public class RecentRequestRsp {

    /** 请求时间（格式 HH:mm:ss） */
    private String time;

    /** 请求模型 */
    private String model;

    /** 提供商 / 通道 */
    private String provider;

    /** Token 消耗 */
    private int tokens;

    /** 响应耗时（ms） */
    private int duration;

    /** 请求状态：success / error */
    private String status;
}
