package dev.nangua.miniagent.llm;

/**
 * Normalised chat-completion response.
 *
 * @param content           assistant content (the JSON protocol envelope in this project)
 * @param reasoningContent  optional provider reasoning channel (e.g. DeepSeek reasoner)
 * @param finishReason      provider finish reason such as "stop" or "length"
 * @param promptTokens      input tokens reported by the provider, or -1
 * @param completionTokens  output tokens reported by the provider, or -1
 * @param model             provider model name
 *
 * <p>归一化后的 chat-completion 响应：content 是协议 JSON 本体；reasoningContent
 * 仅部分供应商提供（如 DeepSeek reasoner）；token 数来自 provider 上报，
 * 拿不到时为 -1。
 */
public record LlmResponse(
        String content,
        String reasoningContent,
        String finishReason,
        int promptTokens,
        int completionTokens,
        String model
) {
    public LlmResponse {
        content = content == null ? "" : content;
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
        finishReason = finishReason == null ? "unknown" : finishReason;
        model = model == null ? "unknown" : model;
    }

    public int totalTokens() {
        if (promptTokens < 0 || completionTokens < 0) {
            return -1;
        }
        return promptTokens + completionTokens;
    }
}
