package com.code.aigateway.core.model;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式编码上下文
 * <p>
 * 在流式请求处理过程中，维护需要跨事件共享的可变状态：
 * <ul>
 *   <li>responseId — 响应唯一标识，所有 SSE chunk 共用</li>
 *   <li>created — 创建时间戳（Unix 秒）</li>
 *   <li>model — 模型名称</li>
 *   <li>firstContentSent — 是否已发送首个 content chunk（用于在首包携带 role 等元数据）</li>
 * </ul>
 * </p>
 */
public class StreamContext {

    private final String responseId;
    private final long created;
    private final String model;
    private final AtomicBoolean firstContentSent = new AtomicBoolean(false);

    public StreamContext(String responseId, long created, String model) {
        this.responseId = responseId;
        this.created = created;
        this.model = model;
    }

    /** 尝试标记首个 content 已发送，返回 true 表示本次成功占位 */
    public boolean tryMarkFirstContentSent() {
        return firstContentSent.compareAndSet(false, true);
    }

    public String getResponseId() {
        return responseId;
    }

    public long getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }
}
