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

    /** 输入 token 数 */
    private int inputTokens;

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
