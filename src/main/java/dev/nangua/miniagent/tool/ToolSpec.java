package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Public tool contract shown to the LLM.
 *
 * <p>公布给模型的工具契约（名称 + 描述 + 参数 schema），由 SystemPromptBuilder
 * 拼进 [TOOLS] 段落。
 */
public record ToolSpec(String name, String description, ObjectNode parameters) {
}
