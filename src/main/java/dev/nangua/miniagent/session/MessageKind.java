package dev.nangua.miniagent.session;

/**
 * 消息类型：标记一条 SessionMessage 在会话（session）中的语义角色。
 * 类型决定两件事：ContextManager 按类型决定哪些消息发回模型，以及渲染成哪个
 * prompt 角色——TOOL_RESULT 渲染为 user 角色，CONTEXT_NOTE 渲染为 system 角色
 * （见 ContextManager）。无论哪种类型，消息都会全量落盘用于审计。
 */
public enum MessageKind {
    USER,
    ASSISTANT_TOOL_CALL,
    // 发回模型时按 user 角色渲染，内容包裹在 [TOOL_RESULT] 标签里。
    TOOL_RESULT,
    ASSISTANT_FINAL,
    // 发回模型时按 system 角色渲染，用于向模型注入附加说明（如纠错提示）。
    CONTEXT_NOTE
}
