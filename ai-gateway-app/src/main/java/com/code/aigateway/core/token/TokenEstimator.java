package com.code.aigateway.core.token;

import com.code.aigateway.sdk.model.UnifiedMessage;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedTool;
import com.code.aigateway.sdk.model.UnifiedToolCall;
import com.code.aigateway.sdk.model.UnifiedToolChoice;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本地 Token 估算组件
 * <p>
 * 使用 jtokkit（OpenAI tiktoken Java 移植）进行本地 token 估算，
 * 适用于非 Anthropic 上游（OpenAI、Gemini 等）。
 * 注意：估算值仅供参考，与实际 token 数可能存在偏差。
 * </p>
 */
@Component
@Slf4j
public class TokenEstimator {

    private final Encoding o200kBase;
    private final Encoding cl100kBase;
    private final ObjectMapper objectMapper;

    public TokenEstimator(ObjectMapper objectMapper) {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.o200kBase = registry.getEncoding(EncodingType.O200K_BASE);
        this.cl100kBase = registry.getEncoding(EncodingType.CL100K_BASE);
        this.objectMapper = objectMapper;
    }

    /**
     * 每条消息的固定开销 token 数（模拟 <|im_start|>role\n ... <|im_end|> 等边界标记）
     */
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    /**
     * 估算请求的 input token 数
     * <p>
     * 按消息粒度分别编码并累加，每条消息额外加固定开销常数，更接近实际 token 数。
     * </p>
     *
     * @param request     统一请求
     * @param targetModel 路由后的目标模型名（用于选择编码）
     * @return 估算的 token 数
     */
    public int estimate(UnifiedRequest request, String targetModel) {
        Encoding encoding = selectEncoding(targetModel);
        int total = 0;

        // 系统提示词（独立编码，附带消息边界开销）
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            total += encoding.encode(request.getSystemPrompt()).size() + MESSAGE_OVERHEAD_TOKENS;
        }

        // 消息列表：按条编码
        if (request.getMessages() != null) {
            for (UnifiedMessage msg : request.getMessages()) {
                total += MESSAGE_OVERHEAD_TOKENS;
                total += encoding.encode(msg.getRole()).size();

                // 文本 / thinking 内容
                if (msg.getParts() != null) {
                    for (UnifiedPart part : msg.getParts()) {
                        if (part.getText() != null) {
                            total += encoding.encode(part.getText()).size();
                        }
                    }
                }

                // assistant 消息中的工具调用
                if (msg.getToolCalls() != null) {
                    for (UnifiedToolCall tc : msg.getToolCalls()) {
                        total += encoding.encode(tc.getToolName()).size();
                        if (tc.getArgumentsJson() != null) {
                            total += encoding.encode(tc.getArgumentsJson()).size();
                        }
                    }
                }
            }
        }

        // 工具定义：每条独立编码
        if (request.getTools() != null) {
            for (UnifiedTool tool : request.getTools()) {
                total += encoding.encode(tool.getName()).size();
                if (tool.getDescription() != null) {
                    total += encoding.encode(tool.getDescription()).size();
                }
                if (tool.getInputSchema() != null) {
                    String schema = serializeSchema(tool.getInputSchema());
                    if (!schema.isEmpty()) {
                        total += encoding.encode(schema).size();
                    }
                }
            }
        }

        // tool_choice 配置开销（如 {"type": "auto"} 或 {"type": "function", "function": {"name": "xxx"}}）
        if (request.getToolChoice() != null) {
            UnifiedToolChoice tc = request.getToolChoice();
            Map<String, Object> tcMap = new java.util.LinkedHashMap<>();
            tcMap.put("type", tc.getType());
            if (tc.getToolName() != null) {
                tcMap.put("function", Map.of("name", tc.getToolName()));
            }
            total += encoding.encode(serializeSchema(tcMap)).size();
        }

        return total;
    }

    /**
     * o 系列模型匹配模式：o1/o3/o4 后跟分隔符或结尾（避免匹配 o1k-xxx 等无关模型）
     * <p>仅覆盖已知模型，新 o 系列发布时需更新此正则。</p>
     */
    private static final Pattern O_SERIES_PATTERN = Pattern.compile("^o[134](?:[-_.]|$)");

    /**
     * 按模型名选择编码类型
     * <ul>
     *   <li>GPT-4o / GPT-4.5 / o 系列 → o200k_base</li>
     *   <li>其他（GPT-4、Gemini 等）→ cl100k_base</li>
     * </ul>
     */
    private Encoding selectEncoding(String model) {
        if (model == null) {
            return cl100kBase;
        }
        String lower = model.toLowerCase();
        if (lower.contains("gpt-4o") || lower.contains("gpt-4.5")
                || lower.contains("gpt-4.1")
                || O_SERIES_PATTERN.matcher(lower).find()) {
            return o200kBase;
        }
        log.debug("[TokenEstimator] 模型 '{}' 未匹配 o200k_base 规则，使用 cl100k_base 估算", model);
        return cl100kBase;
    }

    private String serializeSchema(Map<String, Object> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            log.warn("[TokenEstimator] 工具 Schema 序列化失败，跳过: {}", e.getMessage());
            return "";
        }
    }
}
