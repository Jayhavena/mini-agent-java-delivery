package dev.nangua.miniagent.context;

import dev.nangua.miniagent.session.MessageKind;
import dev.nangua.miniagent.session.SessionMessage;
import dev.nangua.miniagent.util.Json;

import java.util.List;

/**
 * Deterministic extractive summariser. It is intentionally simple: the assignment
 * asks for basic compression, not a second LLM summarisation pipeline.
 *
 * <p>确定式的抽取式摘要：逐条把消息压成一行要点，不引入第二个 LLM 调用。
 * CONTEXT_NOTE（协议纠错提示）在 turn 结束后就是噪音，摘要时直接跳过。
 */
public final class SessionSummarizer {
    private static final int ITEM_CHARS = 280;

    public String digest(List<SessionMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (SessionMessage message : messages) {
            switch (message.kind()) {
                case USER -> sb.append("- 用户: ").append(trim(message.content(), ITEM_CHARS)).append('\n');
                case ASSISTANT_FINAL -> sb.append("- 助手: ").append(trim(message.content(), ITEM_CHARS)).append('\n');
                case ASSISTANT_TOOL_CALL -> {
                    sb.append("- 决策: 调用 ").append(message.toolName()).append('(')
                            .append(trim(Json.write(message.arguments()), 120)).append(')');
                    if (!message.thought().isBlank()) {
                        sb.append("；思考: ").append(trim(message.thought(), 160));
                    }
                    sb.append('\n');
                }
                case TOOL_RESULT -> sb.append("- 工具 ").append(message.toolName())
                        .append(message.toolOk() ? " 成功: " : " 失败: ")
                        .append(trim(message.content(), ITEM_CHARS)).append('\n');
                case CONTEXT_NOTE -> {
                    // Protocol corrections are noise once the turn is over.
                }
            }
        }
        return sb.toString().trim();
    }

    /** 合并旧摘要与新摘要；超长时保头又保尾——头部常是用户身份/偏好等最早的事实，
     *  尾部是最新事实，中间用省略标记衔接。 */
    public String merge(String existing, String newDigest, int maxChars) {
        String old = existing == null ? "" : existing.trim();
        String fresh = newDigest == null ? "" : newDigest.trim();
        if (old.isBlank()) {
            return cap(fresh, maxChars);
        }
        if (fresh.isBlank()) {
            return cap(old, maxChars);
        }
        String combined = old + "\n" + fresh;
        if (combined.length() <= maxChars) {
            return combined;
        }
        // Keep the earliest facts (often user identity/preferences) and the newest facts.
        int head = Math.min(1_200, maxChars / 3);
        int tail = Math.max(0, maxChars - head - 40);
        return combined.substring(0, head).stripTrailing()
                + "\n...[较早摘要已省略]...\n"
                + combined.substring(combined.length() - tail).stripLeading();
    }

    private static String cap(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static String trim(String value, int maxChars) {
        String oneLine = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= maxChars ? oneLine : oneLine.substring(0, maxChars) + "...";
    }
}
