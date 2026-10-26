package dev.nangua.miniagent.context;

import dev.nangua.miniagent.llm.ChatMessage;

import java.util.List;

/**
 * Exactly what was sent to the model for one step.
 *
 * <p>一个 step 实际发给模型的内容快照。includedMessages 不含 system prompt；
 * estimatedTokens 是本地估算值（见 TokenEstimator），与 provider 上报的真实
 * token 数是两回事。
 */
public record ContextWindow(
        List<ChatMessage> messages,
        int estimatedTokens,
        int includedMessages,
        int omittedMessages,
        int summaryChars
) {
    public ContextWindow {
        messages = List.copyOf(messages);
    }
}
