package com.code.aigateway.core.model;

import com.code.aigateway.sdk.protocol.StreamEncodeContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
 *   <li>inputTokens — 输入 token 数（Anthropic message_start 事件）</li>
 * </ul>
 */
public class StreamContext {

    private final String responseId;
    private final long created;
    /** 模型名称，failover 后可能被更新为实际使用的模型 */
    private volatile String model;
    private final AtomicBoolean firstContentSent = new AtomicBoolean(false);
    /** 请求开始时的毫秒时间戳（用于精确计算首token延迟） */
    private final long startMs;
    /** 首个 content token 发送的时间戳（毫秒） */
    private volatile long firstTokenTimeMs = 0;
    /** 当前打开的 content block 索引，-1 表示无打开的块 */
    private volatile int openBlockIndex = -1;
    /** 当前打开的 content block 类型（"text"、"thinking"、"tool_use"），null 表示无打开的块 */
    private volatile String openBlockType;
    /** 下一个 content block 的 Anthropic 序号（跨块递增，独立于 Provider 的 outputIndex） */
    private final AtomicInteger nextContentBlockSeq = new AtomicInteger(0);
    /** 输入 token 数，用于 Anthropic message_start 的 usage */
    private volatile int inputTokens;
    /** OpenAI Responses 专属流状态，保持协议私有状态从通用上下文中剥离 */
    private final OpenAiResponsesStreamState responsesState = new OpenAiResponsesStreamState();

    /** SDK 流式编码上下文（CAS 懒初始化，保证并发安全） */
    private final AtomicReference<StreamEncodeContext> sdkContextRef = new AtomicReference<>();
    /** ObjectMapper 用于创建 SDK StreamEncodeContext */
    private final ObjectMapper objectMapper;

    /** 兼容旧调用方的三参数构造器（无 SDK 上下文支持） */
    public StreamContext(String responseId, long created, String model) {
        this.responseId = responseId;
        this.created = created;
        this.model = model;
        this.startMs = System.currentTimeMillis();
        this.objectMapper = null;
    }

    /**
     * 支持 SDK 上下文的构造器
     * <p>
     * 传入 ObjectMapper 以便懒初始化 SDK StreamEncodeContext，
     * SDK 适配器委托时会自动调用 sdkContext() 获取上下文。
     * </p>
     */
    public StreamContext(String responseId, long created, String model, ObjectMapper objectMapper) {
        this.responseId = responseId;
        this.created = created;
        this.model = model;
        this.startMs = System.currentTimeMillis();
        this.objectMapper = objectMapper;
    }

    /**
     * 获取或创建 SDK StreamEncodeContext（懒初始化）
     * <p>
     * 首次调用时创建，后续复用同一实例。
     * 每次调用同步主项目中的 inputTokens 到 SDK 上下文。
     * </p>
     *
     * @return SDK 流式编码上下文
     * @throws IllegalStateException 如果构造时未传入 ObjectMapper
     */
    public StreamEncodeContext sdkContext() {
        StreamEncodeContext ctx = sdkContextRef.get();
        if (ctx == null) {
            if (objectMapper == null) {
                throw new IllegalStateException(
                        "StreamContext 未配置 ObjectMapper，请使用四参数构造器");
            }
            // CAS 保证只有一个线程成功创建实例，其他线程使用胜出者的实例
            StreamEncodeContext newCtx = new StreamEncodeContext(responseId, created, model, objectMapper);
            if (!sdkContextRef.compareAndSet(null, newCtx)) {
                newCtx = sdkContextRef.get();
            }
            ctx = newCtx;
        }
        // 每次调用时同步 inputTokens（setter 可能在 sdkContext 创建之后被调用）
        ctx.setInputTokens(inputTokens);
        return ctx;
    }

    /** 尝试标记首个 content 已发送，返回 true 表示本次成功占位 */
    public boolean tryMarkFirstContentSent() {
        boolean first = firstContentSent.compareAndSet(false, true);
        if (first) {
            // 记录首token时间戳
            firstTokenTimeMs = System.currentTimeMillis();
        }
        return first;
    }

    /**
     * 获取首token响应时间（毫秒），基于请求创建时的毫秒时间戳精确计算
     * @return 首token延迟（ms），若尚未发送首token则返回 -1
     */
    public long getFirstTokenLatencyMs() {
        if (firstTokenTimeMs <= 0) {
            return -1;
        }
        return firstTokenTimeMs - startMs;
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

    public OpenAiResponsesStreamState responses() {
        return responsesState;
    }
}
