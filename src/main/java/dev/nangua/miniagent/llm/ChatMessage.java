package dev.nangua.miniagent.llm;

import java.util.Objects;

/**
 * A provider-neutral chat message sent to the LLM.
 *
 * <p>发给模型的供应商无关消息，只有 role/content 两个字段；没有原生 function
 * calling 的 tool_calls 结构，本项目用纯 JSON 文本协议替代。
 */
public record ChatMessage(String role, String content) {
    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
