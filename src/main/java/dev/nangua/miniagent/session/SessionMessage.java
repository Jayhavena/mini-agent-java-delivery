package dev.nangua.miniagent.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One persisted conversation event. The full trace is retained for audit, while the
 * context manager decides which kinds are sent back to the LLM.
 *
 * <p>一条持久化的会话消息，session 落盘的最小单元。字段按类型分工：arguments
 * 仅 ASSISTANT_TOOL_CALL 使用，toolOk 仅 TOOL_RESULT 有意义；step 记录消息
 * 产生于 turn 内的第几次模型请求（USER/CONTEXT_NOTE 恒为 0）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionMessage(
        String id,
        MessageKind kind,
        String content,
        String thought,
        String toolName,
        JsonNode arguments,
        boolean toolOk,
        int step,
        Instant createdAt
) {
    public SessionMessage {
        // JSON 里缺的字段会以 null 进构造器，这里统一兜底成空串/当前时间，消息始终可直接使用。
        Objects.requireNonNull(kind, "kind");
        content = content == null ? "" : content;
        thought = thought == null ? "" : thought;
        toolName = toolName == null ? "" : toolName;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static SessionMessage user(String text) {
        return new SessionMessage(newId(), MessageKind.USER, text, "", "", null, true, 0, Instant.now());
    }

    public static SessionMessage assistantToolCall(String thought, String toolName,
                                                   ObjectNode arguments, int step) {
        // content 有意冗余存一份 {type, thought, tool, arguments} 的 JSON 封装，
        // 使落盘消息单看 content 字段就是自包含的调用记录。
        ObjectNode envelope = Json.mapper().createObjectNode();
        envelope.put("type", "tool_call");
        envelope.put("thought", thought == null ? "" : thought);
        envelope.put("tool", toolName);
        envelope.set("arguments", arguments);
        return new SessionMessage(newId(), MessageKind.ASSISTANT_TOOL_CALL, Json.write(envelope),
                thought, toolName, arguments, true, step, Instant.now());
    }

    public static SessionMessage toolResult(String toolName, boolean ok, String content, int step) {
        return new SessionMessage(newId(), MessageKind.TOOL_RESULT, content, "", toolName,
                null, ok, step, Instant.now());
    }

    public static SessionMessage assistantFinal(String thought, String answer, int step) {
        return new SessionMessage(newId(), MessageKind.ASSISTANT_FINAL, answer, thought, "",
                null, true, step, Instant.now());
    }

    public static SessionMessage contextNote(String content) {
        return new SessionMessage(newId(), MessageKind.CONTEXT_NOTE, content, "", "",
                null, true, 0, Instant.now());
    }

    private static String newId() {
        return "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
