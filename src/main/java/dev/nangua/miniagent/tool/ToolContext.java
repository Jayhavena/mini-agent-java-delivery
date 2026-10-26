package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Per-session execution context. The mutable state node belongs to one session and
 * is persisted with that session, which is what keeps two chat windows isolated.
 *
 * <p>按 session 隔离的执行上下文。sessionState 是该 session 的可变状态节点
 * （如 todo 列表），随 session 一起持久化——两个聊天窗口互不可见正是靠这一点。
 */
public record ToolContext(String userId, String sessionId, ObjectNode sessionState) {
    public ToolContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(sessionState, "sessionState");
    }
}
