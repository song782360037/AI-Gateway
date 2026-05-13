package com.code.aigateway.core.model;

import com.code.aigateway.sdk.protocol.StreamEncodeContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式编码上下文（App 层）
 * <p>
 * 在流式请求处理过程中，维护 App 层特有状态（首 token 延迟统计等），
 * 并通过 {@link #sdkContext()} 懒初始化提供 SDK 层的 {@link StreamEncodeContext}。
 * </p>
 * <p>
 * 协议编码状态（content block 管理、firstContentSent 等）已全部下沉到 SDK 的
 * {@link StreamEncodeContext}，本类不再重复维护。
 * </p>
 * <p>
 * 设计用于 Reactor 单 subscriber 管道，不应跨线程共享实例。
 * </p>
 */
public class StreamContext {

    private final String responseId;
    private final long created;
    /** 模型名称，failover 后可能被更新为实际使用的模型 */
    private volatile String model;
    /** 请求开始时的毫秒时间戳（用于精确计算首token延迟） */
    private final long startMs;
    /** 首个 content token 发送的时间戳（毫秒） */
    private volatile long firstTokenTimeMs = 0;
    /** 标记首 token 是否已发送（App 层用于延迟统计，SDK 层有独立的 firstContentSent） */
    private final AtomicBoolean firstTokenSent = new AtomicBoolean(false);

    /** 输入 token 数本地缓存，SDK 上下文创建前可用于兜底 */
    private volatile int inputTokens;

    /** 缓存命中 token 数本地缓存（null 表示未知/未设置） */
    private volatile Integer cachedInputTokens;

    /** 缓存写入 token 数本地缓存（null 表示未知/未设置，对应 Anthropic cache_creation_input_tokens） */
    private volatile Integer cacheCreationInputTokens;

    /** SDK 流式编码上下文（CAS 懒初始化，保证并发安全） */
    private final AtomicReference<StreamEncodeContext> sdkContextRef = new AtomicReference<>();
    /** ObjectMapper 用于创建 SDK StreamEncodeContext */
    private final ObjectMapper objectMapper;

    /**
     * 兼容旧调用方的三参数构造器（无 SDK 上下文支持）
     * @deprecated 请使用四参数构造器 {@link #StreamContext(String, long, String, ObjectMapper)}，
     * 三参数构造器无法创建 SDK 上下文，调用 {@link #sdkContext()} 将抛出 IllegalStateException。
     */
    @Deprecated
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
     * 每次调用同步 App 层的可变字段（model、inputTokens）到 SDK 上下文。
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
        // 仅当 App 层可变字段与 SDK 上下文不一致时才同步，避免高频流式场景下的冗余赋值
        if (!Objects.equals(this.model, ctx.getModel())) {
            ctx.setModel(this.model);
        }
        if (this.inputTokens != ctx.getInputTokens()) {
            ctx.setInputTokens(inputTokens);
        }
        if (!Objects.equals(this.cachedInputTokens, ctx.getCachedInputTokens())) {
            ctx.setCachedInputTokens(cachedInputTokens);
        }
        if (!Objects.equals(this.cacheCreationInputTokens, ctx.getCacheCreationInputTokens())) {
            ctx.setCacheCreationInputTokens(cacheCreationInputTokens);
        }
        return ctx;
    }

    /**
     * 尝试标记首 token 已发送，返回 true 表示本次成功占位
     * <p>用于 App 层的首 token 延迟统计，与 SDK 层的 firstContentSent 独立。</p>
     */
    public boolean tryMarkFirstTokenSent() {
        boolean first = firstTokenSent.compareAndSet(false, true);
        if (first) {
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

    public String getResponseId() {
        return responseId;
    }

    public long getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    /** failover 切换候选后，更新为实际使用的模型名称，并同步到 SDK 上下文 */
    public void setModel(String model) {
        this.model = model;
        // 同步更新已创建的 SDK 上下文中的 model
        StreamEncodeContext ctx = sdkContextRef.get();
        if (ctx != null) {
            ctx.setModel(model);
        }
    }

    /** 获取输入 token 数（优先从 SDK 上下文读取，兜底读取本地缓存） */
    public int getInputTokens() {
        StreamEncodeContext ctx = sdkContextRef.get();
        return ctx != null ? ctx.getInputTokens() : inputTokens;
    }

    /** 设置输入 token 数，同时同步到 SDK 上下文 */
    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
        StreamEncodeContext ctx = sdkContextRef.get();
        if (ctx != null) {
            ctx.setInputTokens(inputTokens);
        }
    }

    /** 获取缓存命中 token 数（优先从 SDK 上下文读取，兜底读取本地缓存） */
    public Integer getCachedInputTokens() {
        StreamEncodeContext ctx = sdkContextRef.get();
        return ctx != null ? ctx.getCachedInputTokens() : cachedInputTokens;
    }

    /** 设置缓存命中 token 数，同时同步到 SDK 上下文 */
    public void setCachedInputTokens(Integer cachedInputTokens) {
        this.cachedInputTokens = cachedInputTokens;
        StreamEncodeContext ctx = sdkContextRef.get();
        if (ctx != null) {
            ctx.setCachedInputTokens(cachedInputTokens);
        }
    }

    /** 获取缓存写入 token 数（优先从 SDK 上下文读取，兜底读取本地缓存） */
    public Integer getCacheCreationInputTokens() {
        StreamEncodeContext ctx = sdkContextRef.get();
        return ctx != null ? ctx.getCacheCreationInputTokens() : cacheCreationInputTokens;
    }

    /** 设置缓存写入 token 数，同时同步到 SDK 上下文 */
    public void setCacheCreationInputTokens(Integer cacheCreationInputTokens) {
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        StreamEncodeContext ctx = sdkContextRef.get();
        if (ctx != null) {
            ctx.setCacheCreationInputTokens(cacheCreationInputTokens);
        }
    }
}
