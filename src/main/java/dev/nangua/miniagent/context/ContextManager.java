package dev.nangua.miniagent.context;

import dev.nangua.miniagent.agent.AgentConfig;
import dev.nangua.miniagent.llm.ChatMessage;
import dev.nangua.miniagent.session.MessageKind;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.session.SessionMessage;
import dev.nangua.miniagent.trace.TraceEvent;
import dev.nangua.miniagent.trace.TraceLogger;
import dev.nangua.miniagent.trace.TraceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns context construction and basic compression.
 *
 * Invariants:
 * - the system prompt is always first;
 * - a persisted summary represents all messages before {@code compactedMessages};
 * - the most recent messages remain verbatim so follow-ups work;
 * - tool results are capped in the prompt but remain complete on disk.
 *
 * <p>上下文构建与压缩的唯一负责人。不变量：system prompt 永远第一条；摘要 +
 * compactedMessages 一起代表更早的全部历史；最近的消息保持原文（否则追问会失效）；
 * 工具结果只在发给模型的 prompt 里截断，磁盘上始终是完整的。
 */
public final class ContextManager {
    private static final int MAX_TOOL_RESULT_CHARS = 3_200;
    private static final int MAX_OTHER_MESSAGE_CHARS = 8_000;
    private static final double COMPACT_THRESHOLD = 0.85;

    private final AgentConfig config;
    private final SystemPromptBuilder promptBuilder;
    private final SessionSummarizer summarizer = new SessionSummarizer();
    private final TokenEstimator estimator = new TokenEstimator();

