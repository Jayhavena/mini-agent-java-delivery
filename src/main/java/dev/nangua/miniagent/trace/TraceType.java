package dev.nangua.miniagent.trace;

/**
 * trace 事件类型：覆盖 session 生命周期（创建/加载）、一个 turn（一次 chat 调用）
 * 的开始与结束、turn 内每次模型请求与解析，以及每次工具调用的执行结果。
 * 事件按发生顺序追加写入 JSONL 审计日志（见 JsonlTraceLogger），用于事后
 * 复盘「运行时到底做了什么」，与 session 消息记录的「对话内容」互补。
 */
public enum TraceType {
    SESSION_CREATED,
    SESSION_LOADED,
    TURN_START,
    CONTEXT_BUILT,
    COMPRESSION,
    LLM_REQUEST,
    LLM_RESPONSE,
    PARSE_OK,
    PARSE_ERROR,
    TOOL_CALL,
    TOOL_RESULT,
    TOOL_ERROR,
    TURN_LIMIT,
    TURN_END,
    ERROR
}
