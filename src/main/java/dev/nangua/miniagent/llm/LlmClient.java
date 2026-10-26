package dev.nangua.miniagent.llm;

/**
 * The only model dependency of the agent runtime.
 *
 * <p>Agent 运行时对模型的唯一依赖。实现必须无状态：不保存任何跨调用记忆，结果
 * 完全由传入的 messages 决定——这也是“每个 step 都要重建上下文”的根本原因。
 */
@FunctionalInterface
public interface LlmClient {
    LlmResponse complete(LlmRequest request) throws LlmException;
}
