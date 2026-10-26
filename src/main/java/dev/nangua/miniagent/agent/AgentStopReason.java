package dev.nangua.miniagent.agent;

/**
 * turn 的结束原因：FINAL 为模型正常返回最终答案；MAX_TOOL_ROUNDS / MAX_STEPS /
 * PARSE_ERROR 分别是工具次数、推理步数、解析失败次数超限；LLM_ERROR 为模型调用
 * 失败（客户端内部重试后仍失败）。除 FINAL 外都走 fallback 兜底回答。
 */
public enum AgentStopReason {
    FINAL,
    MAX_TOOL_ROUNDS,
    MAX_STEPS,
    PARSE_ERROR,
    LLM_ERROR
}
