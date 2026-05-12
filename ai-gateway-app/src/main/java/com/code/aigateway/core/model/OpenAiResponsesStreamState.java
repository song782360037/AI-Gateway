package com.code.aigateway.core.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI Responses 协议专属流状态
 * <p>
 * 仅用于维护 Responses SSE 编码过程中跨事件共享的 output item 状态，
 * 避免将协议私有字段直接放入通用 StreamContext。
 * </p>
 */
public class OpenAiResponsesStreamState {

    /** 下一个 output item 的索引 */
    private final AtomicInteger nextOutputItemIndex = new AtomicInteger(0);
    /** 当前打开的 reasoning output item 索引，-1 表示未打开 */
    private volatile int reasoningOutputItemIndex = -1;
    /** 当前打开的 reasoning output item ID */
    private volatile String reasoningItemId;
    /** 当前打开的 text output item 索引，-1 表示未打开 */
    private volatile int textOutputItemIndex = -1;
    /** 当前打开的 text output item ID */
    private volatile String textItemId;
    /** 最近一次分配的 output item 索引，-1 表示尚未分配 */
    private volatile int lastOutputItemIndex = -1;
    /** reasoning 块是否已打开 */
    private final AtomicBoolean reasoningBlockOpen = new AtomicBoolean(false);
    /** text 块是否已打开 */
    private final AtomicBoolean textBlockOpen = new AtomicBoolean(false);

    /** 分配下一个 OpenAI Responses output item 索引 */
    public int nextOutputItemIndex() {
        int outputIndex = nextOutputItemIndex.getAndIncrement();
        lastOutputItemIndex = outputIndex;
        return outputIndex;
    }

    /** 获取最近一次分配的 output item 索引 */
    public int getLastOutputItemIndex() {
        return lastOutputItemIndex;
    }

    /** 尝试打开 reasoning 块，返回 true 表示本次成功占位 */
    public boolean tryOpenReasoningBlock() {
        return reasoningBlockOpen.compareAndSet(false, true);
    }

    /** 记录当前打开的 reasoning output item 索引 */
    public void setReasoningOutputItemIndex(int outputIndex) {
        this.reasoningOutputItemIndex = outputIndex;
    }

    /** 获取当前打开的 reasoning output item 索引 */
    public int getReasoningOutputItemIndex() {
        return reasoningOutputItemIndex;
    }

    /** 记录当前打开的 reasoning output item ID */
    public void setReasoningItemId(String itemId) {
        this.reasoningItemId = itemId;
    }

    /** 获取当前打开的 reasoning output item ID */
    public String getReasoningItemId() {
        return reasoningItemId;
    }

    /** 尝试打开 text 块，返回 true 表示本次成功占位 */
    public boolean tryOpenTextBlock() {
        return textBlockOpen.compareAndSet(false, true);
    }

    /** 记录当前打开的 text output item 索引 */
    public void setTextOutputItemIndex(int outputIndex) {
        this.textOutputItemIndex = outputIndex;
    }

    /** 获取当前打开的 text output item 索引 */
    public int getTextOutputItemIndex() {
        return textOutputItemIndex;
    }

    /** 记录当前打开的 text output item ID */
    public void setTextItemId(String itemId) {
        this.textItemId = itemId;
    }

    /** 获取当前打开的 text output item ID */
    public String getTextItemId() {
        return textItemId;
    }

    /** text 块是否已打开 */
    public boolean isTextBlockOpen() {
        return textBlockOpen.get();
    }

    /** 关闭 text 块 */
    public void closeTextBlock() {
        textBlockOpen.set(false);
        textOutputItemIndex = -1;
        textItemId = null;
    }

    /** reasoning 块是否已打开 */
    public boolean isReasoningBlockOpen() {
        return reasoningBlockOpen.get();
    }

    /** 关闭 reasoning 块 */
    public void closeReasoningBlock() {
        reasoningBlockOpen.set(false);
        reasoningOutputItemIndex = -1;
        reasoningItemId = null;
    }
}
