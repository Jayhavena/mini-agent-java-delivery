package dev.nangua.miniagent.llm;

import java.util.List;

/**
 * 一次模型请求的全部输入。jsonMode=true 只表示“请求 JSON 模式”，是否真正下发
 * response_format 还要由 LlmConfig.jsonMode 决定（客户端里两者是与门）。
 */
public record LlmRequest(List<ChatMessage> messages, double temperature, int maxOutputTokens, boolean jsonMode) {
    public LlmRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
