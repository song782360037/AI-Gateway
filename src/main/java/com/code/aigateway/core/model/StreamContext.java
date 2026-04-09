package com.code.aigateway.core.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式编码上下文
 * <p>
 * 在流式请求处理过程中，维护需要跨事件共享的可变状态。
 * 设计用于 Reactor 单 subscriber 管道，不应跨线程共享实例。
 * </p>
 * <p>
 * 线程安全：AtomicBoolean/AtomicInteger 字段通过 CAS 保证状态切换原子性（如块打开/关闭）；
 * volatile 字段依赖单 subscriber 语义保证可见性，不保证多线程并发安全。
 * </p>
 * <ul>
 *   <li>responseId — 响应唯一标识，所有 SSE chunk 共用</li>
 *   <li>created — 创建时间戳（Unix 秒）</li>
 *   <li>model — 模型名称，failover 后可能被更新</li>
 *   <li>firstContentSent — 首个 content chunk 是否已发送（CAS 保护）</li>
 *   <li>openBlockIndex — 当前打开的 content block 索引（-1 表示无）</li>
 *   <li>nextContentBlockSeq — Anthropic content block 序号分配器（原子递增）</li>
 *   <li>openaiOutputItemIndex — Responses output item 序号分配器（原子递增）</li>
 *   <li>textBlockOpen / reasoningBlockOpen — 块打开状态（CAS 保护）</li>
 *   <li>inputTokens — 输入 token 数（Anthropic message_start 事件）</li>
 * </ul>
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
    private final AtomicInteger nextContentBlockSeq = new AtomicInteger(0);
    /** 输入 token 数，用于 Anthropic message_start 的 usage */
    private volatile int inputTokens;
    /** OpenAI Responses: 下一个 output item 的索引 */
    private final AtomicInteger openaiOutputItemIndex = new AtomicInteger(0);
    /** OpenAI Responses: 当前打开的 reasoning output item 索引，-1 表示未打开 */
    private volatile int openaiReasoningOutputItemIndex = -1;
    /** OpenAI Responses: 当前打开的 reasoning output item ID */
    private volatile String openaiReasoningItemId;
    /** OpenAI Responses: 当前打开的 text output item 索引，-1 表示未打开 */
    private volatile int openaiTextOutputItemIndex = -1;
    /** OpenAI Responses: 当前打开的 text output item ID */
    private volatile String openaiTextItemId;
    /** OpenAI Responses: reasoning 块是否已打开 */
    private final AtomicBoolean reasoningBlockOpen = new AtomicBoolean(false);
    /** OpenAI Responses: text 块是否已打开 */
    private final AtomicBoolean textBlockOpen = new AtomicBoolean(false);

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
        int seq = nextContentBlockSeq.getAndIncrement();
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

    /** 分配下一个 OpenAI Responses output item 索引 */
    public int nextOpenAiOutputItemIndex() {
        return openaiOutputItemIndex.getAndIncrement();
    }

    /** 尝试打开 reasoning 块，返回 true 表示本次成功占位 */
    public boolean tryOpenReasoningBlock() {
        return reasoningBlockOpen.compareAndSet(false, true);
    }

    /** 记录当前打开的 reasoning output item 索引 */
    public void setOpenAiReasoningOutputItemIndex(int outputIndex) {
        this.openaiReasoningOutputItemIndex = outputIndex;
    }

    /** 获取当前打开的 reasoning output item 索引 */
    public int getOpenAiReasoningOutputItemIndex() {
        return openaiReasoningOutputItemIndex;
    }

    /** 记录当前打开的 reasoning output item ID */
    public void setOpenAiReasoningItemId(String itemId) {
        this.openaiReasoningItemId = itemId;
    }

    /** 获取当前打开的 reasoning output item ID */
    public String getOpenAiReasoningItemId() {
        return openaiReasoningItemId;
    }

    /** 尝试打开 text 块，返回 true 表示本次成功占位 */
    public boolean tryOpenTextBlock() {
        return textBlockOpen.compareAndSet(false, true);
    }

    /** 记录当前打开的 text output item 索引 */
    public void setOpenAiTextOutputItemIndex(int outputIndex) {
        this.openaiTextOutputItemIndex = outputIndex;
    }

    /** 获取当前打开的 text output item 索引 */
    public int getOpenAiTextOutputItemIndex() {
        return openaiTextOutputItemIndex;
    }

    /** 记录当前打开的 text output item ID */
    public void setOpenAiTextItemId(String itemId) {
        this.openaiTextItemId = itemId;
    }

    /** 获取当前打开的 text output item ID */
    public String getOpenAiTextItemId() {
        return openaiTextItemId;
    }

    /** text 块是否已打开 */
    public boolean isTextBlockOpen() {
        return textBlockOpen.get();
    }

    /** 关闭 text 块 */
    public void closeTextBlock() {
        textBlockOpen.set(false);
        openaiTextOutputItemIndex = -1;
        openaiTextItemId = null;
    }

    /** reasoning 块是否已打开 */
    public boolean isReasoningBlockOpen() {
        return reasoningBlockOpen.get();
    }

    /** 关闭 reasoning 块 */
    public void closeReasoningBlock() {
        reasoningBlockOpen.set(false);
        openaiReasoningOutputItemIndex = -1;
        openaiReasoningItemId = null;
    }
}
