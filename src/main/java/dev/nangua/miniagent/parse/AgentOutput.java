package dev.nangua.miniagent.parse;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Parsed protocol envelope: either one tool call or one final answer.
 *
 * <p>协议解析结果，只有两种合法形态。sealed 让 AgentRuntime 的 instanceof 分支
 * 穷尽，不会出现第三种中间状态。
 */
public sealed interface AgentOutput permits AgentOutput.ToolCall, AgentOutput.FinalAnswer {
    String thought();

    record ToolCall(String thought, String toolName, ObjectNode arguments) implements AgentOutput {
        public ToolCall {
            thought = thought == null ? "" : thought;
            toolName = toolName == null ? "" : toolName;
        }
    }

    record FinalAnswer(String thought, String answer) implements AgentOutput {
        public FinalAnswer {
            thought = thought == null ? "" : thought;
            answer = answer == null ? "" : answer;
        }
    }
}
