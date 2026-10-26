package dev.nangua.miniagent.agent;

/** Agent 基础设施失败（如 session 持久化不可用）。这类错误不可恢复，直接向上抛出，
 *  不像工具失败那样回灌给模型。 */
public class AgentException extends RuntimeException {
    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
