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
 *   <li>firstContentSent — 是否已发送首个 content chunk</li>
 *   <li>openBlockIndex — 当前打开的 content block 索引（-1 表示无打开的块）</li>
 *   <li>inputTokens — 输入 token 数（用于 Anthropic message_start 事件）</li>
 * </ul>
 * </p>
 */
public class StreamContext {

    private final String responseId;
    private final long created;
    /** 模型名称，failover 后可能被更新为实际使用的模型 */
    private volatile String model;
    private final AtomicBoolean firstContentSent = new AtomicBoolean(false);
    /** 当前打开的 content block 索引，-1 表示无打开的块 */
    private volatile int openBlockIndex = -1;
    /** 当前打开的 content block 类型（"text"、"thinking"、"tool_use"），null 表示无打开的块 */
    private volatile String openBlockType;
    /** 下一个 content block 的 Anthropic 序号（跨块递增，独立于 Provider 的 outputIndex） */
    private volatile int nextContentBlockSeq = 0;
    /** 输入 token 数，用于 Anthropic message_start 的 usage */
    private volatile int inputTokens;

    public StreamContext(String responseId, long created, String model) {
        this.responseId = responseId;
        this.created = created;
        this.model = model;
    }

    /** 尝试标记首个 content 已发送，返回 true 表示本次成功占位 */
    public boolean tryMarkFirstContentSent() {
        return firstContentSent.compareAndSet(false, true);
    }

    /** 标记指定索引的 content block 已打开 */
    public void openContentBlock(int index) {
        this.openBlockIndex = index;
    }

    /**
     * 分配下一个 Anthropic content block 序号并打开块
     * @param type 块类型（"text"、"thinking"、"tool_use"）
     * @return 分配的序号
     */
    public int allocateAndOpenContentBlock(String type) {
        int seq = nextContentBlockSeq++;
        this.openBlockIndex = seq;
        this.openBlockType = type;
        return seq;
    }

    /**
     * 尝试关闭当前打开的 content block
     * @return 关闭的块索引，-1 表示没有打开的块
     */
    public int closeContentBlock() {
        int idx = this.openBlockIndex;
        this.openBlockIndex = -1;
        this.openBlockType = null;
        return idx;
    }

    /** 当前是否有未关闭的 content block */
    public boolean hasOpenContentBlock() {
        return openBlockIndex >= 0;
    }

    /** 获取当前打开的 content block 索引，-1 表示无打开的块 */
    public int getOpenBlockIndex() {
        return openBlockIndex;
    }

    /** 获取当前打开的 content block 类型，null 表示无打开的块 */
    public String getOpenBlockType() {
        return openBlockType;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
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

    /** failover 切换候选后，更新为实际使用的模型名称 */
    public void setModel(String model) {
        this.model = model;
    }
}
