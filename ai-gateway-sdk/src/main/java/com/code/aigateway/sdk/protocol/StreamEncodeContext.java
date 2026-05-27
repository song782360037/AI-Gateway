package com.code.aigateway.sdk.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 流式编码上下文
 * <p>
 * 在流式事件处理过程中，维护跨事件共享的可变状态。
 * <b>此对象非线程安全，仅限单 subscriber/单线程使用。</b>
 * 多线程并发访问会导致竞态条件（如 firstContentSent 重复标记、contentBlock 序号错乱）。
 * 如需多线程场景，应使用外层 StreamContext（App 模块提供的线程安全包装）。
 * </p>
 */
public class StreamEncodeContext {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    private final String responseId;
    private final long created;
    private String model;

    /** 首个 content chunk 是否已发送 */
    private boolean firstContentSent;

    /** Anthropic content block 管理 */
    private int openBlockIndex = -1;
    private String openBlockType;
    private int nextContentBlockSeq;

    /**
     * 输入 token 数。
     * <p>0 表示尚未收到真实值（ Anthropic 流式场景下 input_tokens 可能延迟到 message_delta 才出现）。
     * 与 {@link #cachedInputTokens} 的 null 语义区分：0 是"已知为 0"，但当前实现中 0 也作为初始默认值，
     * 因此协议编码层应通过 {@code > 0} 或结合业务上下文判断是否已设置真实值。</p>
     */
    private int inputTokens;

    /** 缓存命中 token 数（null 表示未知/未设置） */
    private Integer cachedInputTokens;

    /** 缓存写入 token 数（null 表示未知/未设置，对应 Anthropic cache_creation_input_tokens） */
    private Integer cacheCreationInputTokens;

    /** Anthropic message_start 是否已发送 */
    private boolean messageStartSent;

    /** message_delta 是否已携带真实 usage（output_tokens 非兜底值） */
    private boolean outputTokensSent;

    /** done 事件是否已处理（用于判断 usage_only 是否需要补发 message_delta） */
    private boolean doneProcessed;

    /**
     * 延迟的 stop_reason（用于 OpenAI usage 延迟到达场景）。
     * <p>当 done 事件未携带 usage 时，暂存 stop_reason，待 usage_only 事件到达后
     * 合并到唯一的 message_delta 中发送，避免发出两个 message_delta 违反 Anthropic 协议。</p>
     */
    private String deferredStopReason;

    /** OpenAI Responses 专属流状态 */
    private final ResponsesStreamState responsesState = new ResponsesStreamState();

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    public StreamEncodeContext(String responseId, long created, String model) {
        this(responseId, created, model, DEFAULT_MAPPER);
    }

    public StreamEncodeContext(String responseId, long created, String model, ObjectMapper objectMapper) {
        this.responseId = responseId;
        this.created = created;
        this.model = model;
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_MAPPER;
    }

    /** 尝试标记首个 content 已发送，返回 true 表示首次 */
    public boolean tryMarkFirstContentSent() {
        if (firstContentSent) {
            return false;
        }
        firstContentSent = true;
        return true;
    }

    /** 分配并打开 Anthropic content block */
    public int allocateAndOpenContentBlock(String type) {
        int seq = nextContentBlockSeq++;
        this.openBlockIndex = seq;
        this.openBlockType = type;
        return seq;
    }

    /** 关闭当前打开的 content block，返回索引（-1 表示无打开的块） */
    public int closeContentBlock() {
        int idx = this.openBlockIndex;
        this.openBlockIndex = -1;
        this.openBlockType = null;
        return idx;
    }

    /** 获取当前打开的 block 索引 */
    public int getOpenBlockIndex() {
        return openBlockIndex;
    }

    /** 获取当前打开的 block 类型 */
    public String getOpenBlockType() {
        return openBlockType;
    }

    /** 序列化为 JSON */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize to json", e);
        }
    }

    // ===================== Getter/Setter =====================

    public String getResponseId() { return responseId; }
    public long getCreated() { return created; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public Integer getCachedInputTokens() { return cachedInputTokens; }
    public void setCachedInputTokens(Integer cachedInputTokens) { this.cachedInputTokens = cachedInputTokens; }
    public Integer getCacheCreationInputTokens() { return cacheCreationInputTokens; }
    public void setCacheCreationInputTokens(Integer cacheCreationInputTokens) { this.cacheCreationInputTokens = cacheCreationInputTokens; }
    public boolean isMessageStartSent() { return messageStartSent; }
    public void setMessageStartSent(boolean messageStartSent) { this.messageStartSent = messageStartSent; }
    public boolean isOutputTokensSent() { return outputTokensSent; }
    public void setOutputTokensSent(boolean outputTokensSent) { this.outputTokensSent = outputTokensSent; }
    public boolean isDoneProcessed() { return doneProcessed; }
    public void setDoneProcessed(boolean doneProcessed) { this.doneProcessed = doneProcessed; }
    public String getDeferredStopReason() { return deferredStopReason; }
    public void setDeferredStopReason(String deferredStopReason) { this.deferredStopReason = deferredStopReason; }
    public ResponsesStreamState responses() { return responsesState; }

    // ===================== OpenAI Responses 流状态 =====================

    /**
     * OpenAI Responses 协议专属流状态
     * <p>单线程使用，无需线程安全。</p>
     */
    public static class ResponsesStreamState {
        private int nextOutputItemIndex;
        private int reasoningOutputItemIndex = -1;
        private String reasoningItemId;
        private int textOutputItemIndex = -1;
        private String textItemId;
        private int lastOutputItemIndex = -1;
        private boolean reasoningBlockOpen;
        private boolean textBlockOpen;

        public int nextOutputItemIndex() {
            int idx = nextOutputItemIndex++;
            lastOutputItemIndex = idx;
            return idx;
        }

        public int getLastOutputItemIndex() { return lastOutputItemIndex; }

        public boolean tryOpenReasoningBlock() {
            if (reasoningBlockOpen) return false;
            reasoningBlockOpen = true;
            return true;
        }
        public void setReasoningOutputItemIndex(int idx) { this.reasoningOutputItemIndex = idx; }
        public int getReasoningOutputItemIndex() { return reasoningOutputItemIndex; }
        public void setReasoningItemId(String id) { this.reasoningItemId = id; }
        public String getReasoningItemId() { return reasoningItemId; }

        public boolean tryOpenTextBlock() {
            if (textBlockOpen) return false;
            textBlockOpen = true;
            return true;
        }
        public void setTextOutputItemIndex(int idx) { this.textOutputItemIndex = idx; }
        public int getTextOutputItemIndex() { return textOutputItemIndex; }
        public void setTextItemId(String id) { this.textItemId = id; }
        public String getTextItemId() { return textItemId; }

        public boolean isTextBlockOpen() { return textBlockOpen; }
        public void closeTextBlock() {
            textBlockOpen = false;
            textOutputItemIndex = -1;
            textItemId = null;
        }

        public boolean isReasoningBlockOpen() { return reasoningBlockOpen; }
        public void closeReasoningBlock() {
            reasoningBlockOpen = false;
            reasoningOutputItemIndex = -1;
            reasoningItemId = null;
        }
    }
}
