package dev.nangua.miniagent.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 一个 turn 的最终返回值：用户可见答案、模型思考摘要、工具调用审计记录、步数、
 * 结束原因与耗时等观测数据。estimatedContextTokens 为 -1 表示走的是 fallback
 * 兜底路径，未构建上下文。
 */
public record AgentReply(
        String answer,
        String thought,
        List<ToolCallRecord> toolCalls,
        int steps,
        AgentStopReason stopReason,
        long elapsedMs,
        int estimatedContextTokens,
        int sessionMessageCount,
        String sessionId
) {
    public AgentReply {
        answer = answer == null ? "" : answer;
        thought = thought == null ? "" : thought;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 一次工具调用的审计记录：参数、成败、结果预览（截断至 300 字符）与耗时。 */
    public record ToolCallRecord(
            String name,
            ObjectNode arguments,
            boolean ok,
            String resultPreview,
            long durationMs
    ) {
    }
}