    public ContextManager(AgentConfig config, SystemPromptBuilder promptBuilder) {
        this.config = config;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Compacts old messages when the estimated prompt is near the budget.
     * Called before a turn and after a final answer.
     *
     * <p>估算 token 达到预算的 85%（COMPACT_THRESHOLD）才真正压缩：把较旧的消息
     * 合并进摘要，只保留最近 keepRecentMessages 条原文。返回是否发生了压缩。
     */
    public boolean maybeCompact(Session session, TraceLogger trace) {
        return compact(session, trace, false);
    }

    /** Manual compaction used by the CLI /compact command. */
    public boolean forceCompact(Session session, TraceLogger trace) {
        return compact(session, trace, true);
    }

    private boolean compact(Session session, TraceLogger trace, boolean force) {
        List<ChatMessage> before = buildMessages(session, false);
        int beforeTokens = estimator.estimateMessages(before);
        if (!force && beforeTokens < config.contextTokenBudget() * COMPACT_THRESHOLD) {
            return false;
        }

        int size = session.getMessages().size();
        int start = Math.min(session.getCompactedMessages(), size);
        int keep = Math.min(config.keepRecentMessages(), size - start);
        // If those recent messages alone are too large, compact a few more.
        // 光靠“保留最近 N 条”仍可能超预算（比如单条工具结果很大），这里继续多压几条，
        // 但至少保留 4 条，避免把上下文压穿。
        while (keep > 4 && estimatedRecent(session, size - keep, size) > config.contextTokenBudget() * 0.70) {
            keep--;
        }
        int cutoff = size - keep;
        if (cutoff <= start) {
            return false;
        }

        List<SessionMessage> older = session.getMessages().subList(start, cutoff);
        String digest = summarizer.digest(older);
        String merged = summarizer.merge(session.getSummary(), digest, config.maxSummaryChars());
        session.setSummary(merged);
        session.setCompactedMessages(cutoff);

        List<ChatMessage> after = buildMessages(session, false);
        int afterTokens = estimator.estimateMessages(after);
        trace.log(TraceEvent.of(session, TraceType.COMPRESSION, TraceEvent.data(
                "compactedFrom", start,
                "compactedTo", cutoff,
                "compactedCount", cutoff - start,
                "beforeTokens", beforeTokens,
                "afterTokens", afterTokens,
                "summaryChars", merged.length(),
                "forced", force
        )));
        return true;
    }

    /**
     * 构建一个 step 实际发送的上下文窗口：system prompt + 未被压缩的消息，
     * 超预算时从最旧的消息开始丢弃。每个 step 都会调用。
     */
    public ContextWindow build(Session session) {
        List<ChatMessage> messages = buildMessages(session, true);
        int total = estimator.estimateMessages(messages);
        // 注意：这里统计的是“[context omitted] 占位提示的条数”（0 或 1），
        // 不是被丢弃的消息数——被丢的数量在 buildMessages 内部就没有传出来。
        int omitted = messages.stream().mapToInt(message -> message.content().startsWith("[context omitted")
                ? 1 : 0).sum();
        return new ContextWindow(messages, total,
                messages.size() - 1, omitted, session.getSummary().length());
    }

    private List<ChatMessage> buildMessages(Session session, boolean enforceBudget) {
        List<ChatMessage> result = new ArrayList<>();
        result.add(ChatMessage.system(promptBuilder.build(session)));

        List<SessionMessage> all = session.getMessages();
        int start = Math.min(session.getCompactedMessages(), all.size());
        for (int i = start; i < all.size(); i++) {
            result.add(toChatMessage(all.get(i)));
        }

        if (!enforceBudget) {
            return result;
        }
        int budget = config.contextTokenBudget();
        int dropped = 0;
        // 兜底丢弃：永远保留 index 0 的 system prompt，从最旧的历史消息开始丢；
        // 条件用 size > 2，保证 system 之外至少还剩一条消息可发。
        while (result.size() > 2 && estimator.estimateMessages(result) > budget) {
            result.remove(1);
            dropped++;
        }
        if (dropped > 0) {
            result.add(1, ChatMessage.system("[context omitted] " + dropped
                    + " older message(s) were omitted by the token budget. "
                    + "Rely on SESSION_MEMORY and the remaining recent messages."));
        }
        return result;
    }

    /**
     * 把持久化消息渲染成发给模型的 ChatMessage。两个关键映射：TOOL_RESULT 以 user
     * 角色承载（本项目用纯文本协议，不用原生 function calling 的 tool 角色）；
     * CONTEXT_NOTE 以 system 角色承载。截断只影响 prompt，磁盘原文不受影响。
     */
    private ChatMessage toChatMessage(SessionMessage message) {
        MessageKind kind = message.kind();
        String content = switch (kind) {
            case USER -> cap(message.content(), MAX_OTHER_MESSAGE_CHARS);
            case ASSISTANT_TOOL_CALL -> cap(message.content(), MAX_OTHER_MESSAGE_CHARS);
            case ASSISTANT_FINAL -> cap(message.content(), MAX_OTHER_MESSAGE_CHARS);
            case CONTEXT_NOTE -> cap(message.content(), MAX_OTHER_MESSAGE_CHARS);
            case TOOL_RESULT -> "[TOOL_RESULT name=" + message.toolName()
                    + " ok=" + message.toolOk() + "]\n"
                    + cap(message.content(), MAX_TOOL_RESULT_CHARS)
                    + "\n[/TOOL_RESULT]";
        };
        return switch (kind) {
            case USER, TOOL_RESULT -> ChatMessage.user(content);
            case ASSISTANT_TOOL_CALL, ASSISTANT_FINAL -> ChatMessage.assistant(content);
            case CONTEXT_NOTE -> ChatMessage.system(content);
        };
    }

    private int estimatedRecent(Session session, int from, int to) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = from; i < to; i++) {
            messages.add(toChatMessage(session.getMessages().get(i)));
        }
        return estimator.estimateMessages(messages);
    }

    private static String cap(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "\n[truncated in prompt]";
    }

    /** For tests and diagnostics. */
    public int estimateFullPrompt(Session session) {
        return estimator.estimateMessages(buildMessages(session, false));
    }

    public Map<String, Object> stats(Session session) {
        return Map.of(
                "messages", session.messageCount(),
                "compactedMessages", session.getCompactedMessages(),
                "summaryChars", session.getSummary().length(),
                "estimatedFullTokens", estimateFullPrompt(session)
        );
    }
}
